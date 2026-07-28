package com.quranreminder.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quranreminder.app.ui.viewmodel.SettingsViewModel

@Composable
fun LanguageSelectionScreen(
    viewModel: SettingsViewModel,
    onLanguageSelected: () -> Unit
) {
    val gradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF0F3042),
            Color(0xFF195353)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradient),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Qur'on Eslatma",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Tilni tanlang / Выберите язык / Select language",
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(48.dp))

            LanguageButton(
                label = "O'zbekcha (Lotin)",
                onClick = {
                    viewModel.setLanguage("uz_latin")
                    onLanguageSelected()
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            LanguageButton(
                label = "Ўзбекча (Кирилл)",
                onClick = {
                    viewModel.setLanguage("uz_cyrillic")
                    onLanguageSelected()
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            LanguageButton(
                label = "Русский",
                onClick = {
                    viewModel.setLanguage("ru")
                    onLanguageSelected()
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            LanguageButton(
                label = "English",
                onClick = {
                    viewModel.setLanguage("en")
                    onLanguageSelected()
                }
            )
        }
    }
}

@Composable
fun LanguageButton(
    label: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.15f)
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
