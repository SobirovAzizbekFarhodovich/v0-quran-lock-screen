package com.quranreminder.app.ui.screens

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.quranreminder.app.ui.theme.QuranReminderTheme
import com.quranreminder.app.ui.theme.nurColors
import com.quranreminder.app.ui.theme.t
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue

sealed class WallpaperOption {
    data class Local(val sample: LockScreenBackgroundManager.Sample) : WallpaperOption()
    data class Remote(val sample: LockScreenBackgroundManager.RemoteSample) : WallpaperOption()
    /** The user's own image; [version] changes with every new pick from the same staging path. */
    data class Custom(val path: String, val version: Long) : WallpaperOption()
}

private val CardShape = RoundedCornerShape(30.dp)
private val ThumbShape = RoundedCornerShape(14.dp)

/**
 * Lock-screen background picker: a large live preview of the real lock screen (clock and the
 * rendered ayah) with its neighbours peeking in, a strip of thumbnails, the lettering choice,
 * the home-screen option and one apply button. Nothing is saved until "apply".
 */
@OptIn(ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun LockScreenPreviewScreen(navController: NavHostController, surah: Int = -1, ayah: Int = -1) {
    // The preview is an immersive, always-dark stage for the wallpaper, in both app themes.
    QuranReminderTheme(darkTheme = true) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val c = nurColors

        var verse by remember { mutableStateOf<QuranVerse?>(null) }
        var selectedFont by remember { mutableStateOf(LockScreenWallpaperApplier.getLockScreenFont(context)) }
        var refreshKey by remember { mutableStateOf(0) }
        // An ayah opened from the reader is shown until the user asks for another one.
        var useRequestedAyah by remember { mutableStateOf(surah > 0 && ayah > 0) }

        var remoteDownloads by remember { mutableStateOf(mapOf<String, Boolean>()) }
        var activeDownloads by remember { mutableStateOf(setOf<String>()) }
        var custom by remember {
            mutableStateOf(LockScreenBackgroundManager.currentCustomPath(context)?.let { WallpaperOption.Custom(it, 0L) })
        }

        val allOptions = remember(custom) {
            buildList {
                custom?.let { add(it) }
                addAll(LockScreenBackgroundManager.samples.map { WallpaperOption.Local(it) })
                addAll(LockScreenBackgroundManager.remoteSamples.map { WallpaperOption.Remote(it) })
            }
        }
        val options by rememberUpdatedState(allOptions)

        // Open on the background that is actually in use, not always on the first one.
        val initialPage = remember {
            val source = LockScreenBackgroundManager.getSelectedSource(context)
            val id = LockScreenBackgroundManager.getSelectedSampleId(context)
            allOptions.indexOfFirst {
                when (it) {
                    is WallpaperOption.Custom -> source == "custom"
                    is WallpaperOption.Local -> source == "sample" && it.sample.id == id
                    is WallpaperOption.Remote -> source == "remote" && it.sample.id == id
                }
            }.coerceAtLeast(0)
        }
        val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { options.size })
        val thumbsState = rememberLazyListState(initialFirstVisibleItemIndex = (initialPage - 1).coerceAtLeast(0))

        val noImageChosenMessage = t("lockPreview.noImageChosen")
        val fullscreenLabel = t("lockPreview.fullscreen")
        val imageLoadedMessage = t("lockPreview.imageLoaded")
        val applyFailedMessage = t("lockPreview.applyFailed")
        val shareFailedMessage = t("lockPreview.shareFailed")
        val downloadFailedMessage = t("lockPreview.downloadFailed")
        var applyToHome by remember { mutableStateOf(LockScreenWallpaperApplier.isApplyToHomeEnabled(context)) }
        val showHomeHint = remember { LockScreenWallpaperApplier.lockScreenShowsHomeInDarkMode(context) }
        var isWorking by remember { mutableStateOf(false) }
        var justApplied by remember { mutableStateOf(false) }
        // Gold pulse on the preview frame when the wallpaper has been set.
        val appliedPulse = remember { Animatable(0f) }

        val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                val path = withContext(Dispatchers.IO) { LockScreenBackgroundManager.stageCustomFromUri(context, uri) }
                if (path != null) {
                    custom = WallpaperOption.Custom(path, System.currentTimeMillis())
                    // The new pick becomes the first page; glide to it.
                    pagerState.animateScrollToPage(0)
                } else {
                    Toast.makeText(context, noImageChosenMessage, Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Swiping only previews; a remote background is downloaded when it comes to rest.
        LaunchedEffect(pagerState.settledPage) {
            val option = allOptions.getOrNull(pagerState.settledPage)
            if (option is WallpaperOption.Remote && remoteDownloads[option.sample.id] != true &&
                option.sample.id !in activeDownloads
            ) {
                activeDownloads = activeDownloads + option.sample.id
                val success = LockScreenBackgroundManager.downloadRemoteSample(context, option.sample)
                activeDownloads = activeDownloads - option.sample.id
                if (success) {
                    remoteDownloads = remoteDownloads + (option.sample.id to true)
                    Toast.makeText(context, imageLoadedMessage, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, downloadFailedMessage, Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Keep the chosen thumbnail in view while swiping the big preview.
        LaunchedEffect(pagerState.currentPage) {
            val visible = thumbsState.layoutInfo.visibleItemsInfo
            // +1: the gallery tile is the strip's first item.
            val item = pagerState.currentPage + 1
            if (visible.none { it.index == item && it.offset >= 0 && it.offset + it.size <= thumbsState.layoutInfo.viewportEndOffset }) {
                thumbsState.animateScrollToItem((item - 2).coerceAtLeast(0))
            }
        }

        LaunchedEffect(refreshKey) {
            withContext(Dispatchers.IO) {
                val database = QuranDatabase.getDatabase(context)
                val selectedVerse = (if (useRequestedAyah) database.quranVerseDao().getVerseOnce(surah, ayah) else null)
                    ?: if (refreshKey > 0) LockScreenVerseSelector.select(context, database, forceNew = true)
                    else LockScreenVerseSelector.select(context, database)
                val downloadMap = LockScreenBackgroundManager.remoteSamples.associate {
                    it.id to LockScreenBackgroundManager.isRemoteSampleDownloaded(context, it)
                }
                withContext(Dispatchers.Main) {
                    verse = selectedVerse
                    remoteDownloads = downloadMap
                }
            }
        }

        val configuration = LocalConfiguration.current
        val screenRatio = remember(configuration) {
            minOf(configuration.screenWidthDp, configuration.screenHeightDp).toFloat() /
                maxOf(configuration.screenWidthDp, configuration.screenHeightDp).coerceAtLeast(1)
        }
        val currentOption = allOptions.getOrNull(pagerState.currentPage)
        val canApply = !isWorking && verse != null &&
            currentOption.let { it !is WallpaperOption.Remote || remoteDownloads[it.sample.id] == true }
        val canShare = !isWorking && verse != null

        // Entrance: the preview rises into place, then the controls follow one after another.
        val enter = remember { Animatable(0f) }
        // Full-screen look at one background: opened by a tap on the card or by pulling it down.
        var fullscreenPage by remember { mutableStateOf<Int?>(null) }
        var cardBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
        // 0 = the card in the pager, 1 = full screen. Driven straight by the finger while the
        // card is pulled down, by a spring otherwise.
        val expand = remember { Animatable(0f) }
        var openOrigin by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
        var rootSize by remember { mutableStateOf(IntSize.Zero) }
        var centredBackground by remember { mutableStateOf<Bitmap?>(null) }
        var centredVerse by remember { mutableStateOf<ImageBitmap?>(null) }
        val minPull = with(LocalDensity.current) { 200.dp.toPx() }
        // Pulling by this much takes the card all the way: its bottom edge stays under the finger.
        fun pullDistance(): Float = (rootSize.height - (openOrigin?.bottom ?: 0f)).coerceAtLeast(minPull)
        fun beginFullscreen(page: Int) {
            if (fullscreenPage == null) {
                openOrigin = cardBounds
                fullscreenPage = page
            }
        }
        fun openFullscreen(page: Int, velocity: Float = 0f) {
            beginFullscreen(page)
            scope.launch { expand.animateTo(1f, spring(dampingRatio = 0.86f, stiffness = 320f), initialVelocity = velocity) }
        }
        fun closeFullscreen(velocity: Float = 0f) {
            scope.launch {
                // Settles back into the card without overshooting.
                expand.animateTo(0f, spring(dampingRatio = 1f, stiffness = 400f), initialVelocity = velocity)
                fullscreenPage = null
            }
        }
        LaunchedEffect(Unit) { enter.animateTo(1f, tween(650, easing = FastOutSlowInEasing)) }
        fun Modifier.entrance(step: Int) = graphicsLayer {
            val p = ((enter.value - step * 0.12f) / (1f - step * 0.12f)).coerceIn(0f, 1f)
            alpha = p
            translationY = (1f - p) * 28.dp.toPx()
        }

        Box(Modifier.fillMaxSize().onSizeChanged { rootSize = it }) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to c.bandDeep,
                        0.45f to MaterialTheme.colorScheme.background,
                        1f to MaterialTheme.colorScheme.background
                    )
                )
                // Insets ignoring visibility: hiding the bars for the full-screen view must not
                // shift this page (that jump made the card land in the wrong place and blink).
                .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
                .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility)
        ) {
            // Top bar: back · centred title · share
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NurIconButton(Icons.Default.ArrowBack, t("common.back"), onClick = { navController.popBackStack() })
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        t("lockPreview.title"),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        t("lockPreview.eyebrow"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                NurIconButton(
                    icon = Icons.Default.Share,
                    contentDescription = t("lockPreview.share"),
                    modifier = Modifier.graphicsLayer { alpha = if (canShare) 1f else 0.4f },
                    onClick = {
                        val selected = verse ?: return@NurIconButton
                        if (!canShare) return@NurIconButton
                        isWorking = true
                        scope.launch {
                            try {
                                LockScreenWallpaperApplier.shareImage(context, selected)
                            } catch (e: Exception) {
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

            // The big preview, neighbours peeking in at the sides.
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .graphicsLayer {
                        val p = enter.value
                        alpha = p
                        scaleX = 0.92f + 0.08f * p
                        scaleY = 0.92f + 0.08f * p
                    }
            ) {
                val cardWidth = minOf(maxWidth * 0.66f, maxHeight * screenRatio)
                val sidePadding = ((maxWidth - cardWidth) / 2).coerceAtLeast(0.dp)
                HorizontalPager(
                    state = pagerState,
                    beyondBoundsPageCount = 1,
                    contentPadding = PaddingValues(horizontal = sidePadding),
                    pageSpacing = 14.dp,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val offset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                        .absoluteValue.coerceIn(0f, 1f)
                    val option = allOptions[page]
                    val isDownloaded = option !is WallpaperOption.Remote || remoteDownloads[option.sample.id] == true
                    val isCentred = page == pagerState.currentPage
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .width(cardWidth)
                                .aspectRatio(screenRatio)
                                .then(
                                    if (isCentred) {
                                        Modifier
                                            .onGloballyPositioned { cardBounds = it.boundsInRoot() }
                                            .draggable(
                                                orientation = Orientation.Vertical,
                                                state = rememberDraggableState { delta ->
                                                    // Only a pull downwards starts it; once started it
                                                    // follows the finger both ways.
                                                    if (fullscreenPage == null && delta <= 0f) return@rememberDraggableState
                                                    beginFullscreen(page)
                                                    scope.launch {
                                                        expand.snapTo((expand.value + delta / pullDistance()).coerceIn(0f, 1f))
                                                    }
                                                },
                                                onDragStopped = { velocity ->
                                                    if (fullscreenPage != null) {
                                                        val v = velocity / pullDistance()
                                                        if (expand.value > 0.3f || velocity > 1200f) openFullscreen(page, v)
                                                        else closeFullscreen(v)
                                                    }
                                                }
                                            )
                                            .clickable(
                                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                                indication = null,
                                                onClickLabel = fullscreenLabel,
                                                role = Role.Button
                                            ) { openFullscreen(page) }
                                    } else Modifier
                                )
                                .graphicsLayer {
                                    // Continuous with the finger: neighbours shrink, dim and tilt away.
                                    val s = 1f - 0.14f * offset
                                    scaleX = s
                                    scaleY = s
                                    alpha = if (fullscreenPage == page) 0f else 1f - 0.45f * offset
                                    rotationY = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                                        .coerceIn(-1f, 1f) * -8f
                                    cameraDistance = 14f * density
                                }
                                .clip(CardShape)
                                .border(
                                    BorderStroke(
                                        (1.5f + 2f * appliedPulse.value).dp,
                                        c.ornament.copy(alpha = if (isCentred) 0.85f else 0.25f)
                                    ),
                                    CardShape
                                )
                        ) {
                            LockPreviewCard(
                                option = option,
                                verse = verse,
                                showVerse = isCentred,
                                selectedFont = selectedFont,
                                isDownloaded = isDownloaded,
                                isDownloading = option is WallpaperOption.Remote && option.sample.id in activeDownloads,
                                onRendered = if (isCentred) { bg, v -> centredBackground = bg; centredVerse = v } else null
                            )
                        }
                    }
                }
                // "Another ayah" rides on the bottom edge of the preview.
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 14.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)), CircleShape)
                        .clickable(role = Role.Button) {
                            useRequestedAyah = false
                            refreshKey++
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val spin = remember { Animatable(0f) }
                    LaunchedEffect(refreshKey) { if (refreshKey > 0) spin.animateTo(spin.value + 360f, tween(500)) }
                    Icon(
                        Icons.Default.Refresh, null, tint = Color.White,
                        modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = spin.value }
                    )
                    Text(t("lockPreview.otherAyah"), style = MaterialTheme.typography.labelMedium, color = Color.White)
                }
            }

            // Backgrounds strip
            SectionLabel(t("lockPreview.section.background"), Modifier.entrance(1))
            LazyRow(
                state = thumbsState,
                modifier = Modifier.fillMaxWidth().entrance(1),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item(key = "gallery") {
                    GalleryTile(label = t("lockPreview.gallery"), onClick = { galleryLauncher.launch("image/*") })
                }
                itemsIndexed(allOptions, key = { _, o -> o.key() }) { index, option ->
                    BackgroundThumb(
                        option = option,
                        selected = index == pagerState.currentPage,
                        isDownloaded = option !is WallpaperOption.Remote || remoteDownloads[option.sample.id] == true,
                        isDownloading = option is WallpaperOption.Remote && option.sample.id in activeDownloads,
                        description = if (option is WallpaperOption.Custom) t("lockPreview.yourImage") else option.label(),
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } }
                    )
                }
            }

            // Lettering
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 16.dp, top = 10.dp)
                    .entrance(2),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    t("lockPreview.section.font"),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(end = 14.dp)
                )
                FontSegments(
                    selected = selectedFont,
                    onSelect = {
                        selectedFont = it
                        LockScreenWallpaperApplier.setLockScreenFont(context, it)
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            // Also on the home screen — one row; the explanation folds out of the ⓘ.
            var infoOpen by remember { mutableStateOf(false) }
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).entrance(3)) {
                // The ⓘ sits beside the switch, not inside it: a tap on it must never flip the
                // switch (that would put the ayah on the home screen and replace the user's own
                // home wallpaper). Label and switch toggle; the icon only explains.
                val homeLabel = t("lockPreview.applyToHome")
                val setHome: (Boolean) -> Unit = {
                    applyToHome = it
                    LockScreenWallpaperApplier.setApplyToHome(context, it)
                }
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        homeLabel,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .clickable { setHome(!applyToHome) }
                            .padding(start = 4.dp, top = 12.dp, bottom = 12.dp, end = 2.dp)
                    )
                    val infoLabel = t("lockPreview.info")
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable(onClickLabel = infoLabel, role = Role.Button) { infoOpen = !infoOpen }
                            .semantics { contentDescription = infoLabel },
                        contentAlignment = Alignment.Center
                    ) {
                        val tint by animateColorAsState(
                            if (infoOpen || showHomeHint) c.ornament else MaterialTheme.colorScheme.onSurfaceVariant,
                            label = "infoTint"
                        )
                        Icon(Icons.Outlined.Info, null, tint = tint, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    Switch(
                        checked = applyToHome,
                        onCheckedChange = setHome,
                        modifier = Modifier.semantics { contentDescription = homeLabel }
                    )
                }
                AnimatedVisibility(
                    visible = infoOpen,
                    enter = expandVertically(tween(260)) + fadeIn(tween(260)),
                    exit = shrinkVertically(tween(200)) + fadeOut(tween(160))
                ) {
                    Column(Modifier.padding(start = 4.dp, end = 4.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(t("lockPreview.applyToHome.desc"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (showHomeHint) {
                            Text(t("lockPreview.applyToHome.hint"), style = MaterialTheme.typography.bodySmall, color = c.ornament)
                        }
                    }
                }
            }

            // Apply
            val buttonScale by animateFloatAsState(
                if (justApplied) 1.03f else 1f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "applyScale"
            )
            Button(
                onClick = {
                    val selected = verse ?: return@Button
                    val option = allOptions.getOrNull(pagerState.currentPage) ?: return@Button
                    isWorking = true
                    scope.launch {
                        val ok = try {
                            withContext(Dispatchers.IO) {
                                // The choice is saved only now, on apply.
                                if (useRequestedAyah) LockScreenWallpaperApplier.saveSelectedVerse(context, selected)
                                val chosen = when (option) {
                                    is WallpaperOption.Local -> { LockScreenBackgroundManager.setSample(context, option.sample); true }
                                    is WallpaperOption.Remote -> { LockScreenBackgroundManager.setRemoteSample(context, option.sample); true }
                                    is WallpaperOption.Custom -> LockScreenBackgroundManager.commitCustom(context, option.path)
                                }
                                if (chosen) LockScreenWallpaperApplier.apply(context, selected, rememberForHour = true)
                                chosen
                            }
                        } catch (e: Exception) {
                            false
                        } catch (e: OutOfMemoryError) {
                            false
                        } finally {
                            isWorking = false
                        }
                        if (ok) {
                            if (option is WallpaperOption.Custom) {
                                LockScreenBackgroundManager.currentCustomPath(context)?.let { custom = WallpaperOption.Custom(it, option.version) }
                            }
                            justApplied = true
                            appliedPulse.snapTo(1f)
                            appliedPulse.animateTo(0f, tween(900))
                            delay(900)
                            justApplied = false
                        } else {
                            Toast.makeText(context, applyFailedMessage, Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                enabled = canApply || isWorking || justApplied,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 12.dp)
                    .height(56.dp)
                    .graphicsLayer { scaleX = buttonScale; scaleY = buttonScale }
                    .entrance(4),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = c.ornament,
                    contentColor = c.bandDeep,
                    disabledContainerColor = c.ornament.copy(alpha = 0.35f),
                    disabledContentColor = c.bandDeep.copy(alpha = 0.6f)
                )
            ) {
                val state = when {
                    isWorking -> 1
                    justApplied -> 2
                    else -> 0
                }
                AnimatedContent(
                    targetState = state,
                    transitionSpec = {
                        (fadeIn(tween(180)) + scaleIn(tween(220), initialScale = 0.8f)) togetherWith
                            (fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.8f))
                    },
                    label = "applyContent"
                ) { s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        when (s) {
                            1 -> CircularProgressIndicator(Modifier.size(22.dp), color = c.bandDeep, strokeWidth = 2.5.dp)
                            2 -> {
                                Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(t("lockPreview.appliedShort"), style = MaterialTheme.typography.labelLarge)
                            }
                            else -> {
                                Icon(Icons.Default.Wallpaper, null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(t("lockPreview.apply"), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        }

        fullscreenPage?.let { page ->
            val option = allOptions.getOrNull(page)
            if (option != null) {
                FullScreenLockPreview(
                    option = option,
                    verse = verse,
                    selectedFont = selectedFont,
                    isDownloaded = option !is WallpaperOption.Remote || remoteDownloads[option.sample.id] == true,
                    origin = openOrigin ?: cardBounds,
                    progress = expand,
                    screenSize = rootSize,
                    placeholderBackground = centredBackground,
                    placeholderVerse = centredVerse,
                    onClose = { velocity -> closeFullscreen(velocity) }
                )
            }
        }
        }
    }
}

/**
 * The background exactly as the lock screen will show it, edge to edge with the system bars
 * hidden. It grows out of the preview card and shrinks back into it; a tap, a pull down or
 * Back closes it.
 */
@Composable
private fun FullScreenLockPreview(
    option: WallpaperOption,
    verse: QuranVerse?,
    selectedFont: LockScreenFontFamily,
    isDownloaded: Boolean,
    origin: androidx.compose.ui.geometry.Rect?,
    progress: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
    screenSize: IntSize,
    placeholderBackground: Bitmap?,
    placeholderVerse: ImageBitmap?,
    onClose: (velocity: Float) -> Unit
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val view = androidx.compose.ui.platform.LocalView.current
    val drag = remember { Animatable(0f) }
    // Known from the first frame: an unmeasured first frame drew nothing and the card blinked.
    var size by remember { mutableStateOf(screenSize) }
    val closeDistance = with(density) { 120.dp.toPx() }

    fun close() {
        // Any pull-away offset eases out together with the shrink back into the card.
        scope.launch { drag.animateTo(0f, spring(dampingRatio = 1f, stiffness = 400f)) }
        onClose(0f)
    }

    // Only once it has landed at full size do the sharp images load and the system bars step
    // aside: doing either mid-motion is what made it stall.
    val atFull by remember { derivedStateOf { progress.value >= 0.999f && !progress.isRunning } }
    val closing by remember { derivedStateOf { progress.targetValue < 0.5f } }
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(atFull) { if (atFull) settled = true }
    androidx.activity.compose.BackHandler { close() }

    // Edge to edge like the real lock screen: hide the status and navigation bars meanwhile.
    val barsController = remember(view) {
        (view.context as? android.app.Activity)?.window?.let { androidx.core.view.WindowCompat.getInsetsController(it, view) }
    }
    LaunchedEffect(settled, closing) {
        if (settled && !closing) {
            barsController?.systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            barsController?.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
    }
    DisposableEffect(Unit) {
        onDispose { barsController?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars()) }
    }

    // A hint about closing shows once the card has landed, then fades away.
    var showHint by remember { mutableStateOf(false) }
    LaunchedEffect(settled) {
        if (!settled) return@LaunchedEffect
        showHint = true
        delay(2200)
        showHint = false
    }
    val hintAlpha by animateFloatAsState(if (showHint && !closing) 1f else 0f, tween(300), label = "hint")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            // The backdrop dims in with the card and lightens as it is pulled away. Animated
            // values are read only at draw time, so the motion never waits on recomposition.
            .drawBehind {
                val p = progress.value.coerceIn(0f, 1f)
                val pulled = (drag.value / 900f).coerceIn(0f, 0.7f)
                drawRect(Color.Black.copy(alpha = 0.94f * p * (1f - pulled)))
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (drag.value > closeDistance) close() else drag.animateTo(0f, spring(dampingRatio = 0.7f))
                        }
                    },
                    onDragCancel = { scope.launch { drag.animateTo(0f, spring()) } },
                    onVerticalDrag = { change, dy ->
                        change.consume()
                        scope.launch { drag.snapTo((drag.value + dy).coerceAtLeast(0f)) }
                    }
                )
            }
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) { close() }
    ) {
        if (size != IntSize.Zero) {
            val startScale = origin?.let { it.width / size.width } ?: 0.7f
            val startDx = origin?.let { it.center.x - size.width / 2f } ?: 0f
            val startDy = origin?.let { it.center.y - size.height / 2f } ?: 0f
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // The spring may overshoot a hair past 1: fine for scale, clamped for
                        // everything that must stay in range (corner radius, offsets).
                        val p = progress.value
                        val pc = p.coerceIn(0f, 1f)
                        val dragScale = 1f - (drag.value / size.height * 0.25f).coerceIn(0f, 0.25f)
                        val s = (startScale + (1f - startScale) * p) * dragScale
                        scaleX = s
                        scaleY = s
                        translationX = startDx * (1f - pc)
                        translationY = startDy * (1f - pc) + drag.value * 0.6f
                        // Corners as on the card, rounding again while the view is pulled away.
                        val corner = (30.dp.toPx() * (1f - pc) + 112.dp.toPx() * (1f - dragScale))
                            .coerceAtLeast(0f) / s.coerceAtLeast(0.1f)
                        shape = RoundedCornerShape(corner)
                        clip = true
                    }
            ) {
                LockPreviewCard(
                    option = option,
                    verse = verse,
                    showVerse = true,
                    selectedFont = selectedFont,
                    isDownloaded = isDownloaded,
                    isDownloading = false,
                    placeholderBackground = placeholderBackground,
                    placeholderVerse = placeholderVerse,
                    renderEnabled = settled,
                    initialSize = size
                )
            }
            Text(
                text = t("lockPreview.fullscreenHint"),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 36.dp)
                    // Gone the moment it starts closing; it must not ride along over the page.
                    .graphicsLayer { alpha = if (closing || progress.targetValue < 1f) 0f else hintAlpha }
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
    }
}

private fun WallpaperOption.key(): String = when (this) {
    is WallpaperOption.Custom -> "custom"
    is WallpaperOption.Local -> sample.id
    is WallpaperOption.Remote -> sample.id
}

private fun WallpaperOption.label(): String = when (this) {
    is WallpaperOption.Custom -> ""
    is WallpaperOption.Local -> sample.label
    is WallpaperOption.Remote -> sample.label
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.4.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 20.dp, top = 8.dp)
    )
}

/** Decodes a background just big enough for a [size] target, off the main thread. */
@Composable
private fun rememberBackgroundBitmap(option: WallpaperOption, isDownloaded: Boolean, size: IntSize): Bitmap? {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, option, isDownloaded, size) {
        if (size == IntSize.Zero) return@produceState
        value = withContext(Dispatchers.IO) {
            when (option) {
                is WallpaperOption.Local ->
                    LockScreenBackgroundManager.decodeSampleBitmap(context, option.sample, size.width, size.height)
                is WallpaperOption.Remote -> if (isDownloaded) {
                    LockScreenBackgroundManager.decodeRemoteSampleBitmap(context, option.sample, size.width, size.height)
                } else null
                is WallpaperOption.Custom ->
                    LockScreenBackgroundManager.decodeSampledFile(option.path, size.width, size.height)
            }
        }
    }
    return bitmap
}

/** One page of the preview: the background, the lock-screen clock and the rendered ayah. */
@Composable
private fun LockPreviewCard(
    option: WallpaperOption,
    verse: QuranVerse?,
    showVerse: Boolean,
    selectedFont: LockScreenFontFamily,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    // The full-screen view starts from what the card already shows, so its first frame is
    // complete; the sharper full-size versions replace them as soon as they are ready.
    placeholderBackground: Bitmap? = null,
    placeholderVerse: ImageBitmap? = null,
    onRendered: ((Bitmap?, ImageBitmap?) -> Unit)? = null,
    // False while the full-screen view is still growing: decoding and uploading full-size
    // bitmaps mid-animation is what made it stall, so the placeholders carry the motion.
    renderEnabled: Boolean = true,
    initialSize: IntSize = IntSize.Zero
) {
    var cardSize by remember { mutableStateOf(initialSize) }
    val bitmap = rememberBackgroundBitmap(option, isDownloaded, if (renderEnabled) cardSize else IntSize.Zero)
        ?: placeholderBackground
    var renderedVerse by remember { mutableStateOf<ImageBitmap?>(null) }
    if (onRendered != null) {
        LaunchedEffect(bitmap, renderedVerse) { onRendered(bitmap, renderedVerse) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .onSizeChanged { cardSize = it }
    ) {
        Crossfade(targetState = bitmap, animationSpec = tween(350), label = "bg") { bmp ->
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    // Same centre-crop as the wallpaper renderer.
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(Modifier.fillMaxSize())
            }
        }
        if (option is WallpaperOption.Remote && !isDownloaded) {
            Box(Modifier.align(Alignment.Center)) {
                if (isDownloading) {
                    CircularProgressIndicator(color = nurColors.ornament)
                } else {
                    Icon(Icons.Default.CloudDownload, null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LockCardContent(verse, showVerse, selectedFont, cardSize, placeholderVerse, renderEnabled) { renderedVerse = it }
        }
    }
}

@Composable
private fun LockCardContent(
    verse: QuranVerse?,
    showVerse: Boolean,
    selectedFont: LockScreenFontFamily,
    cardSize: IntSize,
    placeholderVerse: ImageBitmap?,
    renderEnabled: Boolean,
    onVerseRendered: (ImageBitmap) -> Unit
) {
    val context = LocalContext.current
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0.0f to Color.Black.copy(alpha = 0.30f),
                    0.36f to Color.Black.copy(alpha = 0.10f),
                    0.70f to Color.Black.copy(alpha = 0.04f),
                    1.0f to Color.Black.copy(alpha = 0.28f)
                )
            )
        )
        if (cardSize != IntSize.Zero) LockClock(cardWidthPx = cardSize.width, cardHeightPx = cardSize.height)

        // The verse layer is drawn by the wallpaper renderer itself, at this card's size, so the
        // preview is exactly what "apply" puts on the lock screen. It fades in when it changes.
        if (showVerse && verse != null && cardSize != IntSize.Zero) {
            val language = LockScreenWallpaperApplier.getLockScreenLanguage(context)
            // The previous layer stays up while the next one renders: no blank flash between.
            var overlay by remember { mutableStateOf(placeholderVerse) }
            LaunchedEffect(verse, selectedFont, language, cardSize, renderEnabled) {
                if (!renderEnabled) return@LaunchedEffect
                val image = withContext(Dispatchers.Default) {
                    runCatching {
                        LockScreenWallpaperRenderer.renderTextOverlay(context, verse, cardSize.width, cardSize.height)
                            .asImageBitmap()
                    }.getOrNull()
                }
                if (image != null) {
                    overlay = image
                    onVerseRendered(image)
                }
            }
            Crossfade(targetState = overlay, animationSpec = tween(400), label = "verse") { image ->
                if (image != null) {
                    Image(bitmap = image, contentDescription = null, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

/** The system clock as the lock screen shows it, scaled to the card. */
@Composable
private fun LockClock(cardWidthPx: Int, cardHeightPx: Int) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var now by remember { mutableStateOf(java.util.Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = java.util.Date()
        }
    }
    val locale = remember {
        when (LockScreenWallpaperApplier.getLockScreenLanguage(context)) {
            "uz_cyrillic" -> java.util.Locale.Builder().setLanguage("uz").setScript("Cyrl").build()
            "ru" -> java.util.Locale("ru")
            "en" -> java.util.Locale.ENGLISH
            else -> java.util.Locale("uz")
        }
    }
    val date = remember(now, locale) {
        java.text.SimpleDateFormat("EEEE, d MMMM", locale).format(now).replaceFirstChar { it.titlecase(locale) }
    }
    val time = remember(now) {
        val pattern = if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"
        java.text.SimpleDateFormat(pattern, java.util.Locale.ROOT).format(now)
    }
    val w = with(density) { cardWidthPx.toDp() }
    val h = with(density) { cardHeightPx.toDp() }
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = h * 0.085f),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            date,
            color = Color.White.copy(alpha = 0.9f),
            fontSize = with(density) { (w * 0.037f).toSp() },
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
        Text(
            time,
            color = Color.White.copy(alpha = 0.94f),
            fontSize = with(density) { (w * 0.19f).toSp() },
            fontWeight = FontWeight.SemiBold,
            lineHeight = with(density) { (w * 0.21f).toSp() }
        )
    }
}

@Composable
private fun GalleryTile(label: String, onClick: () -> Unit) {
    val gold = nurColors.ornament
    Column(
        modifier = Modifier
            .size(width = 58.dp, height = 88.dp)
            .clip(ThumbShape)
            .border(BorderStroke(1.5.dp, gold), ThumbShape)
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Add, null, tint = gold, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = gold, maxLines = 1, fontSize = 10.sp)
    }
}

@Composable
private fun BackgroundThumb(
    option: WallpaperOption,
    selected: Boolean,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    description: String,
    onClick: () -> Unit
) {
    val gold = nurColors.ornament
    val density = LocalDensity.current
    val target = remember(density) { with(density) { IntSize(58.dp.roundToPx(), 88.dp.roundToPx()) } }
    val bitmap = rememberBackgroundBitmap(option, isDownloaded, target)
    val ring by animateDpAsState(if (selected) 2.5.dp else 0.dp, spring(stiffness = Spring.StiffnessMediumLow), label = "ring")
    val scale by animateFloatAsState(if (selected) 1f else 0.92f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow), label = "thumbScale")
    Box(
        modifier = Modifier
            .size(width = 58.dp, height = 88.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .border(BorderStroke(ring, gold.copy(alpha = if (selected) 1f else 0f)), ThumbShape)
            .padding(ring + 1.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClickLabel = description, role = Role.Button, onClick = onClick)
            .semantics {
                this.selected = selected
                contentDescription = description
            }
    ) {
        Crossfade(targetState = bitmap, animationSpec = tween(250), label = "thumb") { bmp ->
            if (bmp != null) {
                Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
        if (option is WallpaperOption.Remote && !isDownloaded) {
            Box(
                Modifier.align(Alignment.Center).size(28.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                if (isDownloading) {
                    CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.ArrowDownward, null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
        AnimatedVisibility(
            visible = selected,
            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
            enter = scaleIn(spring(dampingRatio = 0.5f)) + fadeIn(),
            exit = scaleOut() + fadeOut()
        ) {
            Box(
                Modifier.size(18.dp).clip(CircleShape).background(gold),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Check, null, tint = nurColors.bandDeep, modifier = Modifier.size(12.dp))
            }
        }
    }
}

/** "Aa" in each lettering style, with a gold pill that slides to the chosen one. */
@Composable
private fun FontSegments(
    selected: LockScreenFontFamily,
    onSelect: (LockScreenFontFamily) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = nurColors
    val fonts = LockScreenFontFamily.entries
    BoxWithConstraints(
        modifier = modifier
            .height(46.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp)
    ) {
        val segment = maxWidth / fonts.size
        val x by animateDpAsState(
            segment * fonts.indexOf(selected),
            spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
            label = "fontPill"
        )
        Box(
            Modifier
                .offset(x = x)
                .width(segment)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(c.ornament)
        )
        Row(Modifier.fillMaxSize()) {
            fonts.forEach { font ->
                val isSelected = font == selected
                val color by animateColorAsState(
                    if (isSelected) c.bandDeep else MaterialTheme.colorScheme.onSurface,
                    tween(200),
                    label = "fontColor"
                )
                val name = t("lockPreview.font.${font.id}")
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .clickable(onClickLabel = name, role = Role.RadioButton) { onSelect(font) }
                        .semantics {
                            this.selected = isSelected
                            contentDescription = name
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Aa",
                        fontFamily = font.composeFontFamily,
                        fontWeight = if (font == LockScreenFontFamily.STANDART) FontWeight.SemiBold else null,
                        fontSize = (19f * font.sizeScale).sp,
                        color = color
                    )
                }
            }
        }
    }
}
