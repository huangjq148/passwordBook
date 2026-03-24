package com.example.passwordsafe.data.repository

import com.example.passwordsafe.data.crypto.CryptoManager
import com.example.passwordsafe.data.local.database.AccountDao
import com.example.passwordsafe.data.model.Account
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 账号仓库
 * 封装 AccountDao 和 CryptoManager，提供加密/解密功能
 */
@Singleton
class AccountRepository @Inject constructor(
    private val accountDao: AccountDao,
    private val cryptoManager: CryptoManager
) {
    /**
     * 获取所有账号（密码已解密）
     */
    fun getAllAccounts(): Flow<List<Account>> = accountDao.getAllAccounts()

    /**
     * 获取最近使用的账号
     */
    fun getRecentAccounts(): Flow<List<Account>> = accountDao.getRecentAccounts()

    /**
     * 根据ID获取账号（密码解密）
     */
    suspend fun getAccountById(id: Long): Account? {
        val account = accountDao.getAccountById(id) ?: return null
        return decryptAccountPassword(account)
    }

    /**
     * 搜索账号
     */
    fun searchAccounts(query: String): Flow<List<Account>> = accountDao.searchAccounts(query)

    /**
     * 智能搜索账号（支持相关度 + 使用频率排序）
     * 完全匹配 > 开头匹配 > 包含匹配
     * 结合使用频率权重
     */
    fun smartSearch(query: String): Flow<List<Account>> = accountDao.smartSearch(query)

    /**
     * 添加账号（密码加密）
     */
    suspend fun addAccount(account: Account): Long {
        val encryptedAccount = encryptAccountPassword(account)
        return accountDao.insertAccount(encryptedAccount)
    }

    /**
     * 更新账号（密码加密）
     */
    suspend fun updateAccount(account: Account) {
        val encryptedAccount = encryptAccountPassword(account)
        accountDao.updateAccount(encryptedAccount)
    }

    /**
     * 删除账号
     */
    suspend fun deleteAccount(account: Account) {
        accountDao.deleteAccount(account)
    }

    /**
     * 根据ID删除账号
     */
    suspend fun deleteAccountById(id: Long) {
        accountDao.deleteAccountById(id)
    }

    /**
     * 增加使用次数
     */
    suspend fun incrementUseCount(id: Long) {
        accountDao.incrementUseCount(id)
    }

    /**
     * 获取所有账号列表（用于导出，密码解密）
     */
    suspend fun getAllAccountsList(): List<Account> {
        return accountDao.getAllAccountsList().map { decryptAccountPassword(it) }
    }

    /**
     * 删除所有账号
     */
    suspend fun deleteAllAccounts() {
        accountDao.deleteAllAccounts()
    }

    /**
     * 获取账号总数
     */
    fun getAccountCount(): Flow<Int> = accountDao.getAccountCount()

    /**
     * 获取所有分类
     */
    fun getAllCategories(): Flow<List<String>> = accountDao.getAllCategories()

    /**
     * 根据分类获取账号
     */
    fun getAccountsByCategory(category: String): Flow<List<Account>> = 
        accountDao.getAccountsByCategory(category)

    /**
     * 加密账号密码
     */
    private fun encryptAccountPassword(account: Account): Account {
        return if (account.password.isNotEmpty()) {
            account.copy(password = cryptoManager.encrypt(account.password))
        } else {
            account
        }
    }

    /**
     * 解密账号密码
     */
    private fun decryptAccountPassword(account: Account): Account {
        return if (account.password.isNotEmpty()) {
            try {
                account.copy(password = cryptoManager.decrypt(account.password))
            } catch (e: Exception) {
                account // 如果解密失败，返回原始数据
            }
        } else {
            account
        }
    }

    /**
     * 获取解密后的密码
     */
    fun decryptPassword(encryptedPassword: String): String {
        return if (encryptedPassword.isNotEmpty()) {
            try {
                cryptoManager.decrypt(encryptedPassword)
            } catch (e: Exception) {
                encryptedPassword
            }
        } else {
            ""
        }
    }
}
