package com.example.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.Governorate
import com.example.data.model.UserRole
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.ProSpaceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginAuthScreen(
    viewModel: ProSpaceViewModel,
    onLoginSuccess: () -> Unit
) {
    val context = LocalContext.current
    var isSignUpMode by remember { mutableStateOf(false) }

    // Sign In form fields
    var loginEmail by remember { mutableStateOf("geo.elnajjar@gmail.com") }
    var loginPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var selectedLoginRole by remember { mutableStateOf(UserRole.ADMIN) }

    // Sign Up / Member Registration fields
    var regFullName by remember { mutableStateOf("") }
    var regEmail by remember { mutableStateOf("") }
    var regPhone by remember { mutableStateOf("+961 ") }
    var regPassword by remember { mutableStateOf("") }
    var showRegPassword by remember { mutableStateOf(false) }
    var regRole by remember { mutableStateOf(UserRole.PROFESSIONAL) }
    var regSpecialty by remember { mutableStateOf("") }
    var regSyndicateNumber by remember { mutableStateOf("") }
    var regAffiliation by remember { mutableStateOf("") }
    var regGovernorate by remember { mutableStateOf(Governorate.BEIRUT) }
    var showGovDropdown by remember { mutableStateOf(false) }

    // State from ViewModel
    val isAuthenticating by viewModel.isAuthenticating.collectAsState()
    val authErrorMessage by viewModel.authErrorMessage.collectAsState()
    val authSuccessMessage by viewModel.authSuccessMessage.collectAsState()

    var localErrorMessage by remember { mutableStateOf<String?>(null) }
    var showGoogleChooser by remember { mutableStateOf(false) }
    var showForgotPasswordDialog by remember { mutableStateOf(false) }
    var forgotPasswordEmail by remember { mutableStateOf("") }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PremiumBackgroundGradient)
            .verticalScroll(scrollState)
            .padding(20.dp)
            .testTag("login_auth_screen"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // ProSpace Brand Header
        Surface(
            color = OxfordBlue,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .size(76.dp)
                .border(1.5.dp, CarnationOrange, RoundedCornerShape(20.dp)),
            shadowElevation = 6.dp
        ) {
            Image(
                painter = painterResource(id = com.example.R.drawable.ic_prospace_logo_brand),
                contentDescription = "ProHost Logo",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp))
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "ProHost",
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = "Professional Workspace & Office Rental Exchange",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Mode Selector: Sign In vs Register New Member
        TabRow(
            selectedTabIndex = if (isSignUpMode) 1 else 0,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
        ) {
            Tab(
                selected = !isSignUpMode,
                onClick = {
                    isSignUpMode = false
                    localErrorMessage = null
                    viewModel.clearAuthMessages()
                },
                text = { Text("Sign In", fontWeight = FontWeight.Bold) },
                icon = { Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Tab(
                selected = isSignUpMode,
                onClick = {
                    isSignUpMode = true
                    localErrorMessage = null
                    viewModel.clearAuthMessages()
                },
                text = { Text("Register Member", fontWeight = FontWeight.Bold) },
                icon = { Icon(Icons.Default.AppRegistration, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Status or Error Banners
        val displayError = localErrorMessage ?: authErrorMessage
        if (displayError != null) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.ErrorOutline,
                        contentDescription = "Error",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = displayError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        if (authSuccessMessage != null) {
            Surface(
                color = StatusSuccessContainer,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Success",
                        tint = LebaneseCedarGreen,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = authSuccessMessage ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusOnSuccessContainer,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        if (!isSignUpMode) {
            // ==================== SIGN IN FLOW ====================
            Text(
                text = "SELECT ACCESS CLEARANCE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Start)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 1. Super Admin Card (geo.elnajjar@gmail.com)
            RoleSelectionCard(
                roleTitle = "Super Admin (Security & Governance)",
                subtitle = "geo.elnajjar@gmail.com • Central Node Governance",
                icon = Icons.Default.AdminPanelSettings,
                badge = "SUPER ADMIN",
                isSelected = selectedLoginRole == UserRole.ADMIN,
                accentColor = SandstoneDark,
                onClick = {
                    selectedLoginRole = UserRole.ADMIN
                    loginEmail = "geo.elnajjar@gmail.com"
                    viewModel.clearAuthMessages()
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 2. Space Owner Card
            RoleSelectionCard(
                roleTitle = "Workspace Host / Space Owner",
                subtitle = "Manage listings, view telemetry stats, accept/reject requests",
                icon = Icons.Default.HomeWork,
                badge = "OWNER",
                isSelected = selectedLoginRole == UserRole.SPACE_OWNER,
                accentColor = CarnationOrange,
                onClick = {
                    selectedLoginRole = UserRole.SPACE_OWNER
                    viewModel.clearAuthMessages()
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 3. Professional Card
            RoleSelectionCard(
                roleTitle = "Professional / Renter",
                subtitle = "Explore spaces, active rentals & history, WhatsApp connect",
                icon = Icons.Default.Work,
                badge = "PROFESSIONAL",
                isSelected = selectedLoginRole == UserRole.PROFESSIONAL,
                accentColor = OxfordBlue,
                onClick = {
                    selectedLoginRole = UserRole.PROFESSIONAL
                    viewModel.clearAuthMessages()
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Sign In Credentials Form
            ModernCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(20.dp),
                elevation = 3.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Sign In with Firebase Auth",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Clearance: ${selectedLoginRole.name} • End-to-end encrypted",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isAuthenticating) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                InputField(
                    value = loginEmail,
                    onValueChange = {
                        loginEmail = it
                        localErrorMessage = null
                    },
                    label = "Email Address",
                    leadingIcon = Icons.Default.Email,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_email_input"),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                InputField(
                    value = loginPassword,
                    onValueChange = {
                        loginPassword = it
                        localErrorMessage = null
                    },
                    label = "Password",
                    leadingIcon = Icons.Default.Lock,
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showPassword) "Hide password" else "Show password",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_password_input"),
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = {
                            forgotPasswordEmail = loginEmail
                            showForgotPasswordDialog = true
                        }
                    ) {
                        Text(
                            text = "Forgot password?",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Primary Firebase Login Button
                CustomButton(
                    text = if (isAuthenticating) "Authenticating..." else if (selectedLoginRole == UserRole.ADMIN) "Sign In as Super Admin" else "Sign In with Firebase Auth",
                    onClick = {
                        if (loginEmail.isBlank()) {
                            localErrorMessage = "Please enter an email address"
                            return@CustomButton
                        }
                        if (loginPassword.isBlank()) {
                            localErrorMessage = "Please enter your password"
                            return@CustomButton
                        }
                        viewModel.signInWithEmailAndPassword(
                            context = context,
                            email = loginEmail,
                            password = loginPassword,
                            desiredRole = selectedLoginRole,
                            onSuccess = onLoginSuccess
                        )
                    },
                    enabled = !isAuthenticating,
                    variant = CustomButtonVariant.PRIMARY,
                    icon = if (selectedLoginRole == UserRole.ADMIN) Icons.Default.Shield else Icons.AutoMirrored.Filled.Login,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("submit_login_button")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Google Sign In Button via Credential Manager
                CustomButton(
                    text = "Sign In with Google Credential Manager",
                    onClick = {
                        viewModel.signInWithGoogleCredentialManager(
                            activityContext = context,
                            onSuccess = onLoginSuccess
                        )
                    },
                    enabled = !isAuthenticating,
                    variant = CustomButtonVariant.OUTLINED,
                    icon = Icons.Default.AccountCircle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("google_sign_in_button")
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Quick Identity Selector Fallback
                OutlinedButton(
                    onClick = { showGoogleChooser = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        Icons.Default.SwitchAccount,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Quick Verified Profile Selector",
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            // ==================== MEMBER REGISTRATION FLOW ====================
            ModernCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(20.dp),
                elevation = 3.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Register with Firebase Auth",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Join Lebanon's verified commercial workspace exchange network",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isAuthenticating) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Membership Type Selection
                Text(
                    text = "MEMBERSHIP TYPE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = regRole == UserRole.PROFESSIONAL,
                        onClick = { regRole = UserRole.PROFESSIONAL },
                        label = { Text("Professional / Renter") },
                        leadingIcon = { Icon(Icons.Default.Work, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = regRole == UserRole.SPACE_OWNER,
                        onClick = { regRole = UserRole.SPACE_OWNER },
                        label = { Text("Workspace Host") },
                        leadingIcon = { Icon(Icons.Default.HomeWork, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                InputField(
                    value = regFullName,
                    onValueChange = {
                        regFullName = it
                        localErrorMessage = null
                    },
                    label = "Full Name",
                    placeholder = "e.g. Dr. Maya Haddad, Eng. Jad Saliba",
                    leadingIcon = Icons.Default.Person,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                InputField(
                    value = regEmail,
                    onValueChange = {
                        regEmail = it
                        localErrorMessage = null
                    },
                    label = "Work Email",
                    placeholder = "specialist@organization.lb",
                    leadingIcon = Icons.Default.Email,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                InputField(
                    value = regPhone,
                    onValueChange = {
                        regPhone = it
                        localErrorMessage = null
                    },
                    label = "Lebanese Phone (WhatsApp)",
                    placeholder = "+961 70 123456",
                    leadingIcon = Icons.Default.Phone,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                if (regRole == UserRole.SPACE_OWNER) {
                    Spacer(modifier = Modifier.height(10.dp))

                    InputField(
                        value = regSpecialty,
                        onValueChange = {
                            regSpecialty = it
                            localErrorMessage = null
                        },
                        label = "Profession / Medical / Architectural Specialty",
                        placeholder = "e.g. Clinical Dermatologist, Senior Architect",
                        leadingIcon = Icons.Default.Badge,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    InputField(
                        value = regSyndicateNumber,
                        onValueChange = {
                            regSyndicateNumber = it
                            localErrorMessage = null
                        },
                        label = "Syndicate / License / Commercial ID",
                        placeholder = "e.g. LOP-8492, OEA-7731, CR-9921",
                        leadingIcon = Icons.Default.VerifiedUser,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    InputField(
                        value = regAffiliation,
                        onValueChange = {
                            regAffiliation = it
                            localErrorMessage = null
                        },
                        label = "Firm / Studio / Medical Center Name",
                        placeholder = "e.g. Beirut Creative Hub, Horizon Clinic",
                        leadingIcon = Icons.Default.Business,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Governorate Selector
                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showGovDropdown = true },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.LocationOn, contentDescription = null, tint = OxfordBlue, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("Primary Operating Governorate", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(regGovernorate.displayName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                    }

                    DropdownMenu(
                        expanded = showGovDropdown,
                        onDismissRequest = { showGovDropdown = false }
                    ) {
                        Governorate.values().forEach { gov ->
                            DropdownMenuItem(
                                text = { Text(gov.displayName) },
                                onClick = {
                                    regGovernorate = gov
                                    showGovDropdown = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                InputField(
                    value = regPassword,
                    onValueChange = {
                        regPassword = it
                        localErrorMessage = null
                    },
                    label = "Create Secure Password (6+ characters)",
                    leadingIcon = Icons.Default.Lock,
                    trailingIcon = {
                        IconButton(onClick = { showRegPassword = !showRegPassword }) {
                            Icon(
                                imageVector = if (showRegPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showRegPassword) "Hide password" else "Show password",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (showRegPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(20.dp))

                CustomButton(
                    text = if (isAuthenticating) "Registering Account..." else "Create Account & Sign In",
                    onClick = {
                        if (regFullName.isBlank()) {
                            localErrorMessage = "Please enter your full name"
                            return@CustomButton
                        }
                        if (regEmail.isBlank() || !regEmail.contains("@")) {
                            localErrorMessage = "Please enter a valid work email"
                            return@CustomButton
                        }
                        if (regPassword.length < 6) {
                            localErrorMessage = "Password must be at least 6 characters"
                            return@CustomButton
                        }
                        if (regPhone.isBlank() || regPhone.length < 8) {
                            localErrorMessage = "Please enter a valid Lebanese contact phone"
                            return@CustomButton
                        }
                        if (regSpecialty.isBlank()) {
                            localErrorMessage = "Please enter your specialty or profession"
                            return@CustomButton
                        }

                        viewModel.registerMemberWithFirebase(
                            context = context,
                            fullName = regFullName,
                            email = regEmail,
                            password = regPassword,
                            phone = regPhone,
                            role = regRole,
                            specialty = regSpecialty,
                            syndicateNumber = if (regSyndicateNumber.isBlank()) "LB-REG-" + (1000..9999).random() else regSyndicateNumber,
                            affiliation = if (regAffiliation.isBlank()) "Independent Practitioner" else regAffiliation,
                            governorate = regGovernorate,
                            onSuccess = onLoginSuccess
                        )
                    },
                    enabled = !isAuthenticating,
                    variant = CustomButtonVariant.PRIMARY,
                    icon = Icons.Default.CheckCircle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("submit_registration_button")
                )

                Spacer(modifier = Modifier.height(10.dp))

                CustomButton(
                    text = "Sign Up with Google Credential Manager",
                    onClick = {
                        viewModel.signInWithGoogleCredentialManager(
                            activityContext = context,
                            onSuccess = onLoginSuccess
                        )
                    },
                    enabled = !isAuthenticating,
                    variant = CustomButtonVariant.OUTLINED,
                    icon = Icons.Default.AccountCircle,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Security Notice Box
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = LebaneseCedarGreen,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "ProSpace enforces Firebase Auth token validation, Credential Manager security, and Lebanese Commercial Code compliance.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Dialog for Google account selection (quick testing / fallback)
        if (showGoogleChooser) {
            GoogleChooserDialog(
                onAccountSelected = { email, role ->
                    showGoogleChooser = false
                    viewModel.login(email = email, desiredRole = role)
                    onLoginSuccess()
                },
                onDismiss = {
                    showGoogleChooser = false
                }
            )
        }

        // Dialog for Password Reset
        if (showForgotPasswordDialog) {
            ForgotPasswordDialog(
                initialEmail = forgotPasswordEmail,
                onSendReset = { email ->
                    showForgotPasswordDialog = false
                    viewModel.sendPasswordReset(context, email)
                },
                onDismiss = {
                    showForgotPasswordDialog = false
                }
            )
        }
    }
}

@Composable
fun ForgotPasswordDialog(
    initialEmail: String,
    onSendReset: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var email by remember { mutableStateOf(initialEmail) }
    var error by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.LockReset,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Reset Password",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Enter your work email address to receive Firebase password reset instructions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                InputField(
                    value = email,
                    onValueChange = {
                        email = it
                        error = null
                    },
                    label = "Work Email",
                    leadingIcon = Icons.Default.Email,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true
                )

                if (error != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = error ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (email.isBlank() || !email.contains("@")) {
                                error = "Please enter a valid email address"
                            } else {
                                onSendReset(email)
                            }
                        }
                    ) {
                        Text("Send Reset Link")
                    }
                }
            }
        }
    }
}

@Composable
fun RoleSelectionCard(
    roleTitle: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    badge: String,
    isSelected: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .then(
                if (isSelected) Modifier.border(2.dp, accentColor, RoundedCornerShape(14.dp))
                else Modifier
            ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = if (isSelected) accentColor else MaterialTheme.colorScheme.surfaceVariant,
                shape = CircleShape,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = roleTitle,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = if (isSelected) accentColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = badge,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            RadioButton(
                selected = isSelected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(selectedColor = accentColor)
            )
        }
    }
}

@Composable
fun GoogleChooserDialog(
    onAccountSelected: (String, UserRole) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = "Google SSO",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Google Identity",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = "Choose an account",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "to continue to ProHost",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 20.dp),
                    textAlign = TextAlign.Center
                )

                // Primary Verified Google Account
                GoogleAccountRow(
                    name = "George El Najjar",
                    email = "geo.elnajjar@gmail.com",
                    roleText = "Super Admin Node",
                    role = UserRole.ADMIN,
                    avatarLetter = "G",
                    onClick = { onAccountSelected("geo.elnajjar@gmail.com", UserRole.ADMIN) }
                )

                Spacer(modifier = Modifier.height(8.dp))

                GoogleAccountRow(
                    name = "George El Najjar",
                    email = "geo.elnajjar@gmail.com",
                    roleText = "Space Host Account",
                    role = UserRole.SPACE_OWNER,
                    avatarLetter = "G",
                    onClick = { onAccountSelected("geo.elnajjar@gmail.com", UserRole.SPACE_OWNER) }
                )

                Spacer(modifier = Modifier.height(8.dp))

                GoogleAccountRow(
                    name = "George El Najjar",
                    email = "geo.elnajjar@gmail.com",
                    roleText = "Licensed Practitioner Account",
                    role = UserRole.PROFESSIONAL,
                    avatarLetter = "G",
                    onClick = { onAccountSelected("geo.elnajjar@gmail.com", UserRole.PROFESSIONAL) }
                )

                Spacer(modifier = Modifier.height(20.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Cancel", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun GoogleAccountRow(
    name: String,
    email: String,
    roleText: String,
    role: UserRole,
    avatarLetter: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = when(role) {
                    UserRole.ADMIN -> AmberWarning
                    UserRole.SPACE_OWNER -> CarnationOrange
                    UserRole.PROFESSIONAL -> OxfordBlue
                },
                shape = CircleShape,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = avatarLetter,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = email,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Role: $roleText",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = when(role) {
                        UserRole.ADMIN -> AmberWarning
                        UserRole.SPACE_OWNER -> CarnationOrange
                        UserRole.PROFESSIONAL -> OxfordBlue
                    }
                )
            }
        }
    }
}
