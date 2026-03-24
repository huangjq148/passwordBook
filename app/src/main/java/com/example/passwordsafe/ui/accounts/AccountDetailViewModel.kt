package com.example.passwordsafe.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.passwordsafe.data.model.Account
import com.example.passwordsafe.data.repository.AccountRepository
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
 * 账号详情 UI 状态
 */
data class AccountDetailUiState(
    val isLoading: Boolean = false,
    val account: Account? = null,
    val decryptedPassword: String = "",
    val isPasswordVisible: Boolean = false,
    val passwordStrength: PasswordStrength = PasswordStrength.WEAK,
    val isDeleting: Boolean = false,
    val isDeleted: Boolean = false,
    val errorMessage: String? = null
)

/**
 * 账号详情 ViewModel
 * 处理账号详情展示、删除、复制等功能
 */
@HiltViewModel
class AccountDetailViewModel @Inject constructor(
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountDetailUiState())
    val uiState: StateFlow<AccountDetailUiState> = _uiState.asStateFlow()

    /**
     * 加载账号详情
     */
    fun loadAccount(accountId: Long) {
        if (accountId <= 0) {
            _uiState.update { it.copy(errorMessage = "无效的账号ID") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            val account = accountRepository.getAccountById(accountId)
            if (account != null) {
                val strength = PasswordStrengthChecker.check(account.password)
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        account = account,
                        decryptedPassword = account.password,
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
     * 切换密码可见性
     */
    fun togglePasswordVisibility() {
        _uiState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    /**
     * 删除账号
     */
    fun deleteAccount() {
        val account = _uiState.value.account ?: return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true) }
            
            try {
                accountRepository.deleteAccount(account)
                _uiState.update { 
                    it.copy(
                        isDeleting = false,
                        isDeleted = true
                    )
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isDeleting = false,
                        errorMessage = "删除失败: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * 增加使用次数
     */
    fun incrementUseCount() {
        val account = _uiState.value.account ?: return
        viewModelScope.launch {
            val timestamp = System.currentTimeMillis()
            accountRepository.incrementUseCount(account.id)
            _uiState.update {
                it.copy(
                    account = account.copy(
                        useCount = account.useCount + 1,
                        lastUsedAt = timestamp
                    )
                )
            }
        }
    }

    /**
     * 清除错误信息
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
