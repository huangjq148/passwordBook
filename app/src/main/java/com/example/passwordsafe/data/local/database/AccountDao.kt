package com.example.passwordsafe.data.local.database

import androidx.room.*
import com.example.passwordsafe.data.model.Account
import kotlinx.coroutines.flow.Flow

/**
 * 账号数据访问对象
 */
@Dao
interface AccountDao {

    /**
     * 获取所有账号（按使用频率排序）
     */
    @Query("SELECT * FROM accounts ORDER BY useCount DESC, updatedAt DESC")
    fun getAllAccounts(): Flow<List<Account>>

    /**
     * 获取最近使用的账号（限制5个）
     */
    @Query("SELECT * FROM accounts ORDER BY lastUsedAt DESC LIMIT 5")
    fun getRecentAccounts(): Flow<List<Account>>

    /**
     * 根据ID获取账号
     */
    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getAccountById(id: Long): Account?

    /**
     * 搜索账号（按网站名称、账号、分类模糊搜索）
     * 结果按使用频率排序
     */
    @Query("""
        SELECT * FROM accounts 
        WHERE website LIKE '%' || :query || '%' 
        OR account LIKE '%' || :query || '%' 
        OR category LIKE '%' || :query || '%'
        OR websiteUrl LIKE '%' || :query || '%'
        ORDER BY useCount DESC
    """)
    fun searchAccounts(query: String): Flow<List<Account>>

    /**
     * 智能搜索账号（支持相关度排序）
     * 相关度计算：完全匹配(3分) > 开头匹配(2分) > 包含匹配(1分)
     * 最终排序：相关度分数 * 10 + 使用频率权重
     */
    @RewriteQueriesToDropUnusedColumns
    @Query("""
        SELECT *, 
            (CASE 
                WHEN website = :query OR account = :query THEN 300
                WHEN website LIKE :query || '%' OR account LIKE :query || '%' THEN 200
                WHEN website LIKE '%' || :query || '%' OR account LIKE '%' || :query || '%' THEN 100
                WHEN websiteUrl LIKE :query || '%' THEN 150
                WHEN websiteUrl LIKE '%' || :query || '%' THEN 50
                WHEN category LIKE :query || '%' THEN 80
                WHEN category LIKE '%' || :query || '%' THEN 40
                ELSE 0
            END + useCount) AS relevanceScore
        FROM accounts 
        WHERE website LIKE '%' || :query || '%' 
           OR account LIKE '%' || :query || '%' 
           OR category LIKE '%' || :query || '%'
           OR websiteUrl LIKE '%' || :query || '%'
        ORDER BY relevanceScore DESC
    """)
    fun smartSearch(query: String): Flow<List<Account>>

    /**
     * 插入账号
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: Account): Long

    /**
     * 更新账号
     */
    @Update
    suspend fun updateAccount(account: Account)

    /**
     * 删除账号
     */
    @Delete
    suspend fun deleteAccount(account: Account)

    /**
     * 根据ID删除账号
     */
    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteAccountById(id: Long)

    /**
     * 增加使用次数并更新最后使用时间
     */
    @Query("""
        UPDATE accounts 
        SET useCount = useCount + 1, 
            lastUsedAt = :timestamp,
            updatedAt = :timestamp
        WHERE id = :id
    """)
    suspend fun incrementUseCount(id: Long, timestamp: Long = System.currentTimeMillis())

    /**
     * 获取所有账号列表（用于导出）
     */
    @Query("SELECT * FROM accounts ORDER BY website ASC")
    suspend fun getAllAccountsList(): List<Account>

    /**
     * 删除所有账号（用于重置）
     */
    @Query("DELETE FROM accounts")
    suspend fun deleteAllAccounts()

    /**
     * 获取账号总数
     */
    @Query("SELECT COUNT(*) FROM accounts")
    fun getAccountCount(): Flow<Int>

    /**
     * 获取所有分类
     */
    @Query("SELECT DISTINCT category FROM accounts WHERE category != '' ORDER BY category ASC")
    fun getAllCategories(): Flow<List<String>>

    /**
     * 根据分类获取账号
     */
    @Query("SELECT * FROM accounts WHERE category = :category ORDER BY useCount DESC")
    fun getAccountsByCategory(category: String): Flow<List<Account>>
}
