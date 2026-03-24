package com.example.passwordsafe.util

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.passwordsafe.R
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 生物识别助手
 * 封装指纹/面容识别功能
 */
@Singleton
class BiometricHelper @Inject constructor() {
    
    /**
     * 检查设备是否支持生物识别
     * @param context 上下文
     * @return 是否可用
     */
    fun isBiometricAvailable(context: Context): Boolean {
        val biometricManager = BiometricManager.from(context)
        return when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> true
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> false
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> false
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> false
            else -> false
        }
    }
    
    /**
     * 获取生物识别状态描述
     * @param context 上下文
     * @return 状态描述
     */
    fun getBiometricStatusText(context: Context): String {
        val biometricManager = BiometricManager.from(context)
        return when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> context.getString(R.string.biometric_available)
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> context.getString(R.string.biometric_not_supported)
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> context.getString(R.string.biometric_not_available)
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> context.getString(R.string.biometric_not_enrolled)
            else -> context.getString(R.string.biometric_unknown_error)
        }
    }
    
    /**
     * 显示生物识别验证对话框
     * @param activity FragmentActivity
     * @param title 标题
     * @param subtitle 副标题
     * @param onSuccess 成功回调
     * @param onError 错误回调
     */
    fun showBiometricPrompt(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onCancel: () -> Unit = {}
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        
        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }
                
                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    // 用户验证失败，但不取消对话框
                }
                
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    when (errorCode) {
                        BiometricPrompt.ERROR_USER_CANCELED,
                        BiometricPrompt.ERROR_CANCELED,
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON -> {
                            onCancel()
                        }
                        BiometricPrompt.ERROR_LOCKOUT -> {
                            onError(activity.getString(R.string.biometric_lockout))
                        }
                        BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> {
                            onError(activity.getString(R.string.biometric_lockout_permanent))
                        }
                        else -> {
                            onError(errString.toString())
                        }
                    }
                }
            }
        )
        
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(activity.getString(R.string.cancel))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
        
        biometricPrompt.authenticate(promptInfo)
    }
    
    /**
     * 显示生物识别验证对话框（用于密码库解锁）
     * @param activity FragmentActivity
     * @param onSuccess 成功回调
     * @param onError 错误回调
     * @param onCancel 取消回调
     */
    fun showUnlockPrompt(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onCancel: () -> Unit = {}
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        
        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }
                
                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                }
                
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    when (errorCode) {
                        BiometricPrompt.ERROR_USER_CANCELED,
                        BiometricPrompt.ERROR_CANCELED,
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON -> {
                            onCancel()
                        }
                        BiometricPrompt.ERROR_LOCKOUT -> {
                            onError(activity.getString(R.string.biometric_lockout))
                        }
                        BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> {
                            onError(activity.getString(R.string.biometric_lockout_permanent))
                        }
                        else -> {
                            onError(errString.toString())
                        }
                    }
                }
            }
        )
        
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(R.string.biometric_unlock_title))
            .setSubtitle(activity.getString(R.string.biometric_unlock_subtitle))
            .setNegativeButtonText(activity.getString(R.string.use_password))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
        
        biometricPrompt.authenticate(promptInfo)
    }
}
