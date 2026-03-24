package com.example.passwordsafe.data.repository

import com.example.passwordsafe.data.crypto.CryptoManager
import com.example.passwordsafe.data.local.database.SettingDao
import com.example.passwordsafe.data.model.AppSetting
import com.example.passwordsafe.data.model.SettingKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 设置仓库
 * 封装 SettingDao 和 CryptoManager，提供应用设置管理功能
 */
@Singleton
class SettingRepository @Inject constructor(
    private val settingDao: SettingDao,
    private val cryptoManager: CryptoManager
) {
    private val _isFirstTime = MutableStateFlow(true)
    val isFirstTime: StateFlow<Boolean> = _isFirstTime.asStateFlow()

    private val _biometricEnabled = MutableStateFlow(false)
    val biometricEnabled: StateFlow<Boolean> = _biometricEnabled.asStateFlow()

    private val _passwordEnabled = MutableStateFlow(false)
    val passwordEnabled: StateFlow<Boolean> = _passwordEnabled.asStateFlow()

    private val _autoLockTimeout = MutableStateFlow(5L) // 默认5分钟
    val autoLockTimeout: StateFlow<Long> = _autoLockTimeout.asStateFlow()

    /**
     * 初始化设置
     */
    suspend fun initSettings() {
        val isFirstTimeSetting = settingDao.getSettingValue(SettingKeys.IS_FIRST_TIME)
        _isFirstTime.value = isFirstTimeSetting?.toBoolean() ?: true

        val biometricSetting = settingDao.getSettingValue(SettingKeys.BIOMETRIC_ENABLED)
        _biometricEnabled.value = biometricSetting?.toBoolean() ?: false

        val passwordSetting = settingDao.getSettingValue(SettingKeys.PASSWORD_ENABLED)
        _passwordEnabled.value = passwordSetting?.toBoolean() ?: false

        val autoLockSetting = settingDao.getSettingValue(SettingKeys.AUTO_LOCK_TIMEOUT)
        _autoLockTimeout.value = autoLockSetting?.toLong() ?: 5L
    }

    /**
     * 获取设置值
     */
    suspend fun getSetting(key: String): String? {
        return settingDao.getSettingValue(key)
    }

    /**
     * 设置是否首次使用
     */
    suspend fun setIsFirstTime(isFirstTime: Boolean) {
        settingDao.setSetting(AppSetting(SettingKeys.IS_FIRST_TIME, isFirstTime.toString()))
        _isFirstTime.value = isFirstTime
    }

    /**
     * 设置是否启用生物识别
     */
    suspend fun setBiometricEnabled(enabled: Boolean) {
        settingDao.setSetting(AppSetting(SettingKeys.BIOMETRIC_ENABLED, enabled.toString()))
        _biometricEnabled.value = enabled
    }

    /**
     * 设置是否启用密码
     */
    suspend fun setPasswordEnabled(enabled: Boolean) {
        settingDao.setSetting(AppSetting(SettingKeys.PASSWORD_ENABLED, enabled.toString()))
        _passwordEnabled.value = enabled
    }

    /**
     * 设置应用密码（加密存储）
     */
    suspend fun setAppPassword(password: String) {
        val encryptedPassword = if (password.isNotEmpty()) {
            cryptoManager.encrypt(password)
        } else {
            ""
        }
        settingDao.setSetting(AppSetting(SettingKeys.APP_PASSWORD, encryptedPassword))
    }

    /**
     * 验证应用密码
     */
    suspend fun verifyAppPassword(password: String): Boolean {
        val encryptedPassword = settingDao.getSettingValue(SettingKeys.APP_PASSWORD) ?: return false
        if (encryptedPassword.isEmpty()) return false
        
        return try {
            val storedPassword = cryptoManager.decrypt(encryptedPassword)
            storedPassword == password
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 检查是否设置了应用密码
     */
    suspend fun hasAppPassword(): Boolean {
        val encryptedPassword = settingDao.getSettingValue(SettingKeys.APP_PASSWORD)
        return !encryptedPassword.isNullOrEmpty()
    }

    /**
     * 设置自动锁定超时时间（分钟）
     */
    suspend fun setAutoLockTimeout(timeoutMinutes: Long) {
        settingDao.setSetting(AppSetting(SettingKeys.AUTO_LOCK_TIMEOUT, timeoutMinutes.toString()))
        _autoLockTimeout.value = timeoutMinutes
    }

    /**
     * 设置主题模式
     */
    suspend fun setThemeMode(mode: String) {
        settingDao.setSetting(AppSetting(SettingKeys.THEME_MODE, mode))
    }

    /**
     * 获取主题模式
     */
    suspend fun getThemeMode(): String {
        return settingDao.getSettingValue(SettingKeys.THEME_MODE) ?: "system"
    }

    /**
     * 设置最后备份时间
     */
    suspend fun setLastBackupTime(timestamp: Long) {
        settingDao.setSetting(AppSetting(SettingKeys.LAST_BACKUP_TIME, timestamp.toString()))
    }

    /**
     * 获取最后备份时间
     */
    suspend fun getLastBackupTime(): Long {
        return settingDao.getSettingValue(SettingKeys.LAST_BACKUP_TIME)?.toLong() ?: 0L
    }

    /**
     * 删除所有设置（用于重置应用）
     */
    suspend fun deleteAllSettings() {
        settingDao.deleteAllSettings()
        _isFirstTime.value = true
        _biometricEnabled.value = false
        _passwordEnabled.value = false
        _autoLockTimeout.value = 5L
    }

    /**
     * 删除应用密码
     */
    suspend fun deleteAppPassword() {
        settingDao.deleteSetting(SettingKeys.APP_PASSWORD)
    }

    /**
     * 修改应用密码
     */
    suspend fun changeAppPassword(currentPassword: String, newPassword: String): Boolean {
        if (!verifyAppPassword(currentPassword)) {
            return false
        }
        setAppPassword(newPassword)
        return true
    }
}
