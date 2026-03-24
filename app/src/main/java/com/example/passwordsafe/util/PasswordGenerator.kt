package com.example.passwordsafe.util

import java.security.SecureRandom

/**
 * 密码生成器
 * 使用 SecureRandom 生成安全的随机密码
 */
object PasswordGenerator {
    
    private const val LOWERCASE = "abcdefghijklmnopqrstuvwxyz"
    private const val UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val DIGITS = "0123456789"
    private const val SPECIAL = "!@#\$%^&*()_+-=[]{}|;:',.<>?/"
    
    private val secureRandom = SecureRandom()
    
    /**
     * 生成随机密码
     * @param length 密码长度 (8-32)
     * @param includeUppercase 是否包含大写字母
     * @param includeLowercase 是否包含小写字母
     * @param includeDigits 是否包含数字
     * @param includeSpecial 是否包含特殊符号
     * @return 生成的密码
     */
    fun generate(
        length: Int = 16,
        includeUppercase: Boolean = true,
        includeLowercase: Boolean = true,
        includeDigits: Boolean = true,
        includeSpecial: Boolean = true
    ): String {
        // 确保长度在有效范围内
        val validLength = length.coerceIn(8, 32)
        
        // 构建字符池
        val charPool = buildString {
            if (includeUppercase) append(UPPERCASE)
            if (includeLowercase) append(LOWERCASE)
            if (includeDigits) append(DIGITS)
            if (includeSpecial) append(SPECIAL)
        }
        
        // 如果没有选择任何字符类型，默认使用所有类型
        val finalPool = if (charPool.isEmpty()) {
            LOWERCASE + UPPERCASE + DIGITS + SPECIAL
        } else {
            charPool
        }
        
        // 生成密码
        val password = StringBuilder(validLength)
        
        // 确保每种选中的字符类型至少有一个字符
        if (includeUppercase) {
            password.append(UPPERCASE[secureRandom.nextInt(UPPERCASE.length)])
        }
        if (includeLowercase) {
            password.append(LOWERCASE[secureRandom.nextInt(LOWERCASE.length)])
        }
        if (includeDigits) {
            password.append(DIGITS[secureRandom.nextInt(DIGITS.length)])
        }
        if (includeSpecial) {
            password.append(SPECIAL[secureRandom.nextInt(SPECIAL.length)])
        }
        
        // 填充剩余长度
        val remainingLength = validLength - password.length
        for (i in 0 until remainingLength) {
            password.append(finalPool[secureRandom.nextInt(finalPool.length)])
        }
        
        // 打乱字符顺序
        return password.toString().toList().shuffled(secureRandom).joinToString("")
    }
    
    /**
     * 生成强密码 (包含所有字符类型，长度16)
     */
    fun generateStrong(): String {
        return generate(
            length = 16,
            includeUppercase = true,
            includeLowercase = true,
            includeDigits = true,
            includeSpecial = true
        )
    }
    
    /**
     * 生成中等强度密码 (只包含字母和数字，长度12)
     */
    fun generateMedium(): String {
        return generate(
            length = 12,
            includeUppercase = true,
            includeLowercase = true,
            includeDigits = true,
            includeSpecial = false
        )
    }
    
    /**
     * 生成简单密码 (只包含字母，长度8)
     */
    fun generateSimple(): String {
        return generate(
            length = 8,
            includeUppercase = true,
            includeLowercase = true,
            includeDigits = false,
            includeSpecial = false
        )
    }
}
