package com.example.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.SupabaseClientProvider
import com.example.data.auth.AccountSessionManager
import com.example.data.auth.AuthActionContext
import com.example.data.auth.AuthFeedbackBus
import com.example.data.auth.SafeAuthErrorMapper
import com.example.data.auth.SafeAuthLogger
import com.example.ui.theme.DangerRed
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.MidarBlue
import com.example.ui.theme.PrimaryIndigo
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ResetPasswordUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isSessionValid: Boolean = true,
    val isSuccess: Boolean = false
)

class ResetPasswordViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ResetPasswordUiState())
    val uiState: StateFlow<ResetPasswordUiState> = _uiState.asStateFlow()

    init {
        checkRecoverySession()
    }

    fun checkRecoverySession() {
        val auth = SupabaseClientProvider.client.auth
        val hasSession = auth.currentSessionOrNull() != null || auth.currentUserOrNull() != null
        if (!hasSession) {
            _uiState.value = _uiState.value.copy(
                isSessionValid = false,
                errorMessage = "رابط استعادة كلمة المرور غير صالح أو منتهي الصلاحية. يرجى طلب رابط جديد."
            )
        } else {
            _uiState.value = _uiState.value.copy(
                isSessionValid = true,
                errorMessage = null
            )
        }
    }

    fun saveNewPassword(
        newPasswordInput: String,
        confirmPasswordInput: String,
        onSuccess: () -> Unit
    ) {
        val trimmedPassword = newPasswordInput.trim()
        val trimmedConfirm = confirmPasswordInput.trim()

        val reqs = checkPasswordRequirements(trimmedPassword)
        if (!reqs.isValid) {
            _uiState.value = _uiState.value.copy(errorMessage = "يرجى استيفاء جميع شروط كلمة المرور الموضحة أدناه.")
            return
        }

        if (trimmedPassword != trimmedConfirm) {
            _uiState.value = _uiState.value.copy(errorMessage = "كلمتا المرور غير متطابقتين.")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                // Update password on Supabase for the recovered user
                SupabaseClientProvider.client.auth.updateUser {
                    password = trimmedPassword
                }

                // Clean session teardown so the user signs in with the new credentials
                try {
                    SupabaseClientProvider.client.auth.signOut()
                } catch (_: Exception) {}
                AccountSessionManager.endSessionKeepingLocalData()

                AuthFeedbackBus.emitSuccess("تم تغيير كلمة المرور بنجاح. يمكنك الآن تسجيل الدخول بكلمة المرور الجديدة.")
                _uiState.value = _uiState.value.copy(isLoading = false, isSuccess = true)
                onSuccess()
            } catch (e: Exception) {
                SafeAuthLogger.logAuthError("ResetPasswordViewModel", "saveNewPassword", e)
                val safeMsg = SafeAuthErrorMapper.getArabicErrorMessage(e, AuthActionContext.RESET_PASSWORD)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = safeMsg
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}

@Composable
fun ResetPasswordScreen(
    viewModel: ResetPasswordViewModel = viewModel(),
    onSuccess: () -> Unit,
    onBackToLogin: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isConfirmVisible by remember { mutableStateOf(false) }

    val passwordReqs = remember(password) { checkPasswordRequirements(password) }
    val isPasswordMatching = remember(password, confirmPassword) {
        confirmPassword.isNotEmpty() && password == confirmPassword
    }
    val isFormValid = passwordReqs.isValid && isPasswordMatching

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF07142E),
                        Color(0xFF091C3E),
                        Color(0xFF0C244F),
                        Color(0xFF07132B)
                    )
                )
            )
            .testTag("reset_password_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top back button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                IconButton(
                    onClick = onBackToLogin,
                    modifier = Modifier.testTag("reset_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "رجوع لتسجيل الدخول",
                        tint = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Screen Header
            Text(
                text = "تعيين كلمة مرور جديدة",
                style = TextStyle(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "أدخل كلمة المرور الجديدة لحسابك في تطبيق مدار للمدرس",
                style = TextStyle(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFF94A3B8)
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Error feedback message
            if (!uiState.errorMessage.isNullOrEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFEE2E2).copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp)
                        .testTag("reset_error_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = uiState.errorMessage ?: "",
                            color = Color(0xFFFCA5A5),
                            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                            textAlign = TextAlign.Right,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            if (!uiState.isSessionValid) {
                // If token or session is invalid/expired
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "رابط استعادة كلمة المرور غير صالح أو منتهي الصلاحية.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = onBackToLogin,
                            colors = ButtonDefaults.buttonColors(containerColor = MidarBlue),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("reset_expired_back_button")
                        ) {
                            Text("العودة لتسجيل الدخول وطلب رابط جديد", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                // Form Card
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF101E38)),
                    border = BorderStroke(1.dp, Color(0xFF1E3A8A).copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Field 1: New Password
                        Column {
                            Text(
                                text = "كلمة المرور الجديدة",
                                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = password,
                                onValueChange = {
                                    password = it
                                    viewModel.clearError()
                                },
                                placeholder = { Text("أدخل كلمة المرور الجديدة", color = Color(0xFF64748B)) },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Lock, contentDescription = null, tint = Color(0xFF94A3B8))
                                },
                                trailingIcon = {
                                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                        Icon(
                                            imageVector = if (isPasswordVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                                            contentDescription = null,
                                            tint = Color(0xFF94A3B8)
                                        )
                                    }
                                },
                                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Next
                                ),
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MidarBlue,
                                    unfocusedBorderColor = Color(0xFF334155),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedContainerColor = Color(0xFF0A152E),
                                    unfocusedContainerColor = Color(0xFF0A152E)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("reset_new_password_input")
                            )
                        }

                        // Field 2: Confirm Password
                        Column {
                            Text(
                                text = "تأكيد كلمة المرور",
                                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = confirmPassword,
                                onValueChange = {
                                    confirmPassword = it
                                    viewModel.clearError()
                                },
                                placeholder = { Text("أعد كتابة كلمة المرور للتأكيد", color = Color(0xFF64748B)) },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Lock, contentDescription = null, tint = Color(0xFF94A3B8))
                                },
                                trailingIcon = {
                                    IconButton(onClick = { isConfirmVisible = !isConfirmVisible }) {
                                        Icon(
                                            imageVector = if (isConfirmVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                                            contentDescription = null,
                                            tint = Color(0xFF94A3B8)
                                        )
                                    }
                                },
                                visualTransformation = if (isConfirmVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done
                                ),
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = if (confirmPassword.isNotEmpty() && !isPasswordMatching) DangerRed else MidarBlue,
                                    unfocusedBorderColor = if (confirmPassword.isNotEmpty() && !isPasswordMatching) DangerRed else Color(0xFF334155),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedContainerColor = Color(0xFF0A152E),
                                    unfocusedContainerColor = Color(0xFF0A152E)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("reset_confirm_password_input")
                            )
                            if (confirmPassword.isNotEmpty() && !isPasswordMatching) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "كلمتا المرور غير متطابقتين",
                                    color = DangerRed,
                                    style = TextStyle(fontSize = 11.sp)
                                )
                            }
                        }

                        // Password Requirements checklist
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF0A152E),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "شروط كلمة المرور:",
                                    style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                                )
                                RequirementRow("8 أحرف على الأقل", passwordReqs.hasMinLength)
                                RequirementRow("حرف كبير واحد على الأقل (A-Z)", passwordReqs.hasUppercase)
                                RequirementRow("حرف صغير واحد على الأقل (a-z)", passwordReqs.hasLowercase)
                                RequirementRow("رقم واحد على الأقل (0-9)", passwordReqs.hasDigit)
                                RequirementRow("رمز خاص واحد على الأقل (!@#$%)", passwordReqs.hasSpecialChar)
                            }
                        }

                        // Submit Button
                        Button(
                            onClick = {
                                viewModel.saveNewPassword(password, confirmPassword, onSuccess)
                            },
                            enabled = isFormValid && !uiState.isLoading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1E60FF),
                                disabledContainerColor = Color(0xFF1E3A8A).copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("reset_save_password_button")
                        ) {
                            if (uiState.isLoading) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("جاري الحفظ...", fontWeight = FontWeight.Bold)
                            } else {
                                Text("حفظ كلمة المرور الجديدة", fontWeight = FontWeight.Bold)
                            }
                        }

                        // Cancel Button
                        OutlinedButton(
                            onClick = onBackToLogin,
                            border = BorderStroke(1.dp, Color(0xFF334155)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("reset_cancel_button")
                        ) {
                            Text("إلغاء والعودة لتسجيل الدخول", color = Color(0xFF94A3B8))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RequirementRow(label: String, isMet: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = if (isMet) Icons.Default.Check else Icons.Default.Close,
            contentDescription = null,
            tint = if (isMet) EmeraldGreen else Color(0xFF64748B),
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = label,
            style = TextStyle(
                fontSize = 11.sp,
                color = if (isMet) Color(0xFFD1FAE5) else Color(0xFF94A3B8)
            )
        )
    }
}
