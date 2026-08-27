package com.quranreminder.app.ui.screens

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.quranreminder.app.BuildConfig
import com.quranreminder.app.auth.AuthUser
import com.quranreminder.app.auth.SessionManager
import com.quranreminder.app.ui.theme.NurColors
import com.quranreminder.app.ui.theme.NurEmblem
import com.quranreminder.app.ui.theme.t
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
    session: SessionManager,
    onForgotPassword: () -> Unit = {},
    onAuthenticated: (AuthUser, Boolean) -> Unit
) {
    var isSignUp by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            NurEmblem(size = 64.dp)
            Spacer(Modifier.height(16.dp))
            Text(t("auth.brand"), style = MaterialTheme.typography.displaySmall, color = NurColors.Ink)
            Text(
                if (isSignUp) t("auth.title.signUp") else t("auth.title.signIn"),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(28.dp))
            if (isSignUp) {
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(t("auth.name")) }, singleLine = true)
                Spacer(Modifier.height(12.dp))
            }
            OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text(t("auth.email")) }, singleLine = true)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                password,
                { password = it },
                Modifier.fillMaxWidth(),
                label = { Text(t("auth.password")) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )
            error?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(20.dp))
            val incompleteError = t("auth.error.incomplete")
            val failedError = t("auth.error.failed")
            Button(
                onClick = {
                    if (email.isBlank() || password.length < 8 || (isSignUp && name.isBlank())) {
                        error = incompleteError
                        return@Button
                    }
                    loading = true
                    error = null
                    scope.launch {
                        val result = if (isSignUp) session.signUp(name.trim(), email.trim(), password) else session.signIn(email.trim(), password)
                        result.onSuccess { onAuthenticated(it, isSignUp) }
                            .onFailure { error = it.message ?: failedError }
                        loading = false
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = !loading
            ) { Text(if (loading) t("auth.loading") else if (isSignUp) t("auth.signUp") else t("auth.signIn")) }
            if (!isSignUp) {
                TextButton(onClick = onForgotPassword) {
                    Text(t("auth.forgotPassword"))
                }
            }
            TextButton(onClick = { isSignUp = !isSignUp; error = null }) {
                Text(if (isSignUp) t("auth.toggle.toSignIn") else t("auth.toggle.toSignUp"))
            }

            if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Divider(Modifier.weight(1f))
                    Text(t("auth.or"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    Divider(Modifier.weight(1f))
                }
                val unexpectedCredentialError = t("auth.google.unexpectedCredential")
                val noAccountError = t("auth.google.noAccount")
                val couldNotSignInError = t("auth.google.failed")
                val genericGoogleError = t("auth.google.error")
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            loading = true
                            error = null
                            try {
                                val credentialManager = CredentialManager.create(context)
                                val googleIdOption = GetGoogleIdOption.Builder()
                                    .setFilterByAuthorizedAccounts(false)
                                    .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                                    .build()
                                val request = GetCredentialRequest.Builder()
                                    .addCredentialOption(googleIdOption)
                                    .build()
                                val activity = context as? Activity
                                    ?: throw IllegalStateException("Activity topilmadi")
                                val result = credentialManager.getCredential(activity, request)
                                val credential = result.credential
                                if (credential is CustomCredential &&
                                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                                ) {
                                    val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                                    session.signInWithGoogle(idToken)
                                        // No reliable "new account" signal from Google sign-in;
                                        // MainActivity also falls back to checking user.language.
                                        .onSuccess { onAuthenticated(it, false) }
                                        .onFailure { error = it.message ?: genericGoogleError }
                                } else {
                                    error = unexpectedCredentialError
                                }
                            } catch (e: GetCredentialCancellationException) {
                                // User dismissed the account picker — no error to show.
                            } catch (e: NoCredentialException) {
                                error = noAccountError
                            } catch (e: GetCredentialException) {
                                error = e.message ?: couldNotSignInError
                            } finally {
                                loading = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled = !loading
                ) { Text(t("auth.google")) }
            }
        }
    }
}
