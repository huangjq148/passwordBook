package com.example.passwordsafe.ui.accounts

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
 * 账号列表 UI 状态
 */
data class AccountsUiState(
    val isLoading: Boolean = false,
    val accounts: List<Account> = emptyList(),
    val filteredAccounts: List<Account> = emptyList(),
    val categories: List<String> = emptyList(),
    val searchQuery: String = "",
    val selectedCategory: String? = null,
    val errorMessage: String? = null
)

/**
 * 账号列表 ViewModel
 * 处理账号列表加载、搜索、筛选等功能
 */
@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountsUiState())
    val uiState: StateFlow<AccountsUiState> = _uiState.asStateFlow()

    init {
        loadAccounts()
        loadCategories()
    }

    /**
     * 加载所有账号
     */
    fun loadAccounts() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            accountRepository.getAllAccounts().collect { accounts ->
                _uiState.update { state ->
                    val filtered = applyFilter(accounts, state.searchQuery, state.selectedCategory)
                    state.copy(
                        isLoading = false,
                        accounts = accounts,
                        filteredAccounts = filtered,
                        errorMessage = null
                    )
                }
            }
        }
    }

    /**
     * 加载所有分类
     */
    private fun loadCategories() {
        viewModelScope.launch {
            accountRepository.getAllCategories().collect { categories ->
                _uiState.update { it.copy(categories = categories.filter { c -> c.isNotEmpty() }) }
            }
        }
    }

    /**
     * 搜索账号
     */
    fun search(query: String) {
        _uiState.update { state ->
            val filtered = applyFilter(state.accounts, query, state.selectedCategory)
            state.copy(
                searchQuery = query,
                filteredAccounts = filtered
            )
        }
    }

    /**
     * 清除搜索
     */
    fun clearSearch() {
        _uiState.update { state ->
            val filtered = applyFilter(state.accounts, "", state.selectedCategory)
            state.copy(
                searchQuery = "",
                filteredAccounts = filtered
            )
        }
    }

    /**
     * 按分类筛选
     */
    fun filterByCategory(category: String?) {
        _uiState.update { state ->
            val filtered = applyFilter(state.accounts, state.searchQuery, category)
            state.copy(
                selectedCategory = category,
                filteredAccounts = filtered
            )
        }
    }

    /**
     * 应用筛选条件（智能搜索 + 分类筛选）
     * 智能排序：完全匹配 > 开头匹配 > 包含匹配
     * 结合使用频率权重
     */
    private fun applyFilter(
        accounts: List<Account>,
        query: String,
        category: String?
    ): List<Account> {
        var result = accounts
        
        // 智能搜索筛选
        if (query.isNotEmpty()) {
            val lowerQuery = query.lowercase()
            result = result
                .filter { account ->
                    // 模糊匹配：网站名、账号、URL、备注
                    account.website.lowercase().contains(lowerQuery) ||
                    account.account.lowercase().contains(lowerQuery) ||
                    account.websiteUrl.lowercase().contains(lowerQuery) ||
                    account.notes.lowercase().contains(lowerQuery) ||
                    account.category.lowercase().contains(lowerQuery)
                }
                .map { account ->
                    // 计算相关度分数
                    val score = calculateRelevanceScore(account, lowerQuery)
                    Pair(account, score)
                }
                .sortedByDescending { it.second }
                .map { it.first }
        }
        
        // 分类筛选
        if (!category.isNullOrEmpty()) {
            result = result.filter { it.category == category }
        }
        
        return result
    }

    /**
     * 计算账号相关度分数
     * 完全匹配(300分) > 开头匹配(200分) > 包含匹配(100分)
     * 加上使用频率权重
     */
    private fun calculateRelevanceScore(account: Account, query: String): Int {
        var score = 0
        val websiteLower = account.website.lowercase()
        val accountLower = account.account.lowercase()
        val urlLower = account.websiteUrl.lowercase()
        val categoryLower = account.category.lowercase()
        
        // 网站名匹配（权重最高）
        score += when {
            websiteLower == query -> 300
            websiteLower.startsWith(query) -> 200
            websiteLower.contains(query) -> 100
            else -> 0
        }
        
        // 账号匹配
        score += when {
            accountLower == query -> 250
            accountLower.startsWith(query) -> 150
            accountLower.contains(query) -> 80
            else -> 0
        }
        
        // URL 匹配
        score += when {
            urlLower.startsWith(query) -> 120
            urlLower.contains(query) -> 50
            else -> 0
        }
        
        // 分类匹配
        score += when {
            categoryLower == query -> 60
            categoryLower.startsWith(query) -> 40
            categoryLower.contains(query) -> 20
            else -> 0
        }
        
        // 加上使用频率权重（每个使用次数加1分）
        score += account.useCount
        
        return score
    }

    /**
     * 增加账号使用次数
     */
    fun incrementUseCount(accountId: Long) {
        viewModelScope.launch {
            accountRepository.incrementUseCount(accountId)
        }
    }

    /**
     * 清除错误信息
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
