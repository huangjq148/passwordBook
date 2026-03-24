package com.example.passwordsafe.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.passwordsafe.data.model.Account
import com.example.passwordsafe.data.repository.AccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 首页 UI 状态
 */
data class HomeUiState(
    val isLoading: Boolean = false,
    val recentAccounts: List<Account> = emptyList(),
    val searchResults: List<Account> = emptyList(),
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val accountCount: Int = 0,
    val errorMessage: String? = null
)

/**
 * 首页 ViewModel
 * 处理最近账号加载、搜索等功能
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadRecentAccounts()
        loadAccountCount()
    }

    /**
     * 加载最近使用的账号
     */
    fun loadRecentAccounts() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            accountRepository.getRecentAccounts().collect { accounts ->
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        recentAccounts = accounts.take(5), // 最多显示5个
                        errorMessage = null
                    )
                }
            }
        }
    }

    /**
     * 加载账号总数
     */
    private fun loadAccountCount() {
        viewModelScope.launch {
            accountRepository.getAccountCount().collect { count ->
                _uiState.update { it.copy(accountCount = count) }
            }
        }
    }

    /**
     * 智能搜索账号
     * 支持模糊匹配 + 相关度排序 + 使用频率排序
     */
    fun search(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        
        if (query.isEmpty()) {
            _uiState.update { it.copy(isSearching = false, searchResults = emptyList()) }
            return
        }
        
        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true) }
            
            // 使用智能搜索：相关度 + 使用频率排序
            accountRepository.smartSearch(query).collect { results ->
                _uiState.update { 
                    it.copy(
                        searchResults = results,
                        isSearching = false
                    )
                }
            }
        }
    }

    /**
     * 清除搜索
     */
    fun clearSearch() {
        _uiState.update { 
            it.copy(
                searchQuery = "",
                searchResults = emptyList(),
                isSearching = false
            )
        }
    }

    /**
     * 账号点击处理
     */
    fun onAccountClicked(accountId: Long): Long {
        // 增加使用次数
        viewModelScope.launch {
            accountRepository.incrementUseCount(accountId)
        }
        return accountId
    }

    /**
     * 清除错误信息
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
