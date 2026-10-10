package com.quranreminder.app.ui.screens

import android.net.Uri
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockReset
import androidx.compose.material.icons.outlined.MarkEmailRead
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.quranreminder.app.auth.SessionManager
import com.quranreminder.app.auth.localizedAuthMessage
import com.quranreminder.app.ui.theme.LocalAppStrings
import com.quranreminder.app.ui.theme.NurIconButton
import com.quranreminder.app.ui.theme.NurPrimaryButton
import com.quranreminder.app.ui.theme.NurScreenHeader
import com.quranreminder.app.ui.theme.NurSheet
import com.quranreminder.app.ui.theme.NurStarMedallion
import com.quranreminder.app.ui.theme.t
import kotlinx.coroutines.launch

private enum class ResetStep { RequestEmail, Confirm }

private val SheetOverlap = 24.dp

@Composable
fun ForgotPasswordScreen(
    session: SessionManager,
    initialToken: String = "",
    onDone: () -> Unit
) {
    val strings = LocalAppStrings.current
    var step by remember { mutableStateOf(if (initialToken.isBlank()) ResetStep.RequestEmail else ResetStep.Confirm) }
    var email by remember { mutableStateOf("") }
    var emailSent by remember { mutableStateOf(false) }
    var token by remember { mutableStateOf(initialToken) }
    var newPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val emptyEmailError = t("forgotPassword.error.emptyEmail")
    val sentInfo = t("forgotPassword.sent")
    val resetFailedRequestError = t("forgotPassword.error.failed")
    val requestReset: () -> Unit = request@{
        if (email.isBlank()) {
            error = emptyEmailError
            return@request
        }
        focusManager.clearFocus()
        loading = true
        error = null
        scope.launch {
            session.requestPasswordReset(email.trim())
                .onSuccess {
                    info = sentInfo
                    emailSent = true
                }
                .onFailure { error = it.localizedAuthMessage(strings) ?: resetFailedRequestError }
            loading = false
        }
    }

    val invalidError = t("forgotPassword.error.invalid")
    val resetFailedError = t("forgotPassword.error.failed")
    val confirmReset: () -> Unit = confirm@{
        if (token.isBlank() || newPassword.length < 8) {
            error = invalidError
            return@confirm
        }
        focusManager.clearFocus()
        loading = true
        error = null
        scope.launch {
            session.confirmPasswordReset(token.trim(), newPassword)
                .onSuccess { onDone() }
                .onFailure { error = it.localizedAuthMessage(strings) ?: resetFailedError }
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
        NurScreenHeader(
            title = t("forgotPassword.title"),
            eyebrow = if (step == ResetStep.RequestEmail) t("forgotPassword.step1") else t("forgotPassword.step2"),
            leading = {
                NurIconButton(Icons.Default.ArrowBack, contentDescription = t("common.back"), onClick = onDone)
            },
            bottomPadding = SheetOverlap + 28.dp
        )

        NurSheet(modifier = Modifier.offset(y = -SheetOverlap)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 24.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                StepProgress(current = if (step == ResetStep.RequestEmail) 0 else 1)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    NurStarMedallion(
                        icon = when {
                            step == ResetStep.Confirm -> Icons.Outlined.LockReset
                            emailSent -> Icons.Outlined.MarkEmailRead
                            else -> Icons.Outlined.Email
                        },
                        size = 52.dp
                    )
                    Text(
                        text = if (step == ResetStep.RequestEmail) t("forgotPassword.requestPrompt") else t("forgotPassword.confirmPrompt"),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f)
                    )
                }

                when (step) {
                    ResetStep.RequestEmail -> {
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it.filterNot(Char::isWhitespace) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(t("auth.email")) },
                            leadingIcon = { Icon(Icons.Outlined.Email, contentDescription = null) },
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { if (!loading) requestReset() })
                        )
                        info?.let { AuthNotice(text = it, isError = false) }
                        error?.let { AuthNotice(text = it, isError = true) }
                        NurPrimaryButton(
                            text = t("forgotPassword.send"),
                            onClick = requestReset,
                            loading = loading
                        )
                        if (emailSent) {
                            TextButton(
                                onClick = { step = ResetStep.Confirm; error = null },
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .heightIn(min = 48.dp)
                            ) {
                                Text(t("forgotPassword.haveToken"), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }

                    ResetStep.Confirm -> {
                        OutlinedTextField(
                            value = token,
                            onValueChange = { pasted ->
                                token = if (pasted.contains("token=")) {
                                    runCatching { Uri.parse(pasted).getQueryParameter("token") }.getOrNull() ?: pasted
                                } else pasted
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(t("forgotPassword.token")) },
                            leadingIcon = { Icon(Icons.Outlined.Key, contentDescription = null) },
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
                        )
                        OutlinedTextField(
                            value = newPassword,
                            onValueChange = { newPassword = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(t("forgotPassword.newPassword")) },
                            leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                        contentDescription = if (passwordVisible) t("auth.password.hide") else t("auth.password.show")
                                    )
                                }
                            },
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { if (!loading) confirmReset() })
                        )
                        error?.let { AuthNotice(text = it, isError = true) }
                        NurPrimaryButton(
                            text = t("forgotPassword.update"),
                            onClick = confirmReset,
                            loading = loading
                        )
                    }
                }

                TextButton(
                    onClick = onDone,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .heightIn(min = 48.dp)
                ) {
                    Text(t("forgotPassword.backToSignIn"), style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

/** Two-segment progress for the request → confirm flow. */
@Composable
private fun StepProgress(current: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(2) { i ->
            Box(
                Modifier
                    .weight(1f)
                    .height(4.dp)
                    .background(
                        if (i <= current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        CircleShape
                    )
            )
        }
    }
}
