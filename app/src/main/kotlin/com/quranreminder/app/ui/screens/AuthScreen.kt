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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
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
import com.quranreminder.app.auth.localizedAuthMessage
import com.quranreminder.app.ui.theme.LocalAppStrings
import com.quranreminder.app.ui.theme.NurBand
import com.quranreminder.app.ui.theme.NurLockup
import com.quranreminder.app.ui.theme.NurPrimaryButton
import com.quranreminder.app.ui.theme.NurSecondaryButton
import com.quranreminder.app.ui.theme.NurSheet
import com.quranreminder.app.ui.theme.nurColors
import com.quranreminder.app.ui.theme.t
import kotlinx.coroutines.launch

/** How far the form sheet rises over the band's lower edge. */
private val SheetOverlap = 28.dp

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
    var passwordVisible by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val bandHeight = (LocalConfiguration.current.screenHeightDp * 0.40f).dp

    val strings = LocalAppStrings.current
    val incompleteError = t("auth.error.incomplete")
    val failedError = t("auth.error.failed")
    val submit: () -> Unit = submit@{
        if (email.isBlank() || password.length < 8 || (isSignUp && name.isBlank())) {
            error = incompleteError
            return@submit
        }
        focusManager.clearFocus()
        loading = true
        error = null
        scope.launch {
            val result = if (isSignUp) session.signUp(name.trim(), email.trim(), password) else session.signIn(email.trim(), password)
            result.onSuccess { onAuthenticated(it, isSignUp) }
                .onFailure { error = it.localizedAuthMessage(strings) ?: failedError }
            loading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
            .verticalScroll(rememberScrollState())
    ) {
        NurBand(
            modifier = Modifier.heightIn(min = bandHeight),
            bottomPadding = SheetOverlap + 24.dp
        ) {
            Spacer(Modifier.weight(1f))
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                NurLockup(markSize = 64.dp)
                Spacer(Modifier.height(12.dp))
                Text(
                    text = t("auth.welcome"),
                    style = MaterialTheme.typography.bodyLarge,
                    color = nurColors.onBandMuted,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.weight(1f))
        }

        NurSheet(modifier = Modifier.offset(y = -SheetOverlap)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 28.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = if (isSignUp) t("auth.title.signUp") else t("auth.title.signIn"),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )

                if (isSignUp) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(t("auth.name")) },
                        leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
                    )
                }
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it.filterNot(Char::isWhitespace) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(t("auth.email")) },
                    leadingIcon = { Icon(Icons.Outlined.Email, contentDescription = null) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(t("auth.password")) },
                    supportingText = { Text(t("auth.password.hint")) },
                    leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                    trailingIcon = {
                        PasswordVisibilityToggle(passwordVisible) { passwordVisible = !passwordVisible }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (!loading) submit() })
                )

                if (!isSignUp) {
                    TextButton(
                        onClick = onForgotPassword,
                        modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp)
                    ) {
                        Text(t("auth.forgotPassword"), style = MaterialTheme.typography.labelLarge)
                    }
                }

                error?.let { AuthNotice(text = it, isError = true) }

                NurPrimaryButton(
                    text = if (isSignUp) t("auth.signUp") else t("auth.signIn"),
                    onClick = submit,
                    loading = loading
                )

                if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()) {
                    OrDivider()
                    val unexpectedCredentialError = t("auth.google.unexpectedCredential")
                    val noAccountError = t("auth.google.noAccount")
                    val couldNotSignInError = t("auth.google.failed")
                    val genericGoogleError = t("auth.google.error")
                    NurSecondaryButton(
                        text = t("auth.google"),
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
                                            .onFailure { error = it.localizedAuthMessage(strings) ?: genericGoogleError }
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
                        enabled = !loading
                    )
                }

                TextButton(
                    onClick = { isSignUp = !isSignUp; error = null },
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .heightIn(min = 48.dp)
                ) {
                    Text(
                        text = if (isSignUp) t("auth.toggle.toSignIn") else t("auth.toggle.toSignUp"),
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun PasswordVisibilityToggle(visible: Boolean, onToggle: () -> Unit) {
    IconButton(onClick = onToggle) {
        Icon(
            imageVector = if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
            contentDescription = if (visible) t("auth.password.hide") else t("auth.password.show")
        )
    }
}

@Composable
private fun OrDivider() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
        Text(
            text = t("auth.or").trim(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp)
        )
        Box(Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
    }
}

/**
 * A message block for the auth flow: errors on the error container, confirmations on
 * the tertiary (success-green) container. Icon plus text, so meaning never rests on color.
 * Announced politely to screen readers when it appears.
 */
@Composable
internal fun AuthNotice(text: String, isError: Boolean, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val container = if (isError) scheme.errorContainer else scheme.tertiaryContainer
    val content = if (isError) scheme.onErrorContainer else scheme.onTertiaryContainer
    val icon: ImageVector = if (isError) Icons.Outlined.ErrorOutline else Icons.Outlined.CheckCircle
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(container, MaterialTheme.shapes.small)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = content, modifier = Modifier.weight(1f))
    }
}
