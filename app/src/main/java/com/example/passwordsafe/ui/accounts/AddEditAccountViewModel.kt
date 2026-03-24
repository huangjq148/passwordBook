package com.example.passwordsafe.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.passwordsafe.R
import com.example.passwordsafe.data.model.Account
import com.example.passwordsafe.data.repository.AccountRepository
import com.example.passwordsafe.util.PasswordGenerator
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
 * 添加/编辑账号 UI 状态
 */
data class AddEditAccountUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isEditing: Boolean = false,
    val accountId: Long = -1L,
    val website: String = "",
    val websiteUrl: String = "",
    val account: String = "",
    val password: String = "",
    val notes: String = "",
    val category: String = "",
    val passwordStrength: PasswordStrength = PasswordStrength.WEAK,
    val isPasswordVisible: Boolean = false,
    val isSaved: Boolean = false,
    val websiteError: String? = null,
    val accountError: String? = null,
    val passwordError: String? = null,
    val errorMessage: String? = null
)

/**
 * 密码生成器设置
 */
data class PasswordGeneratorSettings(
    val length: Int = 16,
    val includeUppercase: Boolean = true,
    val includeLowercase: Boolean = true,
    val includeDigits: Boolean = true,
    val includeSpecial: Boolean = true
) {
    fun toGeneratorParams(): PasswordGeneratorParams {
        return PasswordGeneratorParams(
            length = length,
            includeUppercase = includeUppercase,
            includeLowercase = includeLowercase,
            includeDigits = includeDigits,
            includeSpecial = includeSpecial
        )
    }
}

data class PasswordGeneratorParams(
    val length: Int,
    val includeUppercase: Boolean,
    val includeLowercase: Boolean,
    val includeDigits: Boolean,
    val includeSpecial: Boolean
)

/**
 * 添加/编辑账号 ViewModel
 * 处理账号添加、编辑、密码生成等功能
 */
@HiltViewModel
class AddEditAccountViewModel @Inject constructor(
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddEditAccountUiState())
    val uiState: StateFlow<AddEditAccountUiState> = _uiState.asStateFlow()

    private val _generatorSettings = MutableStateFlow(PasswordGeneratorSettings())
    val generatorSettings: StateFlow<PasswordGeneratorSettings> = _generatorSettings.asStateFlow()

    private val _generatedPassword = MutableStateFlow("")
    val generatedPassword: StateFlow<String> = _generatedPassword.asStateFlow()

    private val _generatedPasswordStrength = MutableStateFlow(PasswordStrength.WEAK)
    val generatedPasswordStrength: StateFlow<PasswordStrength> = _generatedPasswordStrength.asStateFlow()

    // 分类列表
    val categories = listOf(
        "" to R.string.category_other,
        "social" to R.string.category_social,
        "finance" to R.string.category_finance,
        "shopping" to R.string.category_shopping,
        "work" to R.string.category_work,
        "entertainment" to R.string.category_entertainment
    )

    /**
     * 加载账号（编辑模式）
     */
    fun loadAccount(accountId: Long) {
        if (accountId <= 0) {
            _uiState.update { it.copy(isEditing = false) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, isEditing = true, accountId = accountId) }
            
            val account = accountRepository.getAccountById(accountId)
            if (account != null) {
                val strength = PasswordStrengthChecker.check(account.password)
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        website = account.website,
                        websiteUrl = account.websiteUrl,
                        account = account.account,
                        password = account.password,
                        notes = account.notes,
                        category = account.category,
                        passwordStrength = strength,
                        errorMessage = null
                    )
                }
            } else {
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        errorMessage = "账号不存在"
                    )
                }
            }
        }
    }

    /**
     * 更新网站名称
     */
    fun updateWebsite(website: String) {
        _uiState.update { 
            it.copy(
                website = website,
                websiteError = null
            )
        }
    }

    /**
     * 更新网站URL
     */
    fun updateWebsiteUrl(url: String) {
        _uiState.update { it.copy(websiteUrl = url) }
    }

    /**
     * 更新账号
     */
    fun updateAccount(account: String) {
        _uiState.update { 
            it.copy(
                account = account,
                accountError = null
            )
        }
    }

    /**
     * 更新密码
     */
    fun updatePassword(password: String) {
        val strength = PasswordStrengthChecker.check(password)
        _uiState.update { 
            it.copy(
                password = password,
                passwordStrength = strength,
                passwordError = null
            )
        }
    }

    /**
     * 更新备注
     */
    fun updateNotes(notes: String) {
        _uiState.update { it.copy(notes = notes) }
    }

    /**
     * 更新分类
     */
    fun updateCategory(category: String) {
        _uiState.update { it.copy(category = category) }
    }

    /**
     * 切换密码可见性
     */
    fun togglePasswordVisibility() {
        _uiState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    /**
     * 验证表单
     */
    private fun validateForm(): Boolean {
        var isValid = true
        val state = _uiState.value

        if (state.website.isBlank()) {
            _uiState.update { it.copy(websiteError = "网站名称不能为空") }
            isValid = false
        }

        if (state.account.isBlank()) {
            _uiState.update { it.copy(accountError = "账号不能为空") }
            isValid = false
        }

        if (state.password.isBlank()) {
            _uiState.update { it.copy(passwordError = "密码不能为空") }
            isValid = false
        }

        return isValid
    }

    /**
     * 保存账号
     */
    fun saveAccount() {
        if (!validateForm()) return

        val state = _uiState.value
        
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            
            try {
                val account = Account(
                    id = if (state.isEditing) state.accountId else 0,
                    website = state.website,
                    websiteUrl = state.websiteUrl,
                    account = state.account,
                    password = state.password,
                    notes = state.notes,
                    category = state.category,
                    createdAt = if (state.isEditing) {
                        // 保留原创建时间
                        accountRepository.getAccountById(state.accountId)?.createdAt 
                            ?: System.currentTimeMillis()
                    } else {
                        System.currentTimeMillis()
                    },
                    updatedAt = System.currentTimeMillis()
                )

                if (state.isEditing) {
                    accountRepository.updateAccount(account)
                } else {
                    accountRepository.addAccount(account)
                }

                _uiState.update { 
                    it.copy(
                        isSaving = false,
                        isSaved = true
                    )
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isSaving = false,
                        errorMessage = "保存失败: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * 生成密码
     */
    fun generatePassword(): String {
        val settings = _generatorSettings.value
        val password = PasswordGenerator.generate(
            length = settings.length,
            includeUppercase = settings.includeUppercase,
            includeLowercase = settings.includeLowercase,
            includeDigits = settings.includeDigits,
            includeSpecial = settings.includeSpecial
        )
        _generatedPassword.value = password
        _generatedPasswordStrength.value = PasswordStrengthChecker.check(password)
        return password
    }

    /**
     * 更新密码生成器设置
     */
    fun updateGeneratorSettings(settings: PasswordGeneratorSettings) {
        _generatorSettings.value = settings
        // 立即生成新密码
        generatePassword()
    }

    /**
     * 使用生成的密码
     */
    fun useGeneratedPassword(password: String) {
        updatePassword(password)
    }

    /**
     * 清除错误信息
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
