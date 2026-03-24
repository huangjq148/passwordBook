# PasswordSafe 密码管理应用实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 构建一个完整的 Android 密码管理应用，支持生物识别/密码验证、账号管理、导入导出等功能。

**Architecture:** 单 Activity + 多 Fragment 架构，使用 MVVM 模式，Hilt 依赖注入，Room 数据库 + Android Keystore 加密。

**Tech Stack:** Kotlin, Android Jetpack (Navigation, Room, Lifecycle, ViewModel), Hilt, BiometricPrompt, Apache POI (Excel)

---

## Phase 1: 项目基础设施

### Task 1.1: 配置 Gradle 依赖

**Files:**
- Modify: `app/build.gradle`
- Modify: `build.gradle` (root)

- [ ] **Step 1: 修改 app/build.gradle**

添加插件：
```gradle
plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
    id 'kotlin-kapt'
    id 'dagger.hilt.android.plugin'
}
```

添加依赖：
```gradle
dependencies {
    // Room
    implementation 'androidx.room:room-runtime:2.4.3'
    implementation 'androidx.room:room-ktx:2.4.3'
    kapt 'androidx.room:room-compiler:2.4.3'

    // Hilt
    implementation 'com.google.dagger:hilt-android:2.44'
    kapt 'com.google.dagger:hilt-compiler:2.44'

    // Biometric
    implementation 'androidx.biometric:biometric:1.1.0'

    // Security Crypto
    implementation 'androidx.security:security-crypto:1.1.0-alpha06'

    // Apache POI for Excel
    implementation 'org.apache.poi:poi:5.2.3'
    implementation 'org.apache.poi:poi-ooxml:5.2.3'

    // 现有依赖保持不变
    implementation 'androidx.core:core-ktx:1.7.0'
    // ... 其他现有依赖
}
```

- [ ] **Step 2: 修改根目录 build.gradle**

在 plugins 块添加：
```gradle
id 'com.google.dagger.hilt.android' version '2.44' apply false
```

- [ ] **Step 3: 验证构建**

Run: `./gradlew clean build`

---

### Task 1.2: 创建 Application 类

**Files:**
- Create: `app/src/main/java/com/example/passwordsafe/PasswordSafeApplication.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: 创建 Application 类**

```kotlin
package com.example.passwordsafe

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PasswordSafeApplication : Application()
```

- [ ] **Step 2: 更新 AndroidManifest.xml**

添加 `android:name` 和权限：
```xml
<uses-permission android:name="android.permission.USE_BIOMETRIC" />

<application
    android:name=".PasswordSafeApplication"
    ... >
```

---

### Task 1.3: 创建 AppModule

**Files:**
- Create: `app/src/main/java/com/example/passwordsafe/di/AppModule.kt`

- [ ] **Step 1: 创建 Hilt 模块**

```kotlin
package com.example.passwordsafe.di

import android.content.Context
import androidx.room.Room
import com.example.passwordsafe.data.crypto.CryptoManager
import com.example.passwordsafe.data.local.database.AppDatabase
import com.example.passwordsafe.data.repository.AccountRepository
import com.example.passwordsafe.data.repository.SettingRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, "passwordsafe_db").build()
    }

    @Provides
    fun provideAccountDao(database: AppDatabase) = database.accountDao()

    @Provides
    fun provideSettingDao(database: AppDatabase) = database.settingDao()

    @Provides
    @Singleton
    fun provideCryptoManager(@ApplicationContext context: Context) = CryptoManager(context)

    @Provides
    @Singleton
    fun provideAccountRepository(accountDao: com.example.passwordsafe.data.local.database.AccountDao, cryptoManager: CryptoManager) = AccountRepository(accountDao, cryptoManager)

    @Provides
    @Singleton
    fun provideSettingRepository(settingDao: com.example.passwordsafe.data.local.database.SettingDao, cryptoManager: CryptoManager) = SettingRepository(settingDao, cryptoManager)
}
```

---

## Phase 2: 数据层

### Task 2.1: 创建数据模型

**Files:**
- Create: `app/src/main/java/com/example/passwordsafe/data/model/Account.kt`
- Create: `app/src/main/java/com/example/passwordsafe/data/model/AppSetting.kt`

- [ ] **Step 1: 创建 Account 实体**

```kotlin
package com.example.passwordsafe.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val website: String,
    val websiteUrl: String = "",
    val account: String,
    val password: String,
    val notes: String = "",
    val category: String = "",
    val useCount: Int = 0,
    val lastUsedAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
```

- [ ] **Step 2: 创建 AppSetting 实体**

```kotlin
package com.example.passwordsafe.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "settings")
data class AppSetting(@PrimaryKey val key: String, val value: String)
```

---

### Task 2.2: 创建 DAO

**Files:**
- Create: `app/src/main/java/com/example/passwordsafe/data/local/database/AccountDao.kt`
- Create: `app/src/main/java/com/example/passwordsafe/data/local/database/SettingDao.kt`
- Create: `app/src/main/java/com/example/passwordsafe/data/local/database/AppDatabase.kt`

- [ ] **Step 1: 创建 AccountDao**

```kotlin
package com.example.passwordsafe.data.local.database

import androidx.room.*
import com.example.passwordsafe.data.model.Account
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY useCount DESC, lastUsedAt DESC")
    fun getAllAccounts(): Flow<List<Account>>

    @Query("SELECT * FROM accounts ORDER BY lastUsedAt DESC LIMIT 5")
    fun getRecentAccounts(): Flow<List<Account>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getAccountById(id: Long): Account?

    @Query("SELECT * FROM accounts WHERE website LIKE '%' || :query || '%' OR account LIKE '%' || :query || '%' OR notes LIKE '%' || :query || '%' OR category LIKE '%' || :query || '%' ORDER BY useCount DESC, lastUsedAt DESC")
    fun searchAccounts(query: String): Flow<List<Account>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: Account): Long

    @Update
    suspend fun updateAccount(account: Account)

    @Delete
    suspend fun deleteAccount(account: Account)

    @Query("UPDATE accounts SET useCount = useCount + 1, lastUsedAt = :timestamp WHERE id = :id")
    suspend fun incrementUseCount(id: Long, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT * FROM accounts")
    suspend fun getAllAccountsList(): List<Account>

    @Query("DELETE FROM accounts")
    suspend fun deleteAllAccounts()
}
```

- [ ] **Step 2: 创建 SettingDao**

```kotlin
package com.example.passwordsafe.data.local.database

import androidx.room.*
import com.example.passwordsafe.data.model.AppSetting
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingDao {
    @Query("SELECT * FROM settings WHERE `key` = :key")
    suspend fun getSetting(key: String): AppSetting?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setSetting(setting: AppSetting)

    @Query("DELETE FROM settings WHERE `key` = :key")
    suspend fun deleteSetting(key: String)

    @Query("DELETE FROM settings")
    suspend fun deleteAllSettings()
}
```

- [ ] **Step 3: 创建 AppDatabase**

```kotlin
package com.example.passwordsafe.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.passwordsafe.data.model.Account
import com.example.passwordsafe.data.model.AppSetting

@Database(entities = [Account::class, AppSetting::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun settingDao(): SettingDao
}
```

---

### Task 2.3: 创建 CryptoManager

**Files:**
- Create: `app/src/main/java/com/example/passwordsafe/data/crypto/CryptoManager.kt`

- [ ] **Step 1: 创建加密管理器**

```kotlin
package com.example.passwordsafe.data.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

class CryptoManager(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPrefs = EncryptedSharedPreferences.create(
        context, "encrypted_prefs", masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    companion object {
        private const val KEY_ALIAS = "passwordsafe_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val TAG_LENGTH = 128
    }

    fun encrypt(data: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build()
        keyGenerator.init(spec)
        val secretKey = keyGenerator.generateKey()
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val encryptedData = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + encryptedData, Base64.NO_WRAP)
    }

    fun decrypt(encryptedData: String): String {
        val combined = Base64.decode(encryptedData, Base64.NO_WRAP)
        val iv = combined.sliceArray(0 until 12)
        val data = combined.sliceArray(12 until combined.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(TAG_LENGTH, iv)
        val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        val secretKeyEntry = keyStore.getEntry(KEY_ALIAS, null) as java.security.KeyStore.SecretKeyEntry
        cipher.init(Cipher.DECRYPT_MODE, secretKeyEntry.secretKey, spec)
        return String(cipher.doFinal(data), Charsets.UTF_8)
    }
}
```

---

### Task 2.4: 创建 Repository

**Files:**
- Create: `app/src/main/java/com/example/passwordsafe/data/repository/AccountRepository.kt`
- Create: `app/src/main/java/com/example/passwordsafe/data/repository/SettingRepository.kt`

- [ ] **Step 1: 创建 AccountRepository**

```kotlin
package com.example.passwordsafe.data.repository

import com.example.passwordsafe.data.crypto.CryptoManager
import com.example.passwordsafe.data.local.database.AccountDao
import com.example.passwordsafe.data.model.Account
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepository @Inject constructor(
    private val accountDao: AccountDao,
    private val cryptoManager: CryptoManager
) {
    fun getAllAccounts(): Flow<List<Account>> = accountDao.getAllAccounts()
    fun getRecentAccounts(): Flow<List<Account>> = accountDao.getRecentAccounts()
    suspend fun getAccountById(id: Long): Account? = accountDao.getAccountById(id)
    fun searchAccounts(query: String): Flow<List<Account>> = accountDao.searchAccounts(query)

    suspend fun addAccount(account: Account): Long {
        return accountDao.insertAccount(account.copy(password = cryptoManager.encrypt(account.password)))
    }

    suspend fun updateAccount(account: Account) {
        accountDao.updateAccount(account.copy(password = cryptoManager.encrypt(account.password), updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteAccount(account: Account) = accountDao.deleteAccount(account)
    suspend fun incrementUseCount(id: Long) = accountDao.incrementUseCount(id)
    suspend fun getAllAccountsList(): List<Account> = accountDao.getAllAccountsList()
    suspend fun deleteAllAccounts() = accountDao.deleteAllAccounts()
    fun decryptPassword(encrypted: String): String = cryptoManager.decrypt(encrypted)
}
```

- [ ] **Step 2: 创建 SettingRepository**

```kotlin
package com.example.passwordsafe.data.repository

import com.example.passwordsafe.data.crypto.CryptoManager
import com.example.passwordsafe.data.local.database.SettingDao
import com.example.passwordsafe.data.model.AppSetting
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingRepository @Inject constructor(
    private val settingDao: SettingDao,
    private val cryptoManager: CryptoManager
) {
    suspend fun isFirstTime(): Boolean {
        val setting = settingDao.getSetting(KEY_FIRST_TIME)
        return setting == null || setting.value == "true"
    }

    suspend fun setFirstTime(value: Boolean) = settingDao.setSetting(AppSetting(KEY_FIRST_TIME, value.toString()))
    suspend fun isBiometricEnabled(): Boolean = settingDao.getSetting(KEY_BIOMETRIC_ENABLED)?.value == "true"
    suspend fun setBiometricEnabled(value: Boolean) = settingDao.setSetting(AppSetting(KEY_BIOMETRIC_ENABLED, value.toString()))
    suspend fun isPasswordEnabled(): Boolean = settingDao.getSetting(KEY_PASSWORD_ENABLED)?.value == "true"
    suspend fun setPasswordEnabled(value: Boolean) = settingDao.setSetting(AppSetting(KEY_PASSWORD_ENABLED, value.toString()))

    suspend fun getAppPassword(): String? {
        val setting = settingDao.getSetting(KEY_APP_PASSWORD)
        return setting?.value?.let { cryptoManager.decrypt(it) }
    }

    suspend fun setAppPassword(password: String) = settingDao.setSetting(AppSetting(KEY_APP_PASSWORD, cryptoManager.encrypt(password)))

    suspend fun getAutoLockTimeout(): Long = settingDao.getSetting(KEY_AUTO_LOCK_TIMEOUT)?.value?.toLongOrNull() ?: 60000L
    suspend fun setAutoLockTimeout(timeout: Long) = settingDao.setSetting(AppSetting(KEY_AUTO_LOCK_TIMEOUT, timeout.toString()))
    suspend fun resetAllSettings() = settingDao.deleteAllSettings()

    companion object {
        const val KEY_FIRST_TIME = "is_first_time"
        const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        const val KEY_PASSWORD_ENABLED = "password_enabled"
        const val KEY_APP_PASSWORD = "app_password"
        const val KEY_AUTO_LOCK_TIMEOUT = "auto_lock_timeout"
    }
}
```

---

## Phase 3: 工具类

### Task 3.1: 创建工具类

**Files:**
- Create: `app/src/main/java/com/example/passwordsafe/util/PasswordStrengthChecker.kt`
- Create: `app/src/main/java/com/example/passwordsafe/util/PasswordGenerator.kt`
- Create: `app/src/main/java/com/example/passwordsafe/util/BiometricHelper.kt`
- Create: `app/src/main/java/com/example/passwordsafe/util/AutoLockManager.kt`

- [ ] **Step 1: 创建 PasswordStrengthChecker**

```kotlin
package com.example.passwordsafe.util

enum class PasswordStrength { WEAK, MEDIUM, STRONG }

object PasswordStrengthChecker {
    fun check(password: String): PasswordStrength {
        var score = 0
        if (password.length >= 8) score++
        if (password.length >= 12) score++
        if (password.any { it.isLowerCase() }) score++
        if (password.any { it.isUpperCase() }) score++
        if (password.any { it.isDigit() }) score++
        if (password.any { it in "!@#\$%^&*()_+-=[]{}|;:',.<>?/" }) score++
        return when { score <= 2 -> PasswordStrength.WEAK; score <= 4 -> PasswordStrength.MEDIUM; else -> PasswordStrength.STRONG }
    }

    fun getStrengthText(strength: PasswordStrength) = when (strength) {
        PasswordStrength.WEAK -> "弱"
        PasswordStrength.MEDIUM -> "中"
        PasswordStrength.STRONG -> "强"
    }
}
```

- [ ] **Step 2: 创建 PasswordGenerator**

```kotlin
package com.example.passwordsafe.util

import java.security.SecureRandom

data class PasswordOptions(
    val length: Int = 16,
    val includeUppercase: Boolean = true,
    val includeLowercase: Boolean = true,
    val includeDigits: Boolean = true,
    val includeSpecial: Boolean = true
)

object PasswordGenerator {
    private const val UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val LOWERCASE = "abcdefghijklmnopqrstuvwxyz"
    private const val DIGITS = "0123456789"
    private const val SPECIAL = "!@#\$%^&*"
    private val random = SecureRandom()

    fun generate(options: PasswordOptions): String {
        var charset = ""
        val required = mutableListOf<Char>()
        if (options.includeUppercase) { charset += UPPERCASE; required.add(UPPERCASE.random()) }
        if (options.includeLowercase) { charset += LOWERCASE; required.add(LOWERCASE.random()) }
        if (options.includeDigits) { charset += DIGITS; required.add(DIGITS.random()) }
        if (options.includeSpecial) { charset += SPECIAL; required.add(SPECIAL.random()) }
        if (charset.isEmpty()) charset = LOWERCASE + DIGITS

        val passwordChars = required.toMutableList()
        repeat(options.length - required.size) { passwordChars.add(charset.random(random)) }
        passwordChars.shuffle(random)
        return passwordChars.joinToString("")
    }
}
```

- [ ] **Step 3: 创建 BiometricHelper**

```kotlin
package com.example.passwordsafe.util

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object BiometricHelper {
    fun isBiometricAvailable(context: Context): Boolean {
        return BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun showBiometricPrompt(activity: FragmentActivity, title: String, subtitle: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val executor = ContextCompat.getMainExecutor(activity)
        val biometricPrompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
            override fun onAuthenticationFailed() = onError("认证失败")
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onError(errString.toString())
        })
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title).setSubtitle(subtitle).setNegativeButtonText("使用密码")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG).build()
        biometricPrompt.authenticate(promptInfo)
    }
}
```

- [ ] **Step 4: 创建 AutoLockManager**

```kotlin
package com.example.passwordsafe.util

import android.app.Application
import android.os.Handler
import android.os.Looper
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutoLockManager @Inject constructor(private val application: Application) {
    private var lastActiveTime = System.currentTimeMillis()
    private var isLocked = false
    private var autoLockTimeout = 60000L
    private val handler = Handler(Looper.getMainLooper())
    private val lockCheckRunnable = object : Runnable {
        override fun run() {
            if (System.currentTimeMillis() - lastActiveTime > autoLockTimeout && !isLocked) isLocked = true
            handler.postDelayed(this, 1000)
        }
    }

    fun setAutoLockTimeout(timeout: Long) { autoLockTimeout = timeout }
    fun onUserInteraction() { lastActiveTime = System.currentTimeMillis(); isLocked = false }
    fun onAppBackgrounded() { lastActiveTime = System.currentTimeMillis() }
    fun onAppForegrounded(): Boolean {
        if (System.currentTimeMillis() - lastActiveTime > autoLockTimeout) { isLocked = true; return true }
        return false
    }
    fun isAppLocked() = isLocked
    fun unlock() { isLocked = false; lastActiveTime = System.currentTimeMillis() }
    fun lock() { isLocked = true }
    fun startMonitoring() { handler.post(lockCheckRunnable) }
    fun stopMonitoring() { handler.removeCallbacks(lockCheckRunnable) }
}
```

---

## Phase 4: 资源文件

### Task 4.1: 创建 drawable 资源

**Files:**
- Create: `app/src/main/res/drawable/ic_shield_green.xml`
- Create: `app/src/main/res/drawable/ic_shield_orange.xml`
- Create: `app/src/main/res/drawable/ic_add.xml`
- Create: `app/src/main/res/drawable/ic_copy.xml`
- Create: `app/src/main/res/drawable/ic_edit.xml`
- Create: `app/src/main/res/drawable/ic_delete.xml`
- Create: `app/src/main/res/drawable/ic_settings.xml`
- Create: `app/src/main/res/drawable/ic_fingerprint.xml`
- Create: `app/src/main/res/drawable/bg_category.xml`

- [ ] **Step 1: 创建所有 drawable 文件**

`ic_shield_green.xml`:
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#4CAF50" android:pathData="M12,1L3,5v6c0,5.55 3.84,10.74 9,12 5.16,-1.26 9,-6.45 9,-12V5l-9,-4z"/>
</vector>
```

`ic_shield_orange.xml`:
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#FF9800" android:pathData="M12,1L3,5v6c0,5.55 3.84,10.74 9,12 5.16,-1.26 9,-6.45 9,-12V5l-9,-4z"/>
</vector>
```

`ic_add.xml`:
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#FFFFFF" android:pathData="M19,13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"/>
</vector>
```

`ic_copy.xml`:
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#000000" android:pathData="M16,1H4C2.9,1 2,1.9 2,3v14h2V3h12V1zM19,5H8C6.9,5 6,5.9 6,7v14c0,1.1 0.9,2 2,2h11c1.1,0 2,-0.9 2,-2V7C21,5.9 20.1,5 19,5zM19,21H8V7h11V21z"/>
</vector>
```

`ic_edit.xml`:
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#000000" android:pathData="M3,17.25V21h3.75L17.81,9.94l-3.75,-3.75L3,17.25zM20.71,7.04c0.39,-0.39 0.39,-1.02 0,-1.41l-2.34,-2.34c-0.39,-0.39 -1.02,-0.39 -1.41,0l-1.83,1.83 3.75,3.75 1.83,-1.83z"/>
</vector>
```

`ic_delete.xml`:
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#000000" android:pathData="M6,19c0,1.1 0.9,2 2,2h8c1.1,0 2,-0.9 2,-2V7H6v12zM19,4h-3.5l-1,-1h-5l-1,1H5v2h14V4z"/>
</vector>
```

`ic_settings.xml`:
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#000000" android:pathData="M19.14,12.94c0.04,-0.3 0.06,-0.61 0.06,-0.94 0,-0.32 -0.02,-0.64 -0.07,-0.94l2.03,-1.58c0.18,-0.14 0.23,-0.41 0.12,-0.61l-1.92,-3.32c-0.12,-0.22 -0.37,-0.29 -0.59,-0.22l-2.39,0.96c-0.5,-0.38 -1.03,-0.7 -1.62,-0.94L14.4,3.81c-0.04,-0.24 -0.24,-0.41 -0.48,-0.41h-3.84c-0.24,0 -0.43,0.17 -0.47,0.41L9.25,6.35C8.66,6.59 8.12,6.92 7.63,7.28L5.24,6.33c-0.22,-0.08 -0.47,0 -0.59,0.22L2.74,9.87c-0.12,0.21 -0.08,0.47 0.12,0.61l2.03,1.58C4.84,12.36 4.8,12.69 4.8,13s0.02,0.64 0.07,0.94l-2.03,1.58c-0.18,0.14 -0.23,0.41 -0.12,0.61l1.92,3.32c0.12,0.22 0.37,0.29 0.59,0.22l2.39,-0.96c0.5,0.38 1.03,0.7 1.62,0.94l0.36,2.54c0.05,0.24 0.24,0.41 0.48,0.41h3.84c0.24,0 0.44,-0.17 0.47,-0.41l0.36,-2.54c0.59,-0.24 1.13,-0.56 1.62,-0.94l2.39,0.96c0.22,0.08 0.47,0 0.59,-0.22l1.92,-3.32c0.12,-0.22 0.07,-0.47 -0.12,-0.61L19.14,12.94zM12,15.6c-1.43,0 -2.6,-1.16 -2.6,-2.6 0,-1.43 1.17,-2.6 2.6,-2.6s2.6,1.17 2.6,2.6C14.6,14.44 13.43,15.6 12,15.6z"/>
</vector>
```

`ic_fingerprint.xml`:
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#FFFFFF" android:pathData="M17.81,4.47c-0.08,0 -0.16,-0.02 -0.23,-0.06C15.66,3.42 14,3 12.01,3c-1.98,0 -3.86,0.47 -5.57,1.41 -0.24,0.13 -0.54,0.04 -0.68,-0.2 -0.13,-0.24 -0.04,-0.55 0.2,-0.68C7.82,2.52 9.86,2 12.01,2c2.13,0 3.99,0.47 6.03,1.52 0.25,0.13 0.34,0.43 0.21,0.67 -0.09,0.18 -0.26,0.28 -0.44,0.28zM3.5,9.72c-0.1,0 -0.2,-0.03 -0.29,-0.09 -0.23,-0.16 -0.28,-0.47 -0.12,-0.7 0.99,-1.4 2.25,-2.5 3.75,-3.27C9.98,4.04 14,4.03 17.15,5.65c1.5,0.77 2.76,1.86 3.75,3.25 0.16,0.22 0.11,0.54 -0.12,0.7 -0.23,0.16 -0.54,0.11 -0.7,-0.12 -0.9,-1.26 -2.04,-2.25 -3.39,-2.94 -2.87,-1.47 -6.54,-1.47 -9.4,0.01 -1.36,0.7 -2.5,1.7 -3.4,2.96 -0.08,0.12 -0.21,0.18 -0.39,0.21zM9.75,21.79c-0.13,0 -0.26,-0.04 -0.37,-0.13 -0.87,-0.87 -1.34,-1.43 -2.01,-2.64 -0.69,-1.23 -1.05,-2.73 -1.05,-4.34 0,-2.97 2.54,-5.39 5.66,-5.39s5.66,2.42 5.66,5.39c0,0.28 -0.22,0.5 -0.5,0.5s-0.5,-0.22 -0.5,-0.5c0,-2.42 -2.09,-4.39 -4.66,-4.39 -2.57,0 -4.66,1.97 -4.66,4.39 0,1.44 0.32,2.77 0.93,3.85 0.64,1.13 1.08,1.62 1.85,2.4 0.19,0.2 0.19,0.51 0,0.71 -0.11,0.1 -0.24,0.15 -0.35,0.15z"/>
</vector>
```

`bg_category.xml`:
```xml
<shape xmlns:android="http://schemas.android.com/apk/res/android">
    <solid android:color="#2196F3"/>
    <corners android:radius="4dp"/>
</shape>
```

---

### Task 4.2: 更新字符串和颜色资源

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values/colors.xml`

- [ ] **Step 1: 更新 strings.xml**

```xml
<resources>
    <string name="app_name">PasswordSafe</string>
    <string name="title_home">首页</string>
    <string name="title_settings">我的</string>
    <string name="title_accounts">账号列表</string>
    <string name="title_detail">账号详情</string>
    <string name="title_add_account">添加账号</string>
    <string name="title_edit_account">编辑账号</string>

    <string name="auth_title">验证身份</string>
    <string name="auth_subtitle">请输入密码或使用生物识别</string>
    <string name="setup_password_title">设置主密码</string>
    <string name="setup_password_subtitle">设置密码以保护您的账号信息</string>
    <string name="hint_password">输入密码</string>
    <string name="hint_new_password">设置新密码</string>
    <string name="hint_confirm_password">确认密码</string>
    <string name="use_biometric">使用生物识别</string>
    <string name="unlock">解锁</string>
    <string name="set_password">设置密码</string>
    <string name="skip_setup">跳过设置</string>
    <string name="enable_biometric">启用生物识别</string>
    <string name="password_warning">此密码用于加密您的数据，忘记后将无法恢复</string>

    <string name="search_hint">搜索账号...</string>
    <string name="recent_accounts">最近使用</string>
    <string name="no_accounts">暂无账号</string>
    <string name="add_first_account">点击右下角按钮添加第一个账号</string>
    <string name="add_account">添加账号</string>

    <string name="hint_website">网站名称 *</string>
    <string name="hint_website_url">网站地址</string>
    <string name="hint_account">账号 *</string>
    <string name="hint_password_field">密码 *</string>
    <string name="hint_notes">备注</string>
    <string name="hint_category">分类</string>
    <string name="generate_password">生成密码</string>
    <string name="save">保存</string>
    <string name="delete">删除</string>
    <string name="copy">复制</string>
    <string name="copy_account">复制账号</string>
    <string name="copy_password">复制密码</string>
    <string name="edit">编辑</string>

    <string name="password_verification">密码验证</string>
    <string name="biometric_verification">生物识别验证</string>
    <string name="change_password">修改密码</string>
    <string name="auto_lock">自动锁定</string>
    <string name="export_excel">导出为Excel</string>
    <string name="import_excel">从Excel导入</string>
    <string name="copy_as_text">复制为文本</string>
    <string name="reset_app">重置应用</string>

    <string name="password_length">密码长度: %d</string>
    <string name="uppercase">大写字母</string>
    <string name="lowercase">小写字母</string>
    <string name="digits">数字</string>
    <string name="special_chars">特殊符号</string>
    <string name="regenerate">重新生成</string>
    <string name="use_password">使用此密码</string>

    <string name="saved">已保存</string>
    <string name="deleted">已删除</string>
    <string name="copied">已复制到剪贴板</string>
    <string name="export_success">导出成功</string>
    <string name="import_success">导入成功: 新增 %d 条</string>
    <string name="no_search_results">未找到匹配的账号</string>
    <string name="confirm_delete">确定要删除此账号吗？</string>
    <string name="confirm_reset">确定要重置应用吗？所有数据将被清除。</string>
    <string name="export_warning">导出的数据为明文，请注意保管</string>
    <string name="uncategorized">未分类</string>

    <string name="lock_immediately">立即</string>
    <string name="lock_1min">1分钟</string>
    <string name="lock_5min">5分钟</string>
    <string name="lock_15min">15分钟</string>
</resources>
```

- [ ] **Step 2: 更新 colors.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="purple_200">#FFBB86FC</color>
    <color name="purple_500">#FF6200EE</color>
    <color name="purple_700">#FF3700B3</color>
    <color name="teal_200">#FF03DAC5</color>
    <color name="teal_700">#FF018786</color>
    <color name="black">#FF000000</color>
    <color name="white">#FFFFFFFF</color>
    <color name="green">#4CAF50</color>
    <color name="orange">#FF9800</color>
    <color name="red">#F44336</color>
    <color name="gray">#757575</color>
    <color name="gray_light">#F5F5F5</color>
    <color name="primary">#2196F3</color>
    <color name="primary_dark">#1976D2</color>
</resources>
```

---

## Phase 5: 验证模块

### Task 5.1: 创建验证页面

**Files:**
- Create: `app/src/main/res/layout/fragment_auth.xml`
- Create: `app/src/main/res/layout/fragment_setup_password.xml`
- Create: `app/src/main/java/com/example/passwordsafe/ui/auth/AuthViewModel.kt`
- Create: `app/src/main/java/com/example/passwordsafe/ui/auth/AuthFragment.kt`
- Create: `app/src/main/java/com/example/passwordsafe/ui/auth/SetupPasswordFragment.kt`

- [ ] **Step 1: 创建 fragment_auth.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android" xmlns:app="http://schemas.android.com/apk/res-auto" android:layout_width="match_parent" android:layout_height="match_parent" android:padding="24dp">

    <ImageView android:id="@+id/iv_logo" android:layout_width="80dp" android:layout_height="80dp" android:src="@drawable/ic_fingerprint" android:background="@drawable/bg_category" android:padding="20dp" app:layout_constraintTop_toTopOf="parent" app:layout_constraintBottom_toTopOf="@id/tv_title" app:layout_constraintStart_toStartOf="parent" app:layout_constraintEnd_toEndOf="parent" app:layout_constraintVertical_chainStyle="packed"/>

    <TextView android:id="@+id/tv_title" android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="@string/auth_title" android:textSize="24sp" android:textStyle="bold" android:layout_marginTop="32dp" app:layout_constraintTop_toBottomOf="@id/iv_logo" app:layout_constraintStart_toStartOf="parent" app:layout_constraintEnd_toEndOf="parent"/>

    <TextView android:id="@+id/tv_subtitle" android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="@string/auth_subtitle" android:textSize="14sp" android:textColor="@color/gray" android:layout_marginTop="8dp" app:layout_constraintTop_toBottomOf="@id/tv_title" app:layout_constraintStart_toStartOf="parent" app:layout_constraintEnd_toEndOf="parent"/>

    <com.google.android.material.textfield.TextInputLayout android:id="@+id/til_password" android:layout_width="match_parent" android:layout_height="wrap_content" android:layout_marginTop="32dp" app:passwordToggleEnabled="true" app:layout_constraintTop_toBottomOf="@id/tv_subtitle">
        <com.google.android.material.textfield.TextInputEditText android:id="@+id/et_password" android:layout_width="match_parent" android:layout_height="wrap_content" android:hint="@string/hint_password" android:inputType="textPassword"/>
    </com.google.android.material.textfield.TextInputLayout>

    <Button android:id="@+id/btn_biometric" android:layout_width="match_parent" android:layout_height="wrap_content" android:text="@string/use_biometric" android:drawableStart="@drawable/ic_fingerprint" android:drawablePadding="8dp" style="@style/Widget.MaterialComponents.Button.OutlinedButton" android:layout_marginTop="16dp" app:layout_constraintTop_toBottomOf="@id/til_password"/>

    <Button android:id="@+id/btn_unlock" android:layout_width="match_parent" android:layout_height="wrap_content" android:text="@string/unlock" android:layout_marginTop="8dp" app:layout_constraintTop_toBottomOf="@id/btn_biometric"/>

    <TextView android:id="@+id/tv_error" android:layout_width="wrap_content" android:layout_height="wrap_content" android:textColor="@color/red" android:textSize="12sp" android:visibility="gone" android:layout_marginTop="8dp" app:layout_constraintTop_toBottomOf="@id/btn_unlock" app:layout_constraintStart_toStartOf="parent" app:layout_constraintEnd_toEndOf="parent"/>

</androidx.constraintlayout.widget.ConstraintLayout>
```

- [ ] **Step 2: 创建 fragment_setup_password.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android" xmlns:app="http://schemas.android.com/apk/res-auto" android:layout_width="match_parent" android:layout_height="match_parent" android:padding="24dp">

    <TextView android:id="@+id/tv_title" android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="@string/setup_password_title" android:textSize="24sp" android:textStyle="bold" app:layout_constraintTop_toTopOf="parent" app:layout_constraintStart_toStartOf="parent" app:layout_constraintEnd_toEndOf="parent"/>

    <TextView android:id="@+id/tv_subtitle" android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="@string/setup_password_subtitle" android:textSize="14sp" android:textColor="@color/gray" android:layout_marginTop="8dp" app:layout_constraintTop_toBottomOf="@id/tv_title" app:layout_constraintStart_toStartOf="parent" app:layout_constraintEnd_toEndOf="parent"/>

    <com.google.android.material.textfield.TextInputLayout android:id="@+id/til_password" android:layout_width="match_parent" android:layout_height="wrap_content" android:layout_marginTop="32dp" app:passwordToggleEnabled="true" app:layout_constraintTop_toBottomOf="@id/tv_subtitle">
        <com.google.android.material.textfield.TextInputEditText android:id="@+id/et_password" android:layout_width="match_parent" android:layout_height="wrap_content" android:hint="@string/hint_new_password" android:inputType="textPassword"/>
    </com.google.android.material.textfield.TextInputLayout>

    <LinearLayout android:id="@+id/ll_strength" android:layout_width="match_parent" android:layout_height="wrap_content" android:orientation="horizontal" android:layout_marginTop="8dp" app:layout_constraintTop_toBottomOf="@id/til_password">
        <View android:id="@+id/v_strength1" android:layout_width="0dp" android:layout_height="4dp" android:layout_weight="1" android:layout_marginEnd="4dp" android:background="@color/gray_light"/>
        <View android:id="@+id/v_strength2" android:layout_width="0dp" android:layout_height="4dp" android:layout_weight="1" android:layout_marginStart="4dp" android:layout_marginEnd="4dp" android:background="@color/gray_light"/>
        <View android:id="@+id/v_strength3" android:layout_width="0dp" android:layout_height="4dp" android:layout_weight="1" android:layout_marginStart="4dp" android:background="@color/gray_light"/>
    </LinearLayout>

    <TextView android:id="@+id/tv_strength" android:layout_width="wrap_content" android:layout_height="wrap_content" android:textSize="12sp" android:layout_marginTop="4dp" app:layout_constraintTop_toBottomOf="@id/ll_strength" app:layout_constraintStart_toStartOf="parent"/>

    <com.google.android.material.textfield.TextInputLayout android:id="@+id/til_confirm" android:layout_width="match_parent" android:layout_height="wrap_content" android:layout_marginTop="16dp" app:passwordToggleEnabled="true" app:layout_constraintTop_toBottomOf="@id/tv_strength">
        <com.google.android.material.textfield.TextInputEditText android:id="@+id/et_confirm" android:layout_width="match_parent" android:layout_height="wrap_content" android:hint="@string/hint_confirm_password" android:inputType="textPassword"/>
    </com.google.android.material.textfield.TextInputLayout>

    <TextView android:id="@+id/tv_warning" android:layout_width="match_parent" android:layout_height="wrap_content" android:text="@string/password_warning" android:textSize="12sp" android:textColor="@color/orange" android:layout_marginTop="16dp" app:layout_constraintTop_toBottomOf="@id/til_confirm"/>

    <com.google.android.material.switchmaterial.SwitchMaterial android:id="@+id/switch_biometric" android:layout_width="match_parent" android:layout_height="wrap_content" android:text="@string/enable_biometric" android:layout_marginTop="24dp" app:layout_constraintTop_toBottomOf="@id/tv_warning"/>

    <Button android:id="@+id/btn_set_password" android:layout_width="match_parent" android:layout_height="wrap_content" android:text="@string/set_password" android:layout_marginTop="24dp" app:layout_constraintTop_toBottomOf="@id/switch_biometric"/>

    <Button android:id="@+id/btn_skip" android:layout_width="match_parent" android:layout_height="wrap_content" android:text="@string/skip_setup" style="@style/Widget.MaterialComponents.Button.TextButton" android:layout_marginTop="8dp" app:layout_constraintTop_toBottomOf="@id/btn_set_password"/>

</androidx.constraintlayout.widget.ConstraintLayout>
```

- [ ] **Step 3: 创建 AuthViewModel**

```kotlin
package com.example.passwordsafe.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.passwordsafe.data.repository.SettingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class AuthState { object Idle : AuthState(); object Loading : AuthState(); object Success : AuthState(); data class Error(val message: String) : AuthState() }

@HiltViewModel
class AuthViewModel @Inject constructor(private val settingRepository: SettingRepository) : ViewModel() {
    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState
    private val _isFirstTime = MutableStateFlow(true)
    val isFirstTime: StateFlow<Boolean> = _isFirstTime
    private val _isBiometricEnabled = MutableStateFlow(false)
    val isBiometricEnabled: StateFlow<Boolean> = _isBiometricEnabled

    init { checkInitialState() }

    private fun checkInitialState() {
        viewModelScope.launch {
            _isFirstTime.value = settingRepository.isFirstTime()
            _isBiometricEnabled.value = settingRepository.isBiometricEnabled()
        }
    }

    fun verifyPassword(inputPassword: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val savedPassword = settingRepository.getAppPassword()
            if (savedPassword == null) { _authState.value = AuthState.Error("未设置密码"); return@launch }
            _authState.value = if (inputPassword == savedPassword) AuthState.Success else AuthState.Error("密码错误")
        }
    }

    fun completeFirstTimeSetup(password: String? = null, enableBiometric: Boolean = false) {
        viewModelScope.launch {
            password?.let {
                settingRepository.setAppPassword(it)
                settingRepository.setPasswordEnabled(true)
            }
            if (enableBiometric) settingRepository.setBiometricEnabled(true)
            settingRepository.setFirstTime(false)
            _authState.value = AuthState.Success
        }
    }

    fun resetState() { _authState.value = AuthState.Idle }
}
```

- [ ] **Step 4: 创建 AuthFragment**

```kotlin
package com.example.passwordsafe.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.passwordsafe.R
import com.example.passwordsafe.databinding.FragmentAuthBinding
import com.example.passwordsafe.util.BiometricHelper
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AuthFragment : Fragment() {
    private var _binding: FragmentAuthBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AuthViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAuthBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        checkBiometricAvailability()
        observeState()
        setupListeners()
    }

    private fun checkBiometricAvailability() {
        val isAvailable = BiometricHelper.isBiometricAvailable(requireContext())
        binding.btnBiometric.visibility = if (isAvailable) View.VISIBLE else View.GONE
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.isFirstTime.collect { isFirstTime ->
                        if (isFirstTime) findNavController().navigate(R.id.action_auth_to_setupPassword)
                    }
                }
                launch {
                    viewModel.authState.collect { state ->
                        when (state) {
                            is AuthState.Success -> findNavController().navigate(R.id.action_auth_to_home)
                            is AuthState.Error -> { binding.tvError.text = state.message; binding.tvError.visibility = View.VISIBLE }
                            else -> {}
                        }
                    }
                }
            }
        }
    }

    private fun setupListeners() {
        binding.btnUnlock.setOnClickListener {
            val password = binding.etPassword.text.toString()
            if (password.isNotEmpty()) viewModel.verifyPassword(password)
            else binding.tilPassword.error = "请输入密码"
        }
        binding.btnBiometric.setOnClickListener { showBiometricPrompt() }
    }

    private fun showBiometricPrompt() {
        BiometricHelper.showBiometricPrompt(requireActivity(), "验证身份", "使用生物识别解锁应用",
            { viewModel.resetState(); findNavController().navigate(R.id.action_auth_to_home) },
            { Snackbar.make(binding.root, it, Snackbar.LENGTH_SHORT).show() }
        )
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
```

- [ ] **Step 5: 创建 SetupPasswordFragment**

```kotlin
package com.example.passwordsafe.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.passwordsafe.R
import com.example.passwordsafe.databinding.FragmentSetupPasswordBinding
import com.example.passwordsafe.util.BiometricHelper
import com.example.passwordsafe.util.PasswordStrength
import com.example.passwordsafe.util.PasswordStrengthChecker
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SetupPasswordFragment : Fragment() {
    private var _binding: FragmentSetupPasswordBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AuthViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSetupPasswordBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupPasswordStrengthWatcher()
        setupListeners()
        observeState()
        checkBiometric()
    }

    private fun checkBiometric() {
        binding.switchBiometric.visibility = if (BiometricHelper.isBiometricAvailable(requireContext())) View.VISIBLE else View.GONE
    }

    private fun setupPasswordStrengthWatcher() {
        binding.etPassword.addTextChangedListener { text ->
            updateStrengthIndicator(PasswordStrengthChecker.check(text?.toString() ?: ""))
        }
    }

    private fun updateStrengthIndicator(strength: PasswordStrength) {
        val colorRes = when (strength) { PasswordStrength.WEAK -> R.color.red; PasswordStrength.MEDIUM -> R.color.orange; PasswordStrength.STRONG -> R.color.green }
        val color = requireContext().getColor(colorRes)
        with(binding) {
            vStrength1.setBackgroundColor(if (strength != PasswordStrength.WEAK) color else requireContext().getColor(R.color.gray_light))
            vStrength2.setBackgroundColor(if (strength == PasswordStrength.STRONG) color else requireContext().getColor(R.color.gray_light))
            vStrength3.setBackgroundColor(if (strength == PasswordStrength.STRONG) color else requireContext().getColor(R.color.gray_light))
            tvStrength.text = PasswordStrengthChecker.getStrengthText(strength)
            tvStrength.setTextColor(color)
        }
    }

    private fun setupListeners() {
        binding.btnSetPassword.setOnClickListener {
            val password = binding.etPassword.text.toString()
            val confirm = binding.etConfirm.text.toString()
            when {
                password.isEmpty() -> binding.tilPassword.error = "请输入密码"
                password.length < 6 -> binding.tilPassword.error = "密码至少6位"
                password != confirm -> binding.tilConfirm.error = "两次密码不一致"
                else -> viewModel.completeFirstTimeSetup(password, binding.switchBiometric.isChecked)
            }
        }
        binding.btnSkip.setOnClickListener { showSkipConfirmation() }
    }

    private fun showSkipConfirmation() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("跳过设置").setMessage("不设置密码将无法保护您的账号信息，确定要跳过吗？")
            .setPositiveButton("跳过") { _, _ -> viewModel.completeFirstTimeSetup() }
            .setNegativeButton("取消", null).show()
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.authState.collect { state ->
                    when (state) {
                        is AuthState.Success -> findNavController().navigate(R.id.action_setupPassword_to_home)
                        is AuthState.Error -> Snackbar.make(binding.root, state.message, Snackbar.LENGTH_SHORT).show()
                        else -> {}
                    }
                }
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
```

---

## Phase 6: 首页模块

### Task 6.1: 创建首页

**Files:**
- Modify: `app/src/main/res/layout/fragment_home.xml`
- Create: `app/src/main/res/layout/item_account.xml`
- Create: `app/src/main/java/com/example/passwordsafe/ui/home/AccountAdapter.kt`
- Modify: `app/src/main/java/com/example/passwordsafe/ui/home/HomeFragment.kt`
- Modify: `app/src/main/java/com/example/passwordsafe/ui/home/HomeViewModel.kt`

- [ ] **Step 1: 更新 fragment_home.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.coordinatorlayout.widget.CoordinatorLayout xmlns:android="http://schemas.android.com/apk/res/android" xmlns:app="http://schemas.android.com/apk/res-auto" android:layout_width="match_parent" android:layout_height="match_parent">

    <androidx.constraintlayout.widget.ConstraintLayout android:layout_width="match_parent" android:layout_height="match_parent">
        <LinearLayout android:id="@+id/ll_security_status" android:layout_width="match_parent" android:layout_height="wrap_content" android:orientation="horizontal" android:gravity="center_vertical" android:padding="16dp" android:background="@color/gray_light" app:layout_constraintTop_toTopOf="parent">
            <ImageView android:id="@+id/iv_security" android:layout_width="24dp" android:layout_height="24dp" android:src="@drawable/ic_shield_green"/>
            <TextView android:id="@+id/tv_security_status" android:layout_width="wrap_content" android:layout_height="wrap_content" android:layout_marginStart="8dp" android:textSize="14sp"/>
        </LinearLayout>

        <com.google.android.material.textfield.TextInputLayout android:id="@+id/til_search" android:layout_width="match_parent" android:layout_height="wrap_content" android:layout_margin="16dp" app:startIconDrawable="@android:drawable/ic_menu_search" app:boxBackgroundMode="outline" app:layout_constraintTop_toBottomOf="@id/ll_security_status">
            <com.google.android.material.textfield.TextInputEditText android:id="@+id/et_search" android:layout_width="match_parent" android:layout_height="wrap_content" android:hint="@string/search_hint" android:inputType="text" android:focusable="false"/>
        </com.google.android.material.textfield.TextInputLayout>

        <TextView android:id="@+id/tv_recent_title" android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="@string/recent_accounts" android:textSize="16sp" android:textStyle="bold" android:layout_marginStart="16dp" android:layout_marginTop="8dp" app:layout_constraintStart_toStartOf="parent" app:layout_constraintTop_toBottomOf="@id/til_search"/>

        <androidx.recyclerview.widget.RecyclerView android:id="@+id/rv_accounts" android:layout_width="match_parent" android:layout_height="0dp" android:layout_marginTop="8dp" android:paddingStart="12dp" android:paddingEnd="12dp" app:layout_constraintTop_toBottomOf="@id/tv_recent_title" app:layout_constraintBottom_toBottomOf="parent"/>

        <LinearLayout android:id="@+id/ll_empty" android:layout_width="wrap_content" android:layout_height="wrap_content" android:orientation="vertical" android:gravity="center" android:visibility="gone" app:layout_constraintTop_toBottomOf="@id/tv_recent_title" app:layout_constraintBottom_toBottomOf="parent" app:layout_constraintStart_toStartOf="parent" app:layout_constraintEnd_toEndOf="parent">
            <TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="@string/no_accounts" android:textSize="16sp" android:textColor="@color/gray"/>
            <TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="@string/add_first_account" android:textSize="14sp" android:textColor="@color/gray" android:layout_marginTop="8dp"/>
        </LinearLayout>
    </androidx.constraintlayout.widget.ConstraintLayout>

    <com.google.android.material.floatingactionbutton.FloatingActionButton android:id="@+id/fab_add" android:layout_width="wrap_content" android:layout_height="wrap_content" android:layout_gravity="bottom|end" android:layout_margin="16dp" android:src="@drawable/ic_add" android:contentDescription="@string/add_account"/>

</androidx.coordinatorlayout.widget.CoordinatorLayout>
```

- [ ] **Step 2: 创建 item_account.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.cardview.widget.CardView xmlns:android="http://schemas.android.com/apk/res/android" xmlns:app="http://schemas.android.com/apk/res-auto" android:layout_width="match_parent" android:layout_height="wrap_content" android:layout_margin="4dp" app:cardCornerRadius="8dp" app:cardElevation="2dp">
    <androidx.constraintlayout.widget.ConstraintLayout android:layout_width="match_parent" android:layout_height="wrap_content" android:padding="16dp">
        <TextView android:id="@+id/tv_website" android:layout_width="0dp" android:layout_height="wrap_content" android:textSize="16sp" android:textStyle="bold" android:ellipsize="end" android:maxLines="1" app:layout_constraintStart_toStartOf="parent" app:layout_constraintEnd_toEndOf="parent" app:layout_constraintTop_toTopOf="parent"/>
        <TextView android:id="@+id/tv_account" android:layout_width="0dp" android:layout_height="wrap_content" android:textSize="14sp" android:textColor="@color/gray" android:ellipsize="end" android:maxLines="1" android:layout_marginTop="4dp" app:layout_constraintStart_toStartOf="parent" app:layout_constraintEnd_toEndOf="parent" app:layout_constraintTop_toBottomOf="@id/tv_website"/>
        <TextView android:id="@+id/tv_category" android:layout_width="wrap_content" android:layout_height="wrap_content" android:textSize="12sp" android:textColor="@color/white" android:background="@drawable/bg_category" android:paddingStart="8dp" android:paddingEnd="8dp" android:paddingTop="2dp" android:paddingBottom="2dp" android:layout_marginTop="8dp" app:layout_constraintStart_toStartOf="parent" app:layout_constraintTop_toBottomOf="@id/tv_account"/>
    </androidx.constraintlayout.widget.ConstraintLayout>
</androidx.cardview.widget.CardView>
```

- [ ] **Step 3: 创建 AccountAdapter**

```kotlin
package com.example.passwordsafe.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.passwordsafe.R
import com.example.passwordsafe.data.model.Account
import com.example.passwordsafe.databinding.ItemAccountBinding

class AccountAdapter(private val onItemClick: (Account) -> Unit) : ListAdapter<Account, AccountAdapter.AccountViewHolder>(AccountDiffCallback()) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = AccountViewHolder(ItemAccountBinding.inflate(LayoutInflater.from(parent.context), parent, false), onItemClick)
    override fun onBindViewHolder(holder: AccountViewHolder, position: Int) = holder.bind(getItem(position))

    class AccountViewHolder(private val binding: ItemAccountBinding, private val onItemClick: (Account) -> Unit) : RecyclerView.ViewHolder(binding.root) {
        fun bind(account: Account) {
            binding.tvWebsite.text = account.website
            binding.tvAccount.text = account.account
            binding.tvCategory.text = account.category.ifEmpty { binding.root.context.getString(R.string.uncategorized) }
            binding.root.setOnClickListener { onItemClick(account) }
        }
    }

    class AccountDiffCallback : DiffUtil.ItemCallback<Account>() {
        override fun areItemsTheSame(oldItem: Account, newItem: Account) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Account, newItem: Account) = oldItem == newItem
    }
}
```

- [ ] **Step 4: 更新 HomeViewModel**

```kotlin
package com.example.passwordsafe.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.passwordsafe.data.model.Account
import com.example.passwordsafe.data.repository.AccountRepository
import com.example.passwordsafe.data.repository.SettingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val settingRepository: SettingRepository
) : ViewModel() {
    private val _recentAccounts = MutableStateFlow<List<Account>>(emptyList())
    val recentAccounts: StateFlow<List<Account>> = _recentAccounts.asStateFlow()
    private val _isSecure = MutableStateFlow(true)
    val isSecure: StateFlow<Boolean> = _isSecure.asStateFlow()

    init {
        loadRecentAccounts()
        checkSecurityStatus()
    }

    private fun loadRecentAccounts() {
        viewModelScope.launch {
            accountRepository.getRecentAccounts().collect { _recentAccounts.value = it }
        }
    }

    private fun checkSecurityStatus() {
        viewModelScope.launch {
            val passwordEnabled = settingRepository.isPasswordEnabled()
            val biometricEnabled = settingRepository.isBiometricEnabled()
            _isSecure.value = passwordEnabled || biometricEnabled
        }
    }
}
```

- [ ] **Step 5: 更新 HomeFragment**

```kotlin
package com.example.passwordsafe.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.passwordsafe.R
import com.example.passwordsafe.databinding.FragmentHomeBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HomeViewModel by viewModels()
    private lateinit var adapter: AccountAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupListeners()
        observeData()
    }

    private fun setupRecyclerView() {
        adapter = AccountAdapter { account ->
            val action = HomeFragmentDirections.actionHomeToDetail(account.id)
            findNavController().navigate(action)
        }
        binding.rvAccounts.apply { layoutManager = LinearLayoutManager(requireContext()); adapter = this@HomeFragment.adapter }
    }

    private fun setupListeners() {
        binding.fabAdd.setOnClickListener { findNavController().navigate(R.id.action_home_to_addEdit) }
        binding.etSearch.setOnClickListener { findNavController().navigate(R.id.action_home_to_accounts) }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.recentAccounts.collect { accounts ->
                        adapter.submitList(accounts)
                        binding.llEmpty.visibility = if (accounts.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    viewModel.isSecure.collect { isSecure -> updateSecurityStatus(isSecure) }
                }
            }
        }
    }

    private fun updateSecurityStatus(isSecure: Boolean) {
        if (isSecure) { binding.ivSecurity.setImageResource(R.drawable.ic_shield_green); binding.tvSecurityStatus.text = "安全" }
        else { binding.ivSecurity.setImageResource(R.drawable.ic_shield_orange); binding.tvSecurityStatus.text = "建议设置验证" }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
```

---

## Phase 7: 账号列表、详情、添加编辑模块

由于篇幅限制，以下模块的实现代码结构类似，按照上述模式创建：

### Task 7.1: 账号列表模块

**Files:**
- Create: `app/src/main/res/layout/fragment_accounts.xml`
- Create: `app/src/main/java/com/example/passwordsafe/ui/accounts/AccountsViewModel.kt`
- Create: `app/src/main/java/com/example/passwordsafe/ui/accounts/AccountsFragment.kt`

功能：搜索框 + 账号列表，使用 AccountAdapter 复用。

### Task 7.2: 账号详情模块

**Files:**
- Create: `app/src/main/res/layout/fragment_detail.xml`
- Create: `app/src/main/java/com/example/passwordsafe/ui/detail/DetailViewModel.kt`
- Create: `app/src/main/java/com/example/passwordsafe/ui/detail/DetailFragment.kt`

功能：显示账号详情（网站、URL、账号、密码、备注、分类）+ 复制账号/密码按钮 + 编辑按钮 + 删除按钮。

### Task 7.3: 添加/编辑模块

**Files:**
- Create: `app/src/main/res/layout/fragment_add_edit.xml`
- Create: `app/src/main/res/layout/dialog_password_generator.xml`
- Create: `app/src/main/java/com/example/passwordsafe/ui/addedit/AddEditViewModel.kt`
- Create: `app/src/main/java/com/example/passwordsafe/ui/addedit/AddEditFragment.kt`
- Create: `app/src/main/java/com/example/passwordsafe/ui/addedit/PasswordGeneratorDialog.kt`

功能：表单输入 + 密码强度检测 + 密码生成器对话框。

---

## Phase 8: 设置模块

### Task 8.1: 设置页面

**Files:**
- Create: `app/src/main/res/layout/fragment_settings.xml`
- Create: `app/src/main/java/com/example/passwordsafe/ui/settings/SettingsViewModel.kt`
- Create: `app/src/main/java/com/example/passwordsafe/ui/settings/SettingsFragment.kt`

- [ ] **Step 1: 创建 fragment_settings.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android" xmlns:app="http://schemas.android.com/apk/res-auto" android:layout_width="match_parent" android:layout_height="match_parent" android:padding="16dp">

    <TextView android:id="@+id/tv_security_title" android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="@string/security_status" android:textSize="18sp" android:textStyle="bold" app:layout_constraintTop_toTopOf="parent" app:layout_constraintStart_toStartOf="parent"/>

    <com.google.android.material.switchmaterial.SwitchMaterial android:id="@+id/switch_password" android:layout_width="match_parent" android:layout_height="wrap_content" android:text="@string/password_verification" android:layout_marginTop="16dp" app:layout_constraintTop_toBottomOf="@id/tv_security_title"/>

    <com.google.android.material.switchmaterial.SwitchMaterial android:id="@+id/switch_biometric" android:layout_width="match_parent" android:layout_height="wrap_content" android:text="@string/biometric_verification" android:layout_marginTop="8dp" app:layout_constraintTop_toBottomOf="@id/switch_password"/>

    <Button android:id="@+id/btn_change_password" android:layout_width="match_parent" android:layout_height="wrap_content" android:text="@string/change_password" style="@style/Widget.MaterialComponents.Button.OutlinedButton" android:layout_marginTop="16dp" app:layout_constraintTop_toBottomOf="@id/switch_biometric"/>

    <TextView android:id="@+id/tv_auto_lock_label" android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="@string/auto_lock" android:layout_marginTop="24dp" app:layout_constraintTop_toBottomOf="@id/btn_change_password" app:layout_constraintStart_toStartOf="parent"/>

    <Spinner android:id="@+id/spinner_auto_lock" android:layout_width="match_parent" android:layout_height="wrap_content" android:layout_marginTop="8dp" app:layout_constraintTop_toBottomOf="@id/tv_auto_lock_label"/>

    <View android:layout_width="match_parent" android:layout_height="1dp" android:background="@color/gray_light" android:layout_marginTop="24dp" app:layout_constraintTop_toBottomOf="@id/spinner_auto_lock"/>

    <Button android:id="@+id/btn_export" android:layout_width="match_parent" android:layout_height="wrap_content" android:text="@string/export_excel" style="@style/Widget.MaterialComponents.Button.OutlinedButton" android:layout_marginTop="32dp" app:layout_constraintTop_toBottomOf="@id/spinner_auto_lock"/>

    <Button android:id="@+id/btn_import" android:layout_width="match_parent" android:layout_height="wrap_content" android:text="@string/import_excel" style="@style/Widget.MaterialComponents.Button.OutlinedButton" android:layout_marginTop="8dp" app:layout_constraintTop_toBottomOf="@id/btn_export"/>

    <Button android:id="@+id/btn_copy_text" android:layout_width="match_parent" android:layout_height="wrap_content" android:text="@string/copy_as_text" style="@style/Widget.MaterialComponents.Button.OutlinedButton" android:layout_marginTop="8dp" app:layout_constraintTop_toBottomOf="@id/btn_import"/>

    <Button android:id="@+id/btn_reset" android:layout_width="match_parent" android:layout_height="wrap_content" android:text="@string/reset_app" android:textColor="@color/red" style="@style/Widget.MaterialComponents.Button.TextButton" android:layout_marginTop="24dp" app:layout_constraintTop_toBottomOf="@id/btn_copy_text"/>

</androidx.constraintlayout.widget.ConstraintLayout>
```

- [ ] **Step 2: 创建 SettingsViewModel**

```kotlin
package com.example.passwordsafe.ui.settings

import android.content.Context
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.passwordsafe.R
import com.example.passwordsafe.data.repository.AccountRepository
import com.example.passwordsafe.data.repository.SettingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingRepository: SettingRepository,
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val _passwordEnabled = MutableStateFlow(false)
    val passwordEnabled: StateFlow<Boolean> = _passwordEnabled

    private val _biometricEnabled = MutableStateFlow(false)
    val biometricEnabled: StateFlow<Boolean> = _biometricEnabled

    private val _autoLockTimeout = MutableStateFlow(60000L)
    val autoLockTimeout: StateFlow<Long> = _autoLockTimeout

    init { loadSettings() }

    private fun loadSettings() {
        viewModelScope.launch {
            _passwordEnabled.value = settingRepository.isPasswordEnabled()
            _biometricEnabled.value = settingRepository.isBiometricEnabled()
            _autoLockTimeout.value = settingRepository.getAutoLockTimeout()
        }
    }

    fun setPasswordEnabled(enabled: Boolean) {
        viewModelScope.launch { settingRepository.setPasswordEnabled(enabled); _passwordEnabled.value = enabled }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch { settingRepository.setBiometricEnabled(enabled); _biometricEnabled.value = enabled }
    }

    fun setAutoLockTimeout(timeout: Long) {
        viewModelScope.launch { settingRepository.setAutoLockTimeout(timeout); _autoLockTimeout.value = timeout }
    }

    suspend fun exportToExcel(context: Context): File? {
        return try {
            val accounts = accountRepository.getAllAccountsList()
            val workbook: Workbook = XSSFWorkbook()
            val sheet = workbook.createSheet("Accounts")
            val header = sheet.createRow(0)
            listOf("网站", "URL", "账号", "密码", "备注", "分类", "创建时间").forEachIndexed { i, t -> header.createCell(i).setCellValue(t) }
            accounts.forEachIndexed { i, acc ->
                val row = sheet.createRow(i + 1)
                row.createCell(0).setCellValue(acc.website)
                row.createCell(1).setCellValue(acc.websiteUrl)
                row.createCell(2).setCellValue(acc.account)
                row.createCell(3).setCellValue(accountRepository.decryptPassword(acc.password))
                row.createCell(4).setCellValue(acc.notes)
                row.createCell(5).setCellValue(acc.category)
                row.createCell(6).setCellValue(acc.createdAt.toString())
            }
            val file = File(context.cacheDir, "passwords_${System.currentTimeMillis()}.xlsx")
            workbook.write(FileOutputStream(file))
            workbook.close()
            file
        } catch (e: Exception) { null }
    }

    suspend fun importFromExcel(file: File): Int {
        var count = 0
        try {
            val workbook = XSSFWorkbook(FileInputStream(file))
            val sheet = workbook.getSheetAt(0)
            for (i in 1..sheet.lastRowNum) {
                val row = sheet.getRow(i) ?: continue
                val website = row.getCell(0)?.stringCellValue ?: continue
                val url = row.getCell(1)?.stringCellValue ?: ""
                val account = row.getCell(2)?.stringCellValue ?: continue
                val password = row.getCell(3)?.stringCellValue ?: continue
                val notes = row.getCell(4)?.stringCellValue ?: ""
                val category = row.getCell(5)?.stringCellValue ?: ""
                accountRepository.addAccount(com.example.passwordsafe.data.model.Account(
                    website = website, websiteUrl = url, account = account, password = password,
                    notes = notes, category = category
                ))
                count++
            }
            workbook.close()
        } catch (e: Exception) { }
        return count
    }

    suspend fun getAccountsAsText(): String {
        val accounts = accountRepository.getAllAccountsList()
        return accounts.joinToString("\n-------------------\n") { acc ->
            "网站: ${acc.website}\nURL: ${acc.websiteUrl}\n账号: ${acc.account}\n密码: ${accountRepository.decryptPassword(acc.password)}\n备注: ${acc.notes}\n分类: ${acc.category}"
        }
    }

    fun resetApp() {
        viewModelScope.launch {
            accountRepository.deleteAllAccounts()
            settingRepository.resetAllSettings()
        }
    }
}
```

- [ ] **Step 3: 创建 SettingsFragment**

```kotlin
package com.example.passwordsafe.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.passwordsafe.R
import com.example.passwordsafe.databinding.FragmentSettingsBinding
import com.example.passwordsafe.util.BiometricHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by viewModels()

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { uri ->
        uri?.let {
            lifecycleScope.launch {
                val file = viewModel.exportToExcel(requireContext())
                file?.inputStream()?.copyTo(requireContext().contentResolver.openOutputStream(uri)!!)
                Toast.makeText(requireContext(), R.string.export_success, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            lifecycleScope.launch {
                val tempFile = File.createTempFile("import", ".xlsx", requireContext().cacheDir)
                requireContext().contentResolver.openInputStream(uri)?.copyTo(tempFile.outputStream())
                val count = viewModel.importFromExcel(tempFile)
                Toast.makeText(requireContext(), getString(R.string.import_success, count), Toast.LENGTH_SHORT).show()
                tempFile.delete()
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupSwitches()
        setupSpinner()
        setupButtons()
        observeData()
    }

    private fun setupSwitches() {
        binding.switchPassword.setOnCheckedChangeListener { _, isChecked -> viewModel.setPasswordEnabled(isChecked) }
        binding.switchBiometric.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && BiometricHelper.isBiometricAvailable(requireContext())) viewModel.setBiometricEnabled(true)
            else if (isChecked) { binding.switchBiometric.isChecked = false; Toast.makeText(requireContext(), "设备不支持生物识别", Toast.LENGTH_SHORT).show() }
            else viewModel.setBiometricEnabled(false)
        }
    }

    private fun setupSpinner() {
        val options = listOf(getString(R.string.lock_immediately), getString(R.string.lock_1min), getString(R.string.lock_5min), getString(R.string.lock_15min))
        val values = listOf(0L, 60000L, 300000L, 900000L)
        binding.spinnerAutoLock.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, options)
        binding.spinnerAutoLock.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>, view: View?, position: Int, id: Long) {
                viewModel.setAutoLockTimeout(values[position])
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>) {}
        }
    }

    private fun setupButtons() {
        binding.btnExport.setOnClickListener { showExportWarning() }
        binding.btnImport.setOnClickListener { importLauncher.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) }
        binding.btnCopyText.setOnClickListener { showCopyWarning() }
        binding.btnReset.setOnClickListener { showResetConfirmation() }
    }

    private fun showExportWarning() {
        AlertDialog.Builder(requireContext()).setTitle(R.string.security_warning).setMessage(R.string.export_warning)
            .setPositiveButton(android.R.string.ok) { _, _ -> exportLauncher.launch("passwords.xlsx") }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun showCopyWarning() {
        AlertDialog.Builder(requireContext()).setTitle(R.string.security_warning).setMessage(R.string.export_warning)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                lifecycleScope.launch {
                    val text = viewModel.getAccountsAsText()
                    val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("passwords", text))
                    Toast.makeText(requireContext(), R.string.copied, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun showResetConfirmation() {
        AlertDialog.Builder(requireContext()).setMessage(R.string.confirm_reset)
            .setPositiveButton(android.R.string.ok) { _, _ -> viewModel.resetApp() }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.passwordEnabled.collect { binding.switchPassword.isChecked = it } }
                launch { viewModel.biometricEnabled.collect { binding.switchBiometric.isChecked = it } }
                launch { viewModel.autoLockTimeout.collect {
                    val values = listOf(0L, 60000L, 300000L, 900000L)
                    binding.spinnerAutoLock.setSelection(values.indexOf(it).coerceAtLeast(0))
                } }
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
```

---

## Phase 9: 更新导航和 MainActivity

### Task 9.1: 更新导航图

**Files:**
- Modify: `app/src/main/res/navigation/mobile_navigation.xml`
- Modify: `app/src/main/res/menu/bottom_nav_menu.xml`
- Modify: `app/src/main/java/com/example/passwordsafe/MainActivity.kt`

- [ ] **Step 1: 更新 mobile_navigation.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<navigation xmlns:android="http://schemas.android.com/apk/res/android" xmlns:app="http://schemas.android.com/apk/res-auto" xmlns:tools="http://schemas.android.com/tools" android:id="@+id/mobile_navigation" app:startDestination="@+id/navigation_auth">

    <fragment android:id="@+id/navigation_auth" android:name="com.example.passwordsafe.ui.auth.AuthFragment" tools:layout="@layout/fragment_auth">
        <action android:id="@+id/action_auth_to_setupPassword" app:destination="@id/navigation_setup_password"/>
        <action android:id="@+id/action_auth_to_home" app:destination="@id/navigation_home" app:popUpTo="@id/navigation_auth" app:popUpToInclusive="true"/>
    </fragment>

    <fragment android:id="@+id/navigation_setup_password" android:name="com.example.passwordsafe.ui.auth.SetupPasswordFragment" tools:layout="@layout/fragment_setup_password">
        <action android:id="@+id/action_setupPassword_to_home" app:destination="@id/navigation_home" app:popUpTo="@id/navigation_auth" app:popUpToInclusive="true"/>
    </fragment>

    <fragment android:id="@+id/navigation_home" android:name="com.example.passwordsafe.ui.home.HomeFragment" android:label="@string/title_home" tools:layout="@layout/fragment_home">
        <action android:id="@+id/action_home_to_addEdit" app:destination="@id/navigation_add_edit"/>
        <action android:id="@+id/action_home_to_accounts" app:destination="@id/navigation_accounts"/>
        <action android:id="@+id/action_home_to_detail" app:destination="@id/navigation_detail">
            <argument android:name="accountId" app:argType="long"/>
        </action>
    </fragment>

    <fragment android:id="@+id/navigation_accounts" android:name="com.example.passwordsafe.ui.accounts.AccountsFragment" android:label="@string/title_accounts" tools:layout="@layout/fragment_accounts">
        <action android:id="@+id/action_accounts_to_detail" app:destination="@id/navigation_detail">
            <argument android:name="accountId" app:argType="long"/>
        </action>
    </fragment>

    <fragment android:id="@+id/navigation_detail" android:name="com.example.passwordsafe.ui.detail.DetailFragment" android:label="@string/title_detail" tools:layout="@layout/fragment_detail">
        <argument android:name="accountId" app:argType="long"/>
        <action android:id="@+id/action_detail_to_addEdit" app:destination="@id/navigation_add_edit">
            <argument android:name="accountId" app:argType="long"/>
        </action>
    </fragment>

    <fragment android:id="@+id/navigation_add_edit" android:name="com.example.passwordsafe.ui.addedit.AddEditFragment" android:label="@string/title_add_account" tools:layout="@layout/fragment_add_edit">
        <argument android:name="accountId" app:argType="long" android:defaultValue="-1L"/>
    </fragment>

    <fragment android:id="@+id/navigation_settings" android:name="com.example.passwordsafe.ui.settings.SettingsFragment" android:label="@string/title_settings" tools:layout="@layout/fragment_settings"/>

</navigation>
```

- [ ] **Step 2: 更新 bottom_nav_menu.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<menu xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:id="@+id/navigation_home" android:icon="@drawable/ic_home_black_24dp" android:title="@string/title_home" />
    <item android:id="@+id/navigation_settings" android:icon="@drawable/ic_settings" android:title="@string/title_settings" />
</menu>
```

- [ ] **Step 3: 更新 MainActivity.kt**

```kotlin
package com.example.passwordsafe

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.passwordsafe.databinding.ActivityMainBinding
import com.google.android.material.bottomnavigation.BottomNavigationView
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        val appBarConfiguration = AppBarConfiguration(setOf(R.id.navigation_home, R.id.navigation_settings))
        setupActionBarWithNavController(navController, appBarConfiguration)
        binding.navView.setupWithNavController(navController)
    }
}
```

---

## 执行顺序总结

1. **Phase 1**: 项目基础设施（Gradle依赖、Hilt配置、AppModule）
2. **Phase 2**: 数据层（Model、DAO、Database、CryptoManager、Repository）
3. **Phase 3**: 工具类（PasswordStrengthChecker、PasswordGenerator、BiometricHelper、AutoLockManager）
4. **Phase 4**: 资源文件（Drawable、Strings、Colors）
5. **Phase 5**: 验证模块（AuthFragment、SetupPasswordFragment、AuthViewModel）
6. **Phase 6**: 首页模块（HomeFragment、HomeViewModel、AccountAdapter）
7. **Phase 7**: 账号列表、详情、添加编辑模块
8. **Phase 8**: 设置模块（SettingsFragment、SettingsViewModel、导入导出）
9. **Phase 9**: 导航和 MainActivity 更新

每个 Phase 应独立完成并验证构建成功后再进行下一个。