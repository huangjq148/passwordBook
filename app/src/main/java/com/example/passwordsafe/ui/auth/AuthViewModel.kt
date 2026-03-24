package com.example.passwordsafe.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.passwordsafe.data.repository.SettingRepository
import com.example.passwordsafe.util.PasswordStrength
import com.example.passwordsafe.util.PasswordStrengthChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 验证状态
 */
data class AuthUiState(
    val isLoading: Boolean = false,
    val isConfigReady: Boolean = false,
    val isFirstTime: Boolean = true,
    val isAuthenticated: Boolean = false,
    val setupCompleted: Boolean = false,
    val errorMessage: String? = null,
    val showBiometric: Boolean = false,
    val biometricAvailable: Boolean = false,
    val passwordStrength: PasswordStrength = PasswordStrength.WEAK
)

/**
 * 验证模块 ViewModel
 * 处理密码验证、首次设置密码、生物识别等功能
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val settingRepository: SettingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        checkFirstTime()
    }

    /**
     * 检查是否首次使用
     */
    fun checkFirstTime() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            settingRepository.initSettings()
            
            val hasPassword = settingRepository.hasAppPassword()
            val biometricEnabled = settingRepository.biometricEnabled.value
            if (hasPassword && settingRepository.isFirstTime.value) {
                settingRepository.setIsFirstTime(false)
            }
            
            _uiState.update { 
                it.copy(
                    isLoading = false,
                    isConfigReady = true,
                    isFirstTime = !hasPassword,
                    showBiometric = biometricEnabled && hasPassword
                )
            }
        }
    }

    /**
     * 验证密码
     */
    fun verifyPassword(password: String) {
        if (password.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "密码不能为空") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            
            val isValid = settingRepository.verifyAppPassword(password)
            
            if (isValid) {
                _uiState.update { 
                    it.copy(
                        isLoading = false, 
                        isAuthenticated = true,
                        errorMessage = null
                    )
                }
            } else {
                _uiState.update { 
                    it.copy(
                        isLoading = false, 
                        isAuthenticated = false,
                        errorMessage = "密码错误"
                    )
                }
            }
        }
    }

    /**
     * 设置密码
     */
    fun setupPassword(
        password: String,
        confirmPassword: String,
        authenticateImmediately: Boolean
    ): Boolean {
        // 验证密码
        if (password.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "密码不能为空") }
            return false
        }

        if (password.length < 8) {
            _uiState.update { it.copy(errorMessage = "密码长度至少8位") }
            return false
        }

        if (!password.matches(Regex("^[A-Za-z0-9]+$"))) {
            _uiState.update { it.copy(errorMessage = "密码只能包含英文字母和数字") }
            return false
        }

        if (password != confirmPassword) {
            _uiState.update { it.copy(errorMessage = "两次密码不一致") }
            return false
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            
            try {
                settingRepository.setAppPassword(password)
                settingRepository.setIsFirstTime(false)
                settingRepository.setPasswordEnabled(true)
                
                _uiState.update { 
                    it.copy(
                        isLoading = false, 
                        isConfigReady = true,
                        isAuthenticated = authenticateImmediately,
                        isFirstTime = false,
                        setupCompleted = true,
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false, 
                        isConfigReady = true,
                        errorMessage = "设置密码失败: ${e.message}"
                    )
                }
            }
        }
        
        return true
    }

    /**
     * 检查密码强度
     */
    fun checkPasswordStrength(password: String) {
        val strength = PasswordStrengthChecker.check(password)
        _uiState.update { 
            it.copy(
                passwordStrength = strength
            )
        }
    }

    /**
     * 设置生物识别可用状态
     */
    fun setBiometricAvailable(available: Boolean) {
        _uiState.update { it.copy(biometricAvailable = available) }
    }
    
    /**
     * 启用生物识别
     */
    fun enableBiometric() {
        viewModelScope.launch {
            settingRepository.setBiometricEnabled(true)
            _uiState.update { it.copy(showBiometric = true) }
        }
    }

    fun completeAuthenticationAfterSetup() {
        _uiState.update {
            it.copy(
                isAuthenticated = true,
                setupCompleted = false,
                errorMessage = null
            )
        }
    }

    fun onBiometricAuthenticated() {
        _uiState.update {
            it.copy(
                isAuthenticated = true,
                errorMessage = null
            )
        }
    }

    /**
     * 清除错误信息
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /**
     * 重置验证状态（用于退出登录）
     */
    fun resetAuthState() {
        _uiState.update { 
            AuthUiState(isFirstTime = false, isAuthenticated = false)
        }
    }
}
