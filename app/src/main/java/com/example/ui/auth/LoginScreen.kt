package com.example.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.content.res.Configuration
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.NavySurface
import com.example.ui.theme.RedLive
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    onLoginSuccess: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var host by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (uiState is LoginUiState.Success) onLoginSuccess()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyDeep)
            .testTag("login_screen")
    ) {
        Image(
            painter = painterResource(id = R.drawable.stadium_hero_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            alpha = 0.22f
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            NavyDeep.copy(alpha = 0.85f),
                            NavyDark.copy(alpha = 0.95f),
                            NavyDeep
                        )
                    )
                )
        )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(if (landscape) 12.dp else 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = if (landscape) 980.dp else 480.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = NavySurface.copy(alpha = 0.92f),
                tonalElevation = 8.dp,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp, GoldPrimary.copy(alpha = 0.25f)
                )
            ) {
                if (landscape) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(0.85f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            BrandHeader(compact = true)
                            Spacer(Modifier.height(8.dp))
                            SupportQrSection(compact = true)
                        }
                        Column(
                            modifier = Modifier
                                .weight(1.15f)
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            LoginFields(
                                host = host,
                                onHostChange = {
                                    host = it
                                    if (uiState is LoginUiState.Error) viewModel.clearError()
                                },
                                username = username,
                                onUsernameChange = {
                                    username = it
                                    if (uiState is LoginUiState.Error) viewModel.clearError()
                                },
                                password = password,
                                onPasswordChange = {
                                    password = it
                                    if (uiState is LoginUiState.Error) viewModel.clearError()
                                },
                                passwordVisible = passwordVisible,
                                onTogglePasswordVisibility = {
                                    passwordVisible = !passwordVisible
                                },
                                uiState = uiState,
                                focusManager = focusManager,
                                onLogin = { viewModel.login(host, username, password) },
                                compact = true
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 36.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        BrandHeader(compact = false)
                        Spacer(Modifier.height(12.dp))
                        SupportQrSection(compact = false)
                        Spacer(Modifier.height(16.dp))
                        LoginFields(
                            host = host,
                            onHostChange = {
                                host = it
                                if (uiState is LoginUiState.Error) viewModel.clearError()
                            },
                            username = username,
                            onUsernameChange = {
                                username = it
                                if (uiState is LoginUiState.Error) viewModel.clearError()
                            },
                            password = password,
                            onPasswordChange = {
                                password = it
                                if (uiState is LoginUiState.Error) viewModel.clearError()
                            },
                            passwordVisible = passwordVisible,
                            onTogglePasswordVisibility = {
                                passwordVisible = !passwordVisible
                            },
                            uiState = uiState,
                            focusManager = focusManager,
                            onLogin = { viewModel.login(host, username, password) },
                            compact = false
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BrandHeader(compact: Boolean) {
    Box(
        modifier = Modifier
            .size(if (compact) 58.dp else 80.dp)
            .clip(CircleShape)
            .background(NavyDark)
            .border(2.dp, GoldPrimary, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.eagle_sports_icon),
            contentDescription = "شعار إيغل سبورتس",
            modifier = Modifier.size(if (compact) 50.dp else 70.dp),
            contentScale = ContentScale.Fit
        )
    }
    Spacer(Modifier.height(if (compact) 6.dp else 16.dp))
    Text(
        text = "Eagle Sports",
        style = (if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium).copy(
            fontWeight = FontWeight.ExtraBold,
            color = GoldPrimary,
            letterSpacing = 1.2.sp
        ),
        textAlign = TextAlign.Center
    )
    Text(
        text = "بث رياضي مباشر وتعليق صوتي خارجي",
        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
        textAlign = TextAlign.Center
    )
}

@Composable
private fun SupportQrSection(compact: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = NavyDark.copy(alpha = 0.8f),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, GoldPrimary.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(
                vertical = if (compact) 8.dp else 16.dp,
                horizontal = 10.dp
            ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "للاشتراك في IPTV Premium",
                color = GoldPrimary,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "تواصل مع الدعم عبر مسح QR",
                color = TextSecondary,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(if (compact) 6.dp else 12.dp))
            Image(
                painter = painterResource(id = R.drawable.eagle_support_qr),
                contentDescription = "رمز QR للتواصل مع الدعم والاشتراك في IPTV Premium",
                modifier = Modifier
                    .size(if (compact) 82.dp else 112.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .padding(3.dp),
                contentScale = ContentScale.Fit
            )
        }
    }
}

@Composable
private fun LoginFields(
    host: String,
    onHostChange: (String) -> Unit,
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    passwordVisible: Boolean,
    onTogglePasswordVisibility: () -> Unit,
    uiState: LoginUiState,
    focusManager: FocusManager,
    onLogin: () -> Unit,
    compact: Boolean
) {
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = GoldPrimary,
        unfocusedBorderColor = TextMuted.copy(alpha = 0.4f),
        focusedLabelColor = GoldPrimary,
        unfocusedLabelColor = TextSecondary,
        cursorColor = GoldPrimary,
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary
    )
    val fieldShape = RoundedCornerShape(12.dp)
    val fieldGap = if (compact) 6.dp else 16.dp

    OutlinedTextField(
        value = host,
        onValueChange = onHostChange,
        label = { Text("عنوان السيرفر") },
        placeholder = { Text("http://example.com:8080") },
        leadingIcon = { Icon(Icons.Default.Language, contentDescription = null, tint = GoldPrimary) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
        colors = fieldColors,
        shape = fieldShape,
        modifier = Modifier.fillMaxWidth().testTag("server_input")
    )
    Spacer(Modifier.height(fieldGap))
    OutlinedTextField(
        value = username,
        onValueChange = onUsernameChange,
        label = { Text("اسم المستخدم") },
        placeholder = { Text("أدخل اسم المستخدم لحسابك") },
        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = GoldPrimary) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
        colors = fieldColors,
        shape = fieldShape,
        modifier = Modifier.fillMaxWidth().testTag("username_input")
    )
    Spacer(Modifier.height(fieldGap))
    OutlinedTextField(
        value = password,
        onValueChange = onPasswordChange,
        label = { Text("كلمة المرور") },
        placeholder = { Text("أدخل كلمة المرور") },
        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = GoldPrimary) },
        trailingIcon = {
            IconButton(onClick = onTogglePasswordVisibility) {
                Icon(
                    imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = if (passwordVisible) "إخفاء كلمة المرور" else "إظهار كلمة المرور",
                    tint = TextSecondary
                )
            }
        },
        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(
            onDone = {
                focusManager.clearFocus()
                onLogin()
            }
        ),
        colors = fieldColors,
        shape = fieldShape,
        modifier = Modifier.fillMaxWidth().testTag("password_input")
    )

    if (uiState is LoginUiState.Error) {
        Spacer(Modifier.height(if (compact) 6.dp else 12.dp))
        Text(
            text = uiState.message,
            color = RedLive,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center
        )
    }

    Spacer(Modifier.height(if (compact) 10.dp else 24.dp))
    val isLoading = uiState is LoginUiState.Loading
    Button(
        onClick = {
            focusManager.clearFocus()
            onLogin()
        },
        enabled = !isLoading,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag("login_button"),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = GoldPrimary,
            contentColor = NavyDeep,
            disabledContainerColor = GoldPrimary.copy(alpha = 0.5f)
        )
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = NavyDeep,
                strokeWidth = 2.5.dp
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "جارٍ التحقق من البيانات...",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.SportsSoccer, contentDescription = null, tint = NavyDeep)
                Spacer(Modifier.width(8.dp))
                Text(
                    "تسجيل الدخول",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
    Spacer(Modifier.height(if (compact) 8.dp else 16.dp))
    Text(
        text = "إدارة الخادم والاشتراك تلقائيًا",
        style = MaterialTheme.typography.labelSmall.copy(color = TextMuted),
        textAlign = TextAlign.Center
    )
}
