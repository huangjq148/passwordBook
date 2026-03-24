package com.example.passwordsafe.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.passwordsafe.data.model.Account
import com.example.passwordsafe.data.model.AppSetting

/**
 * 应用数据库
 */
@Database(
    entities = [
        Account::class,
        AppSetting::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun accountDao(): AccountDao
    abstract fun settingDao(): SettingDao
}
