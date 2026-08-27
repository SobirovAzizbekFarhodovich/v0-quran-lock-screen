package com.quranreminder.app.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
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
import com.quranreminder.app.ui.theme.NurScreenHeader
import com.quranreminder.app.ui.theme.t
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.absoluteValue

sealed class WallpaperOption {
    data class Local(val sample: LockScreenBackgroundManager.Sample) : WallpaperOption()
    data class Remote(val sample: LockScreenBackgroundManager.RemoteSample) : WallpaperOption()
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LockScreenPreviewScreen(navController: NavHostController) {
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
    val appliedMessage = t("lockPreview.applied")

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF2E2E2E))
    ) {
        // Carousel Horizontal Pager
        HorizontalPager(
            state = pagerState,
            beyondBoundsPageCount = 2,
            contentPadding = PaddingValues(horizontal = 48.dp), // Show edge of next/prev cards
            pageSpacing = 16.dp, // Space between cards
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 135.dp, bottom = 120.dp) // Leave space for header, font selector, and bottom bar
        ) { page ->
            // Calculate scale and alpha for carousel effect based on distance from center
            val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
            val absOffset = pageOffset.absoluteValue.coerceIn(0f, 1f)
            
            val scale = 1f - (0.15f * absOffset)
            val alpha = 1f - (0.5f * absOffset)
            val cornerRadius = 36.dp // Fixed corner radius for a phone-like card
            
            val option = allOptions[page]
            val isSelected = page == pagerState.settledPage
            val isDownloaded = if (option is WallpaperOption.Remote) remoteDownloads[option.sample.id] == true else true
            val isDownloading = if (option is WallpaperOption.Remote) activeDownloads[option.sample.id] == true else false
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    }
                    .clip(RoundedCornerShape(cornerRadius))
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

        // Header and Font Selector overlay
        Column(modifier = Modifier.fillMaxWidth()) {
            NurScreenHeader(
                title = t("lockPreview.title"),
                eyebrow = t("lockPreview.eyebrow"),
                leading = {
                    NurIconButton(Icons.Default.ArrowBack, t("common.back"), onClick = { navController.popBackStack() })
                },
                trailing = {
                    NurIconButton(Icons.Default.Refresh, t("lockPreview.newAyah"), onClick = { refreshKey++ })
                }
            )

            // Font Selector Row
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(LockScreenFontFamily.entries) { font ->
                    val isFontSelected = font == selectedFont
                    FilterChip(
                        selected = isFontSelected,
                        onClick = {
                            selectedFont = font
                            LockScreenWallpaperApplier.setLockScreenFont(context, font)
                        },
                        label = {
                            Text(
                                text = font.label,
                                fontFamily = font.composeFontFamily,
                                fontWeight = if (isFontSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Color.Black.copy(alpha = 0.4f),
                            labelColor = Color.White.copy(alpha = 0.8f),
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }

        // Horizontal Pip Indicators near bottom
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 120.dp), // just above the action bar
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            allOptions.forEachIndexed { index, _ ->
                val isActive = index == pagerState.currentPage
                val width by animateDpAsState(targetValue = if (isActive) 16.dp else 6.dp, animationSpec = tween(250))
                Box(
                    modifier = Modifier
                        .width(width)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (isActive) Color.White else Color.White.copy(alpha = 0.3f))
                )
            }
        }
        
        // Bottom Action Bar overlay
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                .padding(bottom = 32.dp, top = 20.dp, start = 20.dp, end = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                modifier = Modifier.weight(1f),
                enabled = verse != null && allOptions.getOrNull(pagerState.currentPage).let { it !is WallpaperOption.Remote || remoteDownloads[it.sample.id] == true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(vertical = 14.dp),
                onClick = {
                    val selected = verse ?: return@Button
                    scope.launch {
                        LockScreenWallpaperApplier.apply(context, selected, rememberForHour = true)
                        Toast.makeText(context, appliedMessage, Toast.LENGTH_SHORT).show()
                    }
                }
            ) {
                Icon(Icons.Default.Wallpaper, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(t("lockPreview.apply"), fontWeight = FontWeight.Bold)
            }

            IconButton(
                onClick = { galleryLauncher.launch("image/*") },
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(14.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
                    .padding(4.dp)
            ) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = t("lockPreview.gallery"), tint = Color.White)
            }
            
            IconButton(
                enabled = verse != null,
                onClick = {
                    val selected = verse ?: return@IconButton
                    scope.launch { LockScreenWallpaperApplier.shareImage(context, selected) }
                },
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(14.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
                    .padding(4.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = t("lockPreview.share"), tint = Color.White)
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

    LaunchedEffect(option, isDownloaded) {
        if (option is WallpaperOption.Remote && isDownloaded) {
            withContext(Dispatchers.IO) {
                val file = File(context.filesDir, "remote_bg_${option.sample.id}.png")
                if (file.exists()) {
                    decodedBitmap = BitmapFactory.decodeFile(file.absolutePath)
                }
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        when (option) {
            is WallpaperOption.Local -> {
                Image(
                    painter = painterResource(id = option.sample.resId),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )
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
                        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF1A5276)))
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF1A5276)))
                    if (isDownloading) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color.White)
                    } else {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.align(Alignment.Center).size(64.dp), tint = Color.White.copy(alpha = 0.7f))
                    }
                }
            }
        }
        
        // Overlay Legibility Gradient
        if (isSelected && verse != null && (option is WallpaperOption.Local || isDownloaded)) {
            Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(
                0.0f to Color(0x50000000),
                0.36f to Color(0x22000000),
                0.62f to Color(0x0C000000),
                1.0f to Color(0x48000000)
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
                            shadow = androidx.compose.ui.graphics.Shadow(color = Color(0xAA000000), offset = androidx.compose.ui.geometry.Offset(3f, 3f), blurRadius = 6f)
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
                            shadow = androidx.compose.ui.graphics.Shadow(color = Color(0xBA000000), offset = androidx.compose.ui.geometry.Offset(4f, 4f), blurRadius = 8f)
                        )
                    )
                }
            }
        }
    }
}

