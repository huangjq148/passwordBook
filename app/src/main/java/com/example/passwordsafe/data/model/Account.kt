package com.example.passwordsafe.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 账号数据模型
 * 用于存储用户的账号密码信息
 */
@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /** 网站名称 */
    val website: String,
    
    /** 网站URL */
    val websiteUrl: String = "",
    
    /** 账号 */
    val account: String,
    
    /** 密码（加密存储） */
    val password: String,
    
    /** 备注 */
    val notes: String = "",
    
    /** 分类 */
    val category: String = "",
    
    /** 使用次数 */
    val useCount: Int = 0,
    
    /** 最后使用时间（时间戳） */
    val lastUsedAt: Long = 0L,
    
    /** 创建时间（时间戳） */
    val createdAt: Long = System.currentTimeMillis(),
    
    /** 更新时间（时间戳） */
    val updatedAt: Long = System.currentTimeMillis()
)
