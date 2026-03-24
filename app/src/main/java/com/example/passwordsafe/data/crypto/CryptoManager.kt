package com.example.passwordsafe.data.crypto

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 加密管理器
 * 使用 Android Keystore + AES-GCM 进行数据加密
 */
class CryptoManager(context: Context) {

    companion object {
        private const val TAG = "CryptoManager"
        private const val PREFS_NAME = "passwordsafe_encrypted_prefs"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val TAG_LENGTH = 128
        private const val IV_SIZE = 12
        private const val KEY_ALIAS = "passwordsafe_key"
    }

    private val keyAlias = KEY_ALIAS
    private val keyStore: KeyStore? = try {
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize KeyStore", e)
        null
    }
    
    private val sharedPrefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Log.e(TAG, "Failed to create EncryptedSharedPreferences, falling back to regular SharedPreferences", e)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * 加密数据
     * @param data 原始数据
     * @return 加密后的 Base64 编码字符串（IV + 加密数据）
     */
    fun encrypt(data: String): String {
        if (data.isEmpty()) return ""
        
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val secretKey = getOrCreateKey() ?: return data
            
            // Android Keystore 要求不提供 IV，让系统自动生成
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            
            // 获取系统生成的 IV
            val iv = cipher.iv
            
            // 执行加密
            val encryptedData = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
            
            // 将 IV 和加密数据合并，然后 Base64 编码
            val combined = iv + encryptedData
            android.util.Base64.encodeToString(combined, android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Encryption failed", e)
            data
        }
    }

    /**
     * 解密数据
     * @param encryptedData 加密的 Base64 编码字符串
     * @return 解密后的原始数据
     */
    fun decrypt(encryptedData: String): String {
        if (encryptedData.isEmpty()) return ""
        
        return try {
            val combined = android.util.Base64.decode(encryptedData, android.util.Base64.NO_WRAP)
            
            // 从合并数据中提取 IV 和加密数据
            val iv = combined.copyOfRange(0, IV_SIZE)
            val cipherText = combined.copyOfRange(IV_SIZE, combined.size)
            
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val secretKey = getOrCreateKey() ?: return encryptedData
            
            // 解密时需要提供 IV
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH, iv))
            val decryptedData = cipher.doFinal(cipherText)
            
            String(decryptedData, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Decryption failed", e)
            ""  // 返回空字符串表示解密失败
        }
    }

    /**
     * 获取或创建密钥
     */
    private fun getOrCreateKey(): SecretKey? {
        return try {
            val existingKey = keyStore?.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry
            existingKey?.secretKey ?: createKey()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get or create key", e)
            createKey()
        }
    }

    /**
     * 创建新密钥
     */
    private fun createKey(): SecretKey? {
        return try {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore"
            )
            
            val spec = KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)  // 确保使用随机 IV
                .build()
            
            keyGenerator.init(spec)
            keyGenerator.generateKey()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create key", e)
            null
        }
    }

    /**
     * 保存加密的偏好设置
     */
    fun saveEncryptedPreference(key: String, value: String) {
        sharedPrefs.edit().putString(key, encrypt(value)).apply()
    }

    /**
     * 获取解密的偏好设置
     */
    fun getDecryptedPreference(key: String, defaultValue: String = ""): String {
        val encrypted = sharedPrefs.getString(key, null) ?: return defaultValue
        return try {
            decrypt(encrypted)
        } catch (e: Exception) {
            defaultValue
        }
    }

    /**
     * 检查是否存在密钥
     */
    fun hasKey(): Boolean {
        return try {
            keyStore?.containsAlias(keyAlias) ?: false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 删除密钥（用于重置应用）
     */
    fun deleteKey() {
        try {
            keyStore?.deleteEntry(keyAlias)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete key", e)
        }
    }
}