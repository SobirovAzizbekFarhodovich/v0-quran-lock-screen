package com.quranreminder.app.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.quranreminder.app.database.QuranDatabase
import com.quranreminder.app.database.QuranVerse
import com.quranreminder.app.lockscreen.LockScreenBackgroundManager
import com.quranreminder.app.lockscreen.LockScreenFontFamily
import com.quranreminder.app.lockscreen.LockScreenVerseSelector
import com.quranreminder.app.lockscreen.LockScreenWallpaperApplier
import com.quranreminder.app.lockscreen.LockScreenWallpaperRenderer
import com.quranreminder.app.ui.theme.NurIconButton
import com.quranreminder.app.ui.theme.NurPrimaryButton
import com.quranreminder.app.ui.theme.QuranReminderTheme
import com.quranreminder.app.ui.theme.nurColors
import com.quranreminder.app.ui.theme.NurScreenHeader
import com.quranreminder.app.ui.theme.t
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue

sealed class WallpaperOption {
    data class Local(val sample: LockScreenBackgroundManager.Sample) : WallpaperOption()
    data class Remote(val sample: LockScreenBackgroundManager.RemoteSample) : WallpaperOption()
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LockScreenPreviewScreen(navController: NavHostController) {
    // The preview is an immersive, always-dark stage for the wallpaper, in both app themes.
    QuranReminderTheme(darkTheme = true) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
    
        var verse by remember { mutableStateOf<QuranVerse?>(null) }
        var selectedFont by remember { mutableStateOf(LockScreenWallpaperApplier.getLockScreenFont(context)) }
        var refreshKey by remember { mutableStateOf(0) }
    
        var remoteDownloads by remember { mutableStateOf(mapOf<String, Boolean>()) }
        var activeDownloads by remember { mutableStateOf(mapOf<String, Boolean>()) }
    
        val localSamples = LockScreenBackgroundManager.samples
        val remoteSamples = LockScreenBackgroundManager.remoteSamples
    
        val allOptions = remember(localSamples, remoteSamples) {
            val list = mutableListOf<WallpaperOption>()
            list.addAll(localSamples.map { WallpaperOption.Local(it) })
            list.addAll(remoteSamples.map { WallpaperOption.Remote(it) })
            list
        }

        val pagerState = rememberPagerState(pageCount = { allOptions.size })

        val gallerySetMessage = t("lockPreview.gallerySet")
        val noImageChosenMessage = t("lockPreview.noImageChosen")
        val imageLoadedMessage = t("lockPreview.imageLoaded")
        var applyToHome by remember { mutableStateOf(LockScreenWallpaperApplier.isApplyToHomeEnabled(context)) }
        val showHomeHint = !applyToHome && remember { LockScreenWallpaperApplier.lockScreenShowsHomeInDarkMode(context) }
        val appliedMessage = t("lockPreview.applied")
        val applyFailedMessage = t("lockPreview.applyFailed")
        val shareFailedMessage = t("lockPreview.shareFailed")
        val downloadFailedMessage = t("lockPreview.downloadFailed")
        var isWorking by remember { mutableStateOf(false) }

        val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                val saved = withContext(Dispatchers.IO) {
                    LockScreenBackgroundManager.setCustomFromUri(context, uri)
                }
                if (saved) {
                    refreshKey++
                    Toast.makeText(context, gallerySetMessage, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, noImageChosenMessage, Toast.LENGTH_SHORT).show()
                }
            }
        }

        LaunchedEffect(pagerState.settledPage) {
            val newIndex = pagerState.settledPage
            if (newIndex < allOptions.size && newIndex >= 0) {
                val option = allOptions[newIndex]
                if (option is WallpaperOption.Local) {
                    LockScreenBackgroundManager.setSample(context, option.sample)
                    refreshKey++
                } else if (option is WallpaperOption.Remote) {
                    if (remoteDownloads[option.sample.id] == true) {
                        LockScreenBackgroundManager.setRemoteSample(context, option.sample)
                        refreshKey++
                    } else {
                        activeDownloads = activeDownloads + (option.sample.id to true)
                        val success = LockScreenBackgroundManager.downloadRemoteSample(context, option.sample)
                        activeDownloads = activeDownloads - option.sample.id
                        if (success) {
                            remoteDownloads = remoteDownloads + (option.sample.id to true)
                            LockScreenBackgroundManager.setRemoteSample(context, option.sample)
                            refreshKey++
                            Toast.makeText(context, imageLoadedMessage, Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, downloadFailedMessage, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        LaunchedEffect(refreshKey) {
            withContext(Dispatchers.IO) {
                val database = QuranDatabase.getDatabase(context)
                val selectedVerse = LockScreenVerseSelector.select(context, database)
            
                val downloadMap = LockScreenBackgroundManager.remoteSamples.associate {
                    it.id to LockScreenBackgroundManager.isRemoteSampleDownloaded(context, it)
                }

                withContext(Dispatchers.Main) {
                    verse = selectedVerse
                    remoteDownloads = downloadMap
                }
            }
        }

        val c = nurColors
        val configuration = LocalConfiguration.current
        val screenRatio = remember(configuration) {
            configuration.screenWidthDp.toFloat() / configuration.screenHeightDp.coerceAtLeast(1)
        }
        val currentOption = allOptions.getOrNull(pagerState.currentPage)
        val canApply = !isWorking && verse != null &&
            currentOption.let { it !is WallpaperOption.Remote || remoteDownloads[it.sample.id] == true }
        val canShare = !isWorking && verse != null

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            NurScreenHeader(
                title = t("lockPreview.title"),
                eyebrow = t("lockPreview.eyebrow"),
                leading = {
                    NurIconButton(Icons.Default.ArrowBack, t("common.back"), onClick = { navController.popBackStack() })
                },
                trailing = {
                    NurIconButton(Icons.Default.Refresh, t("lockPreview.newAyah"), onClick = { refreshKey++ })
                },
                bottomPadding = 14.dp
            )

            // Font chips
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(LockScreenFontFamily.entries) { font ->
                    val isFontSelected = font == selectedFont
                    val fontLabel = when (font) {
                        LockScreenFontFamily.STANDART, LockScreenFontFamily.KLASSIK -> t("lockPreview.font.${font.id}")
                        else -> font.label // decorative samples are the label themselves
                    }
                    FilterChip(
                        selected = isFontSelected,
                        onClick = {
                            selectedFont = font
                            LockScreenWallpaperApplier.setLockScreenFont(context, font)
                        },
                        label = {
                            Text(
                                text = fontLabel,
                                style = MaterialTheme.typography.labelLarge,
                                fontFamily = font.composeFontFamily
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            labelColor = MaterialTheme.colorScheme.onSurface,
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = c.frame,
                            selectedBorderColor = c.ornament
                        ),
                        shape = CircleShape
                    )
                }
            }

            // Carousel of phone-shaped previews
            HorizontalPager(
                state = pagerState,
                beyondBoundsPageCount = 2,
                contentPadding = PaddingValues(horizontal = 48.dp), // Show edge of next/prev cards
                pageSpacing = 16.dp,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) { page ->
                // Calculate scale and alpha for carousel effect based on distance from center
                val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                val absOffset = pageOffset.absoluteValue.coerceIn(0f, 1f)

                val scale = 1f - (0.15f * absOffset)
                val alpha = 1f - (0.5f * absOffset)
                val cardShape = RoundedCornerShape(32.dp)

                val option = allOptions[page]
                val isSelected = page == pagerState.settledPage
                val isDownloaded = if (option is WallpaperOption.Remote) remoteDownloads[option.sample.id] == true else true
                val isDownloading = if (option is WallpaperOption.Remote) activeDownloads[option.sample.id] == true else false

                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(screenRatio, matchHeightConstraintsFirst = true)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                this.alpha = alpha
                            }
                            .clip(cardShape)
                            .border(
                                BorderStroke(1.dp, if (isSelected) c.ornament else c.frame),
                                cardShape
                            )
                    ) {
                        WallpaperFullScreenItem(
                            option = option,
                            isSelected = isSelected && absOffset == 0f, // Only show text when fully centered
                            verse = verse,
                            selectedFont = selectedFont,
                            isDownloaded = isDownloaded,
                            isDownloading = isDownloading
                        )
                    }
                }
            }

            // Page indicators
            Row(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                allOptions.forEachIndexed { index, _ ->
                    val isActive = index == pagerState.currentPage
                    val width by animateDpAsState(targetValue = if (isActive) 18.dp else 6.dp, animationSpec = tween(250), label = "pip")
                    Box(
                        modifier = Modifier
                            .width(width)
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(if (isActive) c.ornament else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f))
                    )
                }
            }

            // Bottom action bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .drawBehind {
                        drawLine(c.frame, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
                    }
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .border(BorderStroke(1.dp, c.frame), MaterialTheme.shapes.medium)
                        .toggleable(
                            value = applyToHome,
                            role = Role.Switch,
                            onValueChange = {
                                applyToHome = it
                                LockScreenWallpaperApplier.setApplyToHome(context, it)
                            }
                        )
                        .padding(start = 16.dp, end = 12.dp, top = 6.dp, bottom = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = t("lockPreview.applyToHome"),
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = applyToHome,
                            onCheckedChange = null
                        )
                    }
                    if (showHomeHint) {
                        Text(
                            text = t("lockPreview.applyToHome.hint"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 8.dp, end = 4.dp)
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NurPrimaryButton(
                        text = t("lockPreview.apply"),
                        icon = Icons.Default.Wallpaper,
                        modifier = Modifier.weight(1f),
                        enabled = canApply,
                        loading = isWorking,
                        onClick = {
                            val selected = verse ?: return@NurPrimaryButton
                            isWorking = true
                            scope.launch {
                                // Rendering + WallpaperManager.setBitmap run on Dispatchers.IO inside apply().
                                val message = try {
                                    LockScreenWallpaperApplier.apply(context, selected, rememberForHour = true)
                                    appliedMessage
                                } catch (e: java.io.IOException) {
                                    applyFailedMessage
                                } catch (e: SecurityException) {
                                    applyFailedMessage
                                } catch (e: OutOfMemoryError) {
                                    applyFailedMessage
                                } finally {
                                    isWorking = false
                                }
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            }
                        }
                    )

                    NurIconButton(
                        icon = Icons.Default.PhotoLibrary,
                        contentDescription = t("lockPreview.gallery"),
                        onClick = { galleryLauncher.launch("image/*") }
                    )

                    NurIconButton(
                        icon = Icons.Default.Share,
                        contentDescription = t("lockPreview.share"),
                        modifier = Modifier.alpha(if (canShare) 1f else 0.4f),
                        onClick = {
                            if (!canShare) return@NurIconButton
                            val selected = verse ?: return@NurIconButton
                            isWorking = true
                            scope.launch {
                                try {
                                    LockScreenWallpaperApplier.shareImage(context, selected)
                                } catch (e: java.io.IOException) {
                                    Toast.makeText(context, shareFailedMessage, Toast.LENGTH_SHORT).show()
                                } catch (e: SecurityException) {
                                    Toast.makeText(context, shareFailedMessage, Toast.LENGTH_SHORT).show()
                                } catch (e: IllegalArgumentException) {
                                    // FileProvider path misconfiguration.
                                    Toast.makeText(context, shareFailedMessage, Toast.LENGTH_SHORT).show()
                                } catch (e: android.content.ActivityNotFoundException) {
                                    Toast.makeText(context, shareFailedMessage, Toast.LENGTH_SHORT).show()
                                } catch (e: OutOfMemoryError) {
                                    Toast.makeText(context, shareFailedMessage, Toast.LENGTH_SHORT).show()
                                } finally {
                                    isWorking = false
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun WallpaperFullScreenItem(
    option: WallpaperOption,
    isSelected: Boolean,
    verse: QuranVerse?,
    selectedFont: LockScreenFontFamily,
    isDownloaded: Boolean,
    isDownloading: Boolean
) {
    val context = LocalContext.current
    var decodedBitmap by remember(option, isDownloaded) { mutableStateOf<Bitmap?>(null) }
    // The card's pixel size: images are decoded just big enough for it, not at full size.
    var cardSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(option, isDownloaded, cardSize) {
        if (cardSize == IntSize.Zero) return@LaunchedEffect
        // Off the main thread and memory-cached, so swiping back to a card is instant.
        decodedBitmap = withContext(Dispatchers.IO) {
            when (option) {
                is WallpaperOption.Local -> LockScreenBackgroundManager.decodeSampleBitmap(
                    context, option.sample, cardSize.width, cardSize.height
                )
                is WallpaperOption.Remote -> if (isDownloaded) {
                    LockScreenBackgroundManager.decodeRemoteSampleBitmap(
                        context, option.sample, cardSize.width, cardSize.height
                    )
                } else null
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { cardSize = it }
    ) {
        when (option) {
            is WallpaperOption.Local -> {
                val bitmap = decodedBitmap
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.FillBounds
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
                }
            }
            is WallpaperOption.Remote -> {
                if (isDownloaded) {
                    if (decodedBitmap != null) {
                        Image(
                            bitmap = decodedBitmap!!.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.FillBounds
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
                    if (isDownloading) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = nurColors.ornament)
                    } else {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.align(Alignment.Center).size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        
        // Overlay Legibility Gradient
        if (isSelected && verse != null && (option is WallpaperOption.Local || isDownloaded)) {
            Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(
                0.0f to Color.Black.copy(alpha = 0.31f),
                0.36f to Color.Black.copy(alpha = 0.13f),
                0.62f to Color.Black.copy(alpha = 0.05f),
                1.0f to Color.Black.copy(alpha = 0.28f)
            )))
            
            // Draw Verse with Compose UI using BoxWithConstraints for proportional scaling
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val cardWidth = maxWidth
                val cardHeight = maxHeight
                
                // Match renderer's safe area calculations proportionally
                val topPadding = cardHeight * 0.27f
                val horizontalPadding = cardWidth * 0.12f
                
                // Scale font size proportionally to the card's width
                val titleFontSize = (cardWidth.value * 0.05f).sp 
                val translationFontSize = (cardWidth.value * 0.038f).sp

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = topPadding, start = horizontalPadding, end = horizontalPadding)
                ) {
                    var cleanTranslation = verse.translation.replace(Regex("\\[[^\\]]+\\]"), "").trim()
                    var surahTitle = verse.surahName

                    val selectedLang = LockScreenWallpaperApplier.getLockScreenLanguage(context)
                    if (selectedLang == "uz_latin" || selectedLang == "uz") {
                        cleanTranslation = com.quranreminder.app.lockscreen.CyrillicToLatinConverter.convert(cleanTranslation)
                        surahTitle = com.quranreminder.app.lockscreen.CyrillicToLatinConverter.convert(surahTitle)
                    }

                    cleanTranslation = com.quranreminder.app.lockscreen.FancyTextConverter.convert(cleanTranslation, selectedFont)
                    surahTitle = com.quranreminder.app.lockscreen.FancyTextConverter.convert(surahTitle, selectedFont)

                    val title = "$surahTitle ${verse.surahNumber}:${verse.ayahNumber}"
                    
                    Text(
                        text = title,
                        style = androidx.compose.ui.text.TextStyle(
                            color = Color.White,
                            fontSize = titleFontSize,
                            fontWeight = FontWeight.Bold,
                            fontFamily = selectedFont.composeFontFamily,
                            shadow = androidx.compose.ui.graphics.Shadow(color = Color.Black.copy(alpha = 0.67f), offset = androidx.compose.ui.geometry.Offset(3f, 3f), blurRadius = 6f)
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = cleanTranslation,
                        style = androidx.compose.ui.text.TextStyle(
                            color = Color.White,
                            fontSize = translationFontSize,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = selectedFont.composeFontFamily,
                            shadow = androidx.compose.ui.graphics.Shadow(color = Color.Black.copy(alpha = 0.73f), offset = androidx.compose.ui.geometry.Offset(4f, 4f), blurRadius = 8f)
                        )
                    )
                }
            }
        }
    }
}

