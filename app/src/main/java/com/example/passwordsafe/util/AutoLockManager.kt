package com.example.passwordsafe.util

import android.app.Application
import android.os.Handler
import android.os.Looper
import com.example.passwordsafe.data.repository.SettingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 自动锁定管理器
 * 管理应用自动锁定逻辑
 */
@Singleton
class AutoLockManager @Inject constructor(
    private val application: Application,
    private val settingRepository: SettingRepository
) {
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private var lastInteractionTime: Long = System.currentTimeMillis()
    private var isLocked: Boolean = true
    private var isInBackground: Boolean = false
    
    // 缓存的自动锁定时间（分钟）
    private var cachedAutoLockTimeout: Long = 5L
    
    // 锁定回调
    private var onLockCallback: (() -> Unit)? = null
    
    // 自动锁定的 Runnable
    private val autoLockRunnable = Runnable {
        if (!isInBackground && shouldAutoLock()) {
            lock()
        }
    }
    
    /**
     * 设置锁定回调
     */
    fun setOnLockCallback(callback: () -> Unit) {
        onLockCallback = callback
    }
    
    /**
     * 初始化，加载设置
     */
    fun init() {
        scope.launch {
            cachedAutoLockTimeout = settingRepository.autoLockTimeout.first()
        }
    }
    
    /**
     * 用户交互时调用
     * 重置自动锁定计时器
     */
    fun onUserInteraction() {
        lastInteractionTime = System.currentTimeMillis()
        scheduleAutoLock()
    }
    
    /**
     * 应用进入后台时调用
     */
    fun onAppBackgrounded() {
        isInBackground = true
        handler.removeCallbacks(autoLockRunnable)
        
        // 如果设置了立即锁定，则直接锁定
        if (cachedAutoLockTimeout == 0L) {
            lock()
        }
    }
    
    /**
     * 应用进入前台时调用
     * @return 是否需要重新验证
     */
    fun onAppForegrounded(): Boolean {
        isInBackground = false
        
        // 重新加载设置
        scope.launch {
            cachedAutoLockTimeout = settingRepository.autoLockTimeout.first()
        }
        
        // 检查是否需要重新验证
        return if (shouldAutoLock()) {
            lock()
            true
        } else {
            scheduleAutoLock()
            false
        }
    }
    
    /**
     * 解锁应用
     */
    fun unlock() {
        isLocked = false
        lastInteractionTime = System.currentTimeMillis()
        scheduleAutoLock()
    }
    
    /**
     * 锁定应用
     */
    fun lock() {
        isLocked = true
        handler.removeCallbacks(autoLockRunnable)
        onLockCallback?.invoke()
    }
    
    /**
     * 检查应用是否已锁定
     */
    fun isAppLocked(): Boolean = isLocked
    
    /**
     * 检查是否应该自动锁定
     */
    private fun shouldAutoLock(): Boolean {
        if (isLocked) return false
        
        // -1 表示永不自动锁定
        if (cachedAutoLockTimeout == -1L) return false
        // 0 表示立即锁定（后台锁定已处理）
        if (cachedAutoLockTimeout == 0L) return false
        
        val elapsedTime = System.currentTimeMillis() - lastInteractionTime
        return elapsedTime >= cachedAutoLockTimeout * 60 * 1000 // 转换为毫秒
    }
    
    /**
     * 调度自动锁定
     */
    private fun scheduleAutoLock() {
        handler.removeCallbacks(autoLockRunnable)
        
        if (isLocked) return
        
        // -1 表示永不自动锁定
        if (cachedAutoLockTimeout == -1L) return
        
        // 0 表示立即锁定（后台锁定）
        if (cachedAutoLockTimeout == 0L) return
        
        // 设置自动锁定延时
        val delayMillis = cachedAutoLockTimeout * 60 * 1000L
        handler.postDelayed(autoLockRunnable, delayMillis)
    }
    
    /**
     * 获取剩余锁定时间（毫秒）
     * @return 剩余时间，如果不会自动锁定则返回 -1
     */
    fun getRemainingLockTime(): Long {
        if (cachedAutoLockTimeout == -1L) return -1
        if (cachedAutoLockTimeout == 0L) return 0
        
        val elapsedTime = System.currentTimeMillis() - lastInteractionTime
        val totalTime = cachedAutoLockTimeout * 60 * 1000L
        
        return (totalTime - elapsedTime).coerceAtLeast(0)
    }
    
    /**
     * 更新自动锁定时间设置
     */
    fun updateAutoLockTimeout(timeoutMinutes: Long) {
        cachedAutoLockTimeout = timeoutMinutes
        if (!isLocked) {
            scheduleAutoLock()
        }
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        handler.removeCallbacks(autoLockRunnable)
        onLockCallback = null
    }
}