package com.quranreminder.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.quranreminder.app.auth.AuthUser
import com.quranreminder.app.auth.SessionManager
import com.quranreminder.app.ui.theme.NurColors
import com.quranreminder.app.ui.theme.NurEmblem
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(session: SessionManager, onAuthenticated: (AuthUser, Boolean) -> Unit) {
    var isSignUp by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            NurEmblem(size = 64.dp)
            Spacer(Modifier.height(16.dp))
            Text("Nur", style = MaterialTheme.typography.displaySmall, color = NurColors.Ink)
            Text(
                if (isSignUp) "Hisob yarating" else "Hisobingizga kiring",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(28.dp))
            if (isSignUp) {
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Ismingiz") }, singleLine = true)
                Spacer(Modifier.height(12.dp))
            }
            OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text("Email") }, singleLine = true)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                password,
                { password = it },
                Modifier.fillMaxWidth(),
                label = { Text("Parol (kamida 8 belgi)") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )
            error?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    if (email.isBlank() || password.length < 8 || (isSignUp && name.isBlank())) {
                        error = "Ma’lumotlarni to‘liq kiriting. Parol kamida 8 belgidan iborat bo‘lsin."
                        return@Button
                    }
                    loading = true
                    error = null
                    scope.launch {
                        val result = if (isSignUp) session.signUp(name.trim(), email.trim(), password) else session.signIn(email.trim(), password)
                        result.onSuccess { onAuthenticated(it, isSignUp) }
                            .onFailure { error = it.message ?: "Kirishda xatolik yuz berdi" }
                        loading = false
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = !loading
            ) { Text(if (loading) "Kutilmoqda..." else if (isSignUp) "Ro‘yxatdan o‘tish" else "Kirish") }
            TextButton(onClick = { isSignUp = !isSignUp; error = null }) {
                Text(if (isSignUp) "Hisobingiz bormi? Kirish" else "Hisobingiz yo‘qmi? Ro‘yxatdan o‘ting")
            }
        }
    }
}
