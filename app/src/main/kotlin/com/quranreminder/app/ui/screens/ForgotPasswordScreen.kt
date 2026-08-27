package com.quranreminder.app.ui.screens

import android.net.Uri
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.quranreminder.app.auth.SessionManager
import com.quranreminder.app.ui.theme.NurColors
import com.quranreminder.app.ui.theme.NurEmblem
import com.quranreminder.app.ui.theme.t
import kotlinx.coroutines.launch

private enum class ResetStep { RequestEmail, Confirm }

@Composable
fun ForgotPasswordScreen(
    session: SessionManager,
    initialToken: String = "",
    onDone: () -> Unit
) {
    var step by remember { mutableStateOf(if (initialToken.isBlank()) ResetStep.RequestEmail else ResetStep.Confirm) }
    var email by remember { mutableStateOf("") }
    var emailSent by remember { mutableStateOf(false) }
    var token by remember { mutableStateOf(initialToken) }
    var newPassword by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            NurEmblem(size = 56.dp)
            Spacer(Modifier.height(16.dp))
            Text(t("forgotPassword.title"), style = MaterialTheme.typography.displaySmall, color = NurColors.Ink)
            Spacer(Modifier.height(28.dp))

            when (step) {
                ResetStep.RequestEmail -> {
                    Text(
                        t("forgotPassword.requestPrompt"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(20.dp))
                    OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text(t("auth.email")) }, singleLine = true)
                    info?.let {
                        Spacer(Modifier.height(10.dp))
                        Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                    }
                    error?.let {
                        Spacer(Modifier.height(10.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(20.dp))
                    val emptyEmailError = t("forgotPassword.error.emptyEmail")
                    val sentInfo = t("forgotPassword.sent")
                    Button(
                        onClick = {
                            if (email.isBlank()) {
                                error = emptyEmailError
                                return@Button
                            }
                            loading = true
                            error = null
                            scope.launch {
                                session.requestPasswordReset(email.trim())
                                info = sentInfo
                                emailSent = true
                                loading = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        enabled = !loading
                    ) { Text(if (loading) t("auth.loading") else t("forgotPassword.send")) }
                    if (emailSent) {
                        TextButton(onClick = { step = ResetStep.Confirm; error = null }) {
                            Text(t("forgotPassword.haveToken"))
                        }
                    }
                }

                ResetStep.Confirm -> {
                    Text(
                        t("forgotPassword.confirmPrompt"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(20.dp))
                    OutlinedTextField(
                        value = token,
                        onValueChange = { pasted ->
                            token = if (pasted.contains("token=")) {
                                runCatching { Uri.parse(pasted).getQueryParameter("token") }.getOrNull() ?: pasted
                            } else pasted
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(t("forgotPassword.token")) },
                        singleLine = true
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        newPassword,
                        { newPassword = it },
                        Modifier.fillMaxWidth(),
                        label = { Text(t("forgotPassword.newPassword")) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                    error?.let {
                        Spacer(Modifier.height(10.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(20.dp))
                    val invalidError = t("forgotPassword.error.invalid")
                    val resetFailedError = t("forgotPassword.error.failed")
                    Button(
                        onClick = {
                            if (token.isBlank() || newPassword.length < 8) {
                                error = invalidError
                                return@Button
                            }
                            loading = true
                            error = null
                            scope.launch {
                                session.confirmPasswordReset(token.trim(), newPassword)
                                    .onSuccess { onDone() }
                                    .onFailure { error = it.message ?: resetFailedError }
                                loading = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        enabled = !loading
                    ) { Text(if (loading) t("auth.loading") else t("forgotPassword.update")) }
                }
            }

            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onDone) { Text(t("forgotPassword.backToSignIn")) }
        }
    }
}
