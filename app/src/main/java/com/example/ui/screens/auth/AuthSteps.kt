package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.components.*
import com.example.ui.theme.*

/**
 * The staged sign-in path ("intent first", 1.0.45): Welcome → Providers → Email → Password, Forgot → Reset sent, and the
 * three registration steps. Every step is one short screen in [AuthStepScaffold] (no scrolling on a 360 dp phone at the
 * text cap); LoginAuthScreen owns the state and the actions. Adding a provider (Apple) = one [AuthProvider] entry.
 */

/** Sign-in providers in display order. Apple is ready but off until its Firebase provider is configured. */
internal enum class AuthProvider { GOOGLE, APPLE, EMAIL }

internal object AuthProviders {
    const val APPLE_ENABLED = false
    fun available(): List<AuthProvider> = AuthProvider.entries.filter { it != AuthProvider.APPLE || APPLE_ENABLED }
}

/** One step: back arrow, title, optional subtitle, error line, content, and the actions pinned at the bottom. */
@Composable
internal fun AuthStepScaffold(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    isBusy: Boolean = false,
    error: String? = null,
    showLogo: Boolean = true,
    /** Replaces the small logo above the title (Welcome's large brand lockup). */
    header: (@Composable ColumnScope.() -> Unit)? = null,
    actions: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
    AuthBackdrop()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = Spacing.lg)
            .testTag("login_auth_screen")
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("auth_back")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            }
            Spacer(Modifier.weight(1f))
            if (isBusy) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        }
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            if (header != null) {
                header()
            } else if (showLogo) {
                Image(
                    painter = painterResource(R.drawable.prohost_login_lockup),
                    contentDescription = stringResource(R.string.prohost_login),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.height(56.dp).align(Alignment.Start)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() }
                )
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (error != null) ProHostAlertBanner(message = error, severity = ProHostAlertSeverity.ERROR)
            content()
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.md, bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = actions
        )
    }
    }
}

/**
 * Behind every sign-in / sign-up step (1.0.46): a pre-blurred practice room (`auth_background.webp`, made by
 * scripts/marketing/blur_background.py) under a surface scrim — light at the top so the room shows, near-opaque where the
 * fields and buttons sit — so text keeps its contrast in light and dark themes.
 */
@Composable
private fun AuthBackdrop() {
    val surface = MaterialTheme.colorScheme.surface
    Image(
        painter = painterResource(R.drawable.auth_background),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize()
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to surface.copy(alpha = 0.55f),
                    0.35f to surface.copy(alpha = 0.78f),
                    1f to surface.copy(alpha = 0.92f)
                )
            )
    )
}

/** Small "By continuing you agree to the Terms of Use and Privacy Policy." line with tappable documents. */
@Composable
internal fun AuthTermsNote(onTerms: () -> Unit, onPrivacy: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.auth_terms_note),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Row(horizontalArrangement = Arrangement.Center) {
            TextButton(onClick = onTerms) { Text(stringResource(R.string.terms_of_use), style = MaterialTheme.typography.labelMedium) }
            TextButton(onClick = onPrivacy) { Text(stringResource(R.string.privacy_policy), style = MaterialTheme.typography.labelMedium) }
        }
    }
}

// ---- 1. Welcome --------------------------------------------------------------------------------

@Composable
internal fun WelcomeStep(onCreate: () -> Unit, onSignIn: () -> Unit, onTerms: () -> Unit, onPrivacy: () -> Unit) {
    AuthStepScaffold(
        title = stringResource(R.string.auth_welcome_title),
        subtitle = stringResource(R.string.auth_welcome_subtitle),
        header = {
            Image(
                painter = painterResource(R.drawable.prohost_login_lockup),
                contentDescription = stringResource(R.string.prohost_login),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .padding(top = Spacing.xxl, bottom = Spacing.lg)
                    .size(width = 220.dp, height = 144.dp)
                    .align(Alignment.CenterHorizontally)
            )
        },
        actions = {
            ProPrimaryButton(
                text = stringResource(R.string.create_an_account),
                onClick = onCreate,
                icon = Icons.Default.PersonAdd,
                modifier = Modifier.fillMaxWidth().testTag("auth_create_button")
            )
            CustomButton(
                text = stringResource(R.string.auth_have_account),
                onClick = onSignIn,
                variant = CustomButtonVariant.OUTLINED,
                modifier = Modifier.fillMaxWidth().testTag("auth_signin_button")
            )
            AuthTermsNote(onTerms, onPrivacy)
        }
    ) {}
}

// ---- 2. Providers ------------------------------------------------------------------------------

@Composable
internal fun ProvidersStep(
    creating: Boolean,
    isBusy: Boolean,
    error: String?,
    onBack: () -> Unit,
    onProvider: (AuthProvider) -> Unit,
    onSwitchIntent: () -> Unit
) {
    AuthStepScaffold(
        title = stringResource(if (creating) R.string.auth_providers_create_title else R.string.auth_providers_signin_title),
        onBack = onBack,
        isBusy = isBusy,
        error = error,
        actions = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(if (creating) R.string.have_an_account else R.string.new_to_prohost),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onSwitchIntent, modifier = Modifier.testTag("switch_auth_mode_button")) {
                    Text(stringResource(if (creating) R.string.sign_in else R.string.create_an_account), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    ) {
        Spacer(Modifier.height(Spacing.sm))
        AuthProviders.available().forEach { provider ->
            when (provider) {
                AuthProvider.GOOGLE -> GoogleSignInButton(onClick = { onProvider(provider) }, enabled = !isBusy)
                AuthProvider.APPLE -> CustomButton(
                    text = stringResource(R.string.continue_with_apple),
                    onClick = { onProvider(provider) },
                    variant = CustomButtonVariant.OUTLINED,
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth()
                )
                AuthProvider.EMAIL -> CustomButton(
                    text = stringResource(R.string.continue_with_email),
                    onClick = { onProvider(provider) },
                    variant = CustomButtonVariant.OUTLINED,
                    icon = Icons.Default.Email,
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth().testTag("auth_email_provider")
                )
            }
        }
    }
}

// ---- 3. Email ----------------------------------------------------------------------------------

@Composable
internal fun EmailStep(
    email: String,
    onEmailChange: (String) -> Unit,
    error: String?,
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    val valid = com.example.ui.viewmodel.AuthViewModel.isPlausibleEmail(email.trim().lowercase())
    AuthStepScaffold(
        title = stringResource(R.string.auth_email_title),
        onBack = onBack,
        error = error,
        actions = {
            ProPrimaryButton(
                text = stringResource(R.string.continue_),
                onClick = onContinue,
                enabled = valid,
                modifier = Modifier.fillMaxWidth().testTag("auth_email_continue")
            )
        }
    ) {
        InputField(
            value = email,
            onValueChange = { onEmailChange(it.trim()) },
            label = stringResource(R.string.email_address),
            placeholder = stringResource(R.string.you_example_com),
            leadingIcon = Icons.Default.Email,
            modifier = Modifier.fillMaxWidth().emailAutofill { onEmailChange(it.trim()) }.testTag("auth_email_input"),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { if (valid) onContinue() }),
            singleLine = true
        )
    }
}

// ---- 4. Password -------------------------------------------------------------------------------

@Composable
internal fun PasswordStep(
    creating: Boolean,
    email: String,
    password: String,
    onPasswordChange: (String) -> Unit,
    showPassword: Boolean,
    onToggleShow: () -> Unit,
    isBusy: Boolean,
    error: String?,
    onBack: () -> Unit,
    onChangeEmail: () -> Unit,
    onForgot: () -> Unit,
    onSubmit: () -> Unit
) {
    AuthStepScaffold(
        title = stringResource(if (creating) R.string.auth_password_create_title else R.string.auth_password_signin_title),
        onBack = onBack,
        isBusy = isBusy,
        error = error,
        actions = {
            ProPrimaryButton(
                text = stringResource(
                    when {
                        isBusy && creating -> R.string.creating_account
                        isBusy -> R.string.signing_in
                        creating -> R.string.action_create_account
                        else -> R.string.sign_in
                    }
                ),
                onClick = onSubmit,
                enabled = !isBusy && password.isNotEmpty(),
                isLoading = isBusy,
                modifier = Modifier.fillMaxWidth().testTag("submit_email_button")
            )
        }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Email, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.sm))
            Text(email, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f, fill = false))
            TextButton(onClick = onChangeEmail) { Text(stringResource(R.string.change)) }
        }
        InputField(
            value = password,
            onValueChange = onPasswordChange,
            label = stringResource(if (creating) R.string.new_password else R.string.password),
            leadingIcon = Icons.Default.Key,
            trailingIcon = {
                IconButton(onClick = onToggleShow) {
                    Icon(
                        if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = stringResource(if (showPassword) R.string.hide_password else R.string.show_password)
                    )
                }
            },
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth().passwordAutofill(newPassword = creating) { onPasswordChange(it) }.testTag("auth_password_input"),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (!isBusy && password.isNotEmpty()) onSubmit() }),
            singleLine = true
        )
        if (creating) {
            PasswordChecklist(password)
        } else {
            TextButton(onClick = onForgot, modifier = Modifier.align(Alignment.End).testTag("forgot_password_button")) {
                Text(stringResource(R.string.forgot_password), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Live checklist under the new-password field, mirroring the server's password policy. */
@Composable
internal fun PasswordChecklist(password: String) {
    val check = com.example.util.PasswordRules.check(password)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.fillMaxWidth().testTag("password_rules")) {
        PasswordRule(check.longEnough, stringResource(R.string.password_rule_length))
        PasswordRule(check.hasLowercase, stringResource(R.string.password_rule_lowercase))
        PasswordRule(check.hasNumber, stringResource(R.string.password_rule_number))
    }
}

@Composable
private fun PasswordRule(met: Boolean, label: String) {
    val color = if (met) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Icon(if (met) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = color, modifier = Modifier.semantics { stateDescription = if (met) "✓" else "" })
    }
}

// ---- 5. Forgot password / reset sent -----------------------------------------------------------

@Composable
internal fun ForgotStep(email: String, onEmailChange: (String) -> Unit, isBusy: Boolean, error: String?, onBack: () -> Unit, onSend: () -> Unit) {
    val valid = com.example.ui.viewmodel.AuthViewModel.isPlausibleEmail(email.trim().lowercase())
    AuthStepScaffold(
        title = stringResource(R.string.reset_title),
        subtitle = stringResource(R.string.reset_subtitle),
        onBack = onBack,
        isBusy = isBusy,
        error = error,
        actions = {
            ProPrimaryButton(
                text = stringResource(if (isBusy) R.string.sending else R.string.send_reset_link),
                onClick = onSend,
                enabled = !isBusy && valid,
                isLoading = isBusy,
                modifier = Modifier.fillMaxWidth().testTag("send_reset_button")
            )
        }
    ) {
        InputField(
            value = email,
            onValueChange = { onEmailChange(it.trim()) },
            label = stringResource(R.string.email_address),
            placeholder = stringResource(R.string.you_example_com),
            leadingIcon = Icons.Default.Email,
            modifier = Modifier.fillMaxWidth().testTag("reset_email_input"),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (!isBusy && valid) onSend() }),
            singleLine = true
        )
    }
}

@Composable
internal fun ResetSentStep(email: String, countdownSeconds: Int, isBusy: Boolean, onResend: () -> Unit, onBackToSignIn: () -> Unit) {
    AuthStepScaffold(
        title = stringResource(R.string.check_your_email),
        subtitle = stringResource(R.string.reset_sent_body_x, email),
        isBusy = isBusy,
        actions = {
            ProPrimaryButton(
                text = stringResource(R.string.back_to_sign_in),
                onClick = onBackToSignIn,
                modifier = Modifier.fillMaxWidth()
            )
            if (countdownSeconds > 0) {
                Text(
                    stringResource(R.string.resend_in_xs, countdownSeconds),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                TextButton(onClick = onResend, enabled = !isBusy) { Text(stringResource(R.string.resend_reset_link), fontWeight = FontWeight.SemiBold) }
            }
        }
    ) {
        ProHostAlertBanner(message = stringResource(R.string.reset_sent_hint), icon = Icons.Default.MarkEmailRead)
    }
}

// ---- 6. Registration (three short steps) -------------------------------------------------------

/** "Step 1 of 3" progress for the registration steps. */
@Composable
internal fun RegistrationProgress(step: Int, total: Int = 3) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        repeat(total) { i ->
            Surface(
                color = if (i < step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.extraSmall,
                modifier = Modifier.height(4.dp).width(28.dp)
            ) {}
        }
        Spacer(Modifier.width(Spacing.sm))
        Text(stringResource(R.string.wizard_step_x_of_x, step, total), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun RegistrationStepScaffold(
    step: Int,
    title: String,
    subtitle: String? = null,
    isBusy: Boolean,
    error: String?,
    primaryText: String,
    primaryEnabled: Boolean,
    onPrimary: () -> Unit,
    onBack: () -> Unit,
    secondary: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    AuthStepScaffold(
        title = title,
        subtitle = subtitle,
        onBack = onBack,
        isBusy = isBusy,
        error = error,
        showLogo = false,
        actions = {
            ProPrimaryButton(
                text = primaryText,
                onClick = onPrimary,
                enabled = primaryEnabled && !isBusy,
                isLoading = isBusy,
                modifier = Modifier.fillMaxWidth().testTag("registration_primary_$step")
            )
            secondary?.invoke()
        }
    ) {
        RegistrationProgress(step)
        content()
    }
}

/** Small icon + label pair used for the "signed in as" line on the first registration step. */
@Composable
internal fun AuthInfoLine(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(Spacing.sm))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
