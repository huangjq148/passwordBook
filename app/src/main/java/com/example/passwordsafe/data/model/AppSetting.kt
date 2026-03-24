package com.example.passwordsafe.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 应用设置数据模型
 * 用于存储应用的各种配置项
 */
@Entity(tableName = "app_settings")
data class AppSetting(
    @PrimaryKey
    val key: String,
    
    /** 设置值（加密存储敏感数据） */
    val value: String
)

/**
 * 设置项键名常量
 */
object SettingKeys {
    const val IS_FIRST_TIME = "is_first_time"
    const val BIOMETRIC_ENABLED = "biometric_enabled"
    const val PASSWORD_ENABLED = "password_enabled"
    const val APP_PASSWORD = "app_password"
    const val AUTO_LOCK_TIMEOUT = "auto_lock_timeout"
    const val THEME_MODE = "theme_mode"
    const val LAST_BACKUP_TIME = "last_backup_time"
}
