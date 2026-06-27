package com.quranreminder.app.ui.screens

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.quranreminder.app.database.QuranDatabase
import com.quranreminder.app.database.QuranVerse
import com.quranreminder.app.lockscreen.LockScreenBackgroundManager
import com.quranreminder.app.lockscreen.LockScreenVerseSelector
import com.quranreminder.app.lockscreen.LockScreenWallpaperApplier
import com.quranreminder.app.lockscreen.LockScreenWallpaperRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LockScreenPreviewScreen(navController: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var verse by remember { mutableStateOf<QuranVerse?>(null) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var refreshKey by remember { mutableStateOf(0) }
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

    LaunchedEffect(refreshKey) {
        withContext(Dispatchers.IO) {
            val database = QuranDatabase.getDatabase(context)
            val selectedVerse = LockScreenVerseSelector.select(context, database)
            val bitmap = selectedVerse?.let { LockScreenWallpaperRenderer.render(context, it) }
            withContext(Dispatchers.Main) {
                verse = selectedVerse
                preview = bitmap
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Lock-screen preview",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            IconButton(onClick = { refreshKey++ }) {
                Icon(Icons.Default.Refresh, contentDescription = "Yangi preview")
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(9f / 16f),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
                        refreshKey++
                    }
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
                                LockScreenWallpaperApplier.apply(context, selected, rememberForToday = true)
                                Toast.makeText(context, "Lock-screen yangilandi", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(Icons.Default.Wallpaper, contentDescription = null)
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
                        Text("Ulashish")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun BackgroundPreviewControls(
    onUseSystemWallpaper: () -> Unit,
    onPickGallery: () -> Unit,
    onSampleSelected: (LockScreenBackgroundManager.Sample) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
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
        }
    }
}
