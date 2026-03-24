package com.example.passwordsafe.data.local.database

import androidx.room.*
import com.example.passwordsafe.data.model.AppSetting

/**
 * 设置数据访问对象
 */
@Dao
interface SettingDao {

    /**
     * 获取设置项
     */
    @Query("SELECT * FROM app_settings WHERE `key` = :key")
    suspend fun getSetting(key: String): AppSetting?

    /**
     * 获取设置值
     */
    @Query("SELECT value FROM app_settings WHERE `key` = :key")
    suspend fun getSettingValue(key: String): String?

    /**
     * 保存设置（插入或更新）
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setSetting(setting: AppSetting)

    /**
     * 删除设置项
     */
    @Query("DELETE FROM app_settings WHERE `key` = :key")
    suspend fun deleteSetting(key: String)

    /**
     * 删除所有设置
     */
    @Query("DELETE FROM app_settings")
    suspend fun deleteAllSettings()

    /**
     * 获取所有设置
     */
    @Query("SELECT * FROM app_settings")
    suspend fun getAllSettings(): List<AppSetting>

    /**
     * 检查设置是否存在
     */
    @Query("SELECT EXISTS(SELECT 1 FROM app_settings WHERE `key` = :key)")
    suspend fun hasSetting(key: String): Boolean
}
