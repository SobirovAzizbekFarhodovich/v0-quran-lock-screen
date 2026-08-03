package com.quranreminder.app.ui.screens

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.quranreminder.app.database.QuranDatabase
import com.quranreminder.app.database.QuranVerse
import com.quranreminder.app.lockscreen.LockScreenBackgroundManager
import com.quranreminder.app.lockscreen.LockScreenVerseSelector
import com.quranreminder.app.lockscreen.LockScreenWallpaperApplier
import com.quranreminder.app.lockscreen.LockScreenWallpaperRenderer
import com.quranreminder.app.ui.theme.NurIconButton
import com.quranreminder.app.ui.theme.NurScreenHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LockScreenPreviewScreen(navController: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var verse by remember { mutableStateOf<QuranVerse?>(null) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var refreshKey by remember { mutableStateOf(0) }

    // Remote samples download states
    var remoteDownloads by remember { mutableStateOf(mapOf<String, Boolean>()) }
    var activeDownloads by remember { mutableStateOf(mapOf<String, Boolean>()) }

    // Sample backgrounds list for carousel
    val localSamples = LockScreenBackgroundManager.samples
    val totalPages = localSamples.size + 1 // local samples + 1 Server Download Prompt Page

    val pagerState = rememberPagerState(pageCount = { totalPages })

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                LockScreenBackgroundManager.setCustomFromUri(context, uri)
            }
            if (saved) {
                refreshKey++
                Toast.makeText(context, "Gallery rasmi previewga qo'yildi", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Rasm tanlanmadi", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Automatically set background when user swipes to a local sample page
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage in localSamples.indices) {
            val sample = localSamples[pagerState.currentPage]
            LockScreenBackgroundManager.setSample(context, sample)
            refreshKey++
        }
    }

    LaunchedEffect(refreshKey) {
        withContext(Dispatchers.IO) {
            val database = QuranDatabase.getDatabase(context)
            val selectedVerse = LockScreenVerseSelector.select(context, database)
            val bitmap = selectedVerse?.let { LockScreenWallpaperRenderer.render(context, it) }

            // Check downloaded status of remote backgrounds
            val downloadMap = LockScreenBackgroundManager.remoteSamples.associate {
                it.id to LockScreenBackgroundManager.isRemoteSampleDownloaded(context, it)
            }

            withContext(Dispatchers.Main) {
                verse = selectedVerse
                preview = bitmap
                remoteDownloads = downloadMap
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        NurScreenHeader(
            title = "Lock-screen oyati",
            eyebrow = "Kunlik oyat",
            leading = {
                NurIconButton(Icons.Default.ArrowBack, "Orqaga", onClick = { navController.popBackStack() })
            },
            trailing = {
                NurIconButton(Icons.Default.Refresh, "Yangi preview", onClick = { refreshKey++ })
            }
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Horizontal Carousel Pager for Backgrounds
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(9f / 16f)
                    ) { page ->
                        if (page < localSamples.size) {
                            // Wallpaper Preview Card
                            Card(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 4.dp),
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                            ) {
                                val bitmap = preview
                                if (bitmap == null) {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text("Preview tayyorlanmoqda...")
                                    }
                                } else {
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = "Lock-screen preview",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                        } else {
                            // Server Download Prompt Card (at the end of scroll)
                            ServerDownloadPromptCard(
                                onDownloadRequested = {
                                    scope.launch {
                                        listState.animateScrollToItem(index = 1) // Scroll down to server downloads section
                                    }
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Pager Indicators
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        repeat(totalPages) { index ->
                            val isSelected = pagerState.currentPage == index
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .size(if (isSelected) 10.dp else 7.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                    )
                            )
                        }
                    }
                }
            }

            item {
                BackgroundPreviewControls(
                    onUseSystemWallpaper = {
                        LockScreenBackgroundManager.setSystemWallpaper(context)
                        refreshKey++
                        Toast.makeText(context, "Telefon foni previewga qo'yildi", Toast.LENGTH_SHORT).show()
                    },
                    onPickGallery = { galleryLauncher.launch("image/*") },
                    onSampleSelected = { sample ->
                        LockScreenBackgroundManager.setSample(context, sample)
                        val sampleIndex = localSamples.indexOf(sample)
                        if (sampleIndex >= 0) {
                            scope.launch { pagerState.animateScrollToPage(sampleIndex) }
                        }
                        refreshKey++
                    },
                    onRemoteSampleSelected = { remoteSample ->
                        val isDownloaded = remoteDownloads[remoteSample.id] ?: false
                        if (isDownloaded) {
                            LockScreenBackgroundManager.setRemoteSample(context, remoteSample)
                            refreshKey++
                            Toast.makeText(context, "${remoteSample.label} tanlandi", Toast.LENGTH_SHORT).show()
                        } else {
                            // Start downloading
                            activeDownloads = activeDownloads + (remoteSample.id to true)
                            scope.launch {
                                val success = LockScreenBackgroundManager.downloadRemoteSample(context, remoteSample)
                                activeDownloads = activeDownloads - remoteSample.id
                                if (success) {
                                    remoteDownloads = remoteDownloads + (remoteSample.id to true)
                                    LockScreenBackgroundManager.setRemoteSample(context, remoteSample)
                                    refreshKey++
                                    Toast.makeText(context, "${remoteSample.label} yuklandi va tanlandi", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Yuklab olishda xatolik yuz berdi. Server ishlayotganini tekshiring.", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    },
                    remoteDownloads = remoteDownloads,
                    activeDownloads = activeDownloads
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        modifier = Modifier.weight(1f),
                        enabled = verse != null,
                        onClick = {
                            val selected = verse ?: return@Button
                            scope.launch {
                                LockScreenWallpaperApplier.apply(context, selected, rememberForHour = true)
                                Toast.makeText(context, "Lock-screen yangilandi", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(Icons.Default.Wallpaper, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Qo'yish")
                    }
                    Button(
                        modifier = Modifier.weight(1f),
                        enabled = verse != null,
                        onClick = {
                            val selected = verse ?: return@Button
                            scope.launch {
                                LockScreenWallpaperApplier.shareImage(context, selected)
                            }
                        }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Ulashish")
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

/**
 * End-of-scroll card prompting user to download new wallpapers from the server.
 */
@Composable
private fun ServerDownloadPromptCard(
    onDownloadRequested: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1E293B),
                            Color(0xFF0F172A)
                        )
                    )
                )
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF334155))
                        .border(2.dp, Color(0xFF38BDF8), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = "Server Download",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Serverdan yangi rasmlar yuklab olishni xohlaysizmi?",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    ),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Go'zal va sifatli tabiat hamda islomiy fon rasmlarini serverdan bir bosish bilan yuklab oling.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF94A3B8),
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(28.dp))

                Button(
                    onClick = onDownloadRequested,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0284C7),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Serverdan yuklab olish",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun BackgroundPreviewControls(
    onUseSystemWallpaper: () -> Unit,
    onPickGallery: () -> Unit,
    onSampleSelected: (LockScreenBackgroundManager.Sample) -> Unit,
    onRemoteSampleSelected: (LockScreenBackgroundManager.RemoteSample) -> Unit,
    remoteDownloads: Map<String, Boolean>,
    activeDownloads: Map<String, Boolean>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Background tanlash",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = onUseSystemWallpaper
                ) {
                    Text("Telefon foni")
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = onPickGallery
                ) {
                    Text("Gallery")
                }
            }
            LockScreenBackgroundManager.samples.chunked(2).forEach { rowSamples ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowSamples.forEach { sample ->
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = { onSampleSelected(sample) }
                        ) {
                            Text(sample.label)
                        }
                    }
                    if (rowSamples.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Serverdan yuklash",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            LockScreenBackgroundManager.remoteSamples.forEach { remoteSample ->
                val isDownloaded = remoteDownloads[remoteSample.id] ?: false
                val isDownloading = activeDownloads[remoteSample.id] ?: false

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = remoteSample.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    if (isDownloading) {
                        Button(
                            onClick = {},
                            enabled = false,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Yuklanmoqda...")
                        }
                    } else {
                        Button(
                            onClick = { onRemoteSampleSelected(remoteSample) },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(if (isDownloaded) "Tanlash" else "Yuklab olish")
                        }
                    }
                }
            }
        }
    }
}
