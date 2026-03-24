package com.example.passwordsafe.util

/**
 * 密码强度检查器
 * 根据密码长度、字符类型等评估密码强度
 */
enum class PasswordStrength { WEAK, MEDIUM, STRONG }

object PasswordStrengthChecker {
    
    /**
     * 检查密码强度
     * @param password 要检查的密码
     * @return 密码强度等级
     */
    fun check(password: String): PasswordStrength {
        if (password.isEmpty()) return PasswordStrength.WEAK
        
        var score = 0
        
        // 长度检查
        if (password.length >= 8) score++
        if (password.length >= 12) score++
        
        // 字符类型检查
        if (password.any { it.isLowerCase() }) score++
        if (password.any { it.isUpperCase() }) score++
        if (password.any { it.isDigit() }) score++
        if (password.any { it in "!@#\$%^&*()_+-=[]{}|;:',.<>?/" }) score++
        
        return when {
            score <= 2 -> PasswordStrength.WEAK
            score <= 4 -> PasswordStrength.MEDIUM
            else -> PasswordStrength.STRONG
        }
    }
    
    /**
     * 获取强度显示文本
     */
    fun getStrengthText(strength: PasswordStrength): String = when (strength) {
        PasswordStrength.WEAK -> "弱"
        PasswordStrength.MEDIUM -> "中"
        PasswordStrength.STRONG -> "强"
    }
    
    /**
     * 获取强度颜色资源ID
     */
    fun getStrengthColorRes(strength: PasswordStrength): Int = when (strength) {
        PasswordStrength.WEAK -> android.R.color.holo_red_dark
        PasswordStrength.MEDIUM -> android.R.color.holo_orange_dark
        PasswordStrength.STRONG -> android.R.color.holo_green_dark
    }
    
    /**
     * 获取详细的密码强度建议
     */
    fun getStrengthSuggestions(password: String): List<String> {
        val suggestions = mutableListOf<String>()
        
        if (password.length < 8) {
            suggestions.add("密码长度至少8位")
        }
        if (!password.any { it.isLowerCase() }) {
            suggestions.add("添加小写字母")
        }
        if (!password.any { it.isUpperCase() }) {
            suggestions.add("添加大写字母")
        }
        if (!password.any { it.isDigit() }) {
            suggestions.add("添加数字")
        }
        if (!password.any { it in "!@#\$%^&*()_+-=[]{}|;:',.<>?/" }) {
            suggestions.add("添加特殊符号")
        }
        
        return suggestions
    }
}
