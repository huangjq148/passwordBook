package com.example.passwordsafe.di

import android.content.Context
import androidx.room.Room
import com.example.passwordsafe.data.crypto.CryptoManager
import com.example.passwordsafe.data.local.database.AccountDao
import com.example.passwordsafe.data.local.database.AppDatabase
import com.example.passwordsafe.data.local.database.SettingDao
import com.example.passwordsafe.data.repository.AccountRepository
import com.example.passwordsafe.data.repository.SettingRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt 依赖注入模块
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * 提供数据库实例
     */
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "passwordsafe_db"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    /**
     * 提供账号 DAO
     */
    @Provides
    fun provideAccountDao(database: AppDatabase): AccountDao {
        return database.accountDao()
    }

    /**
     * 提供设置 DAO
     */
    @Provides
    fun provideSettingDao(database: AppDatabase): SettingDao {
        return database.settingDao()
    }

    /**
     * 提供加密管理器
     */
    @Provides
    @Singleton
    fun provideCryptoManager(@ApplicationContext context: Context): CryptoManager {
        return CryptoManager(context)
    }

    /**
     * 提供账号仓库
     */
    @Provides
    @Singleton
    fun provideAccountRepository(
        accountDao: AccountDao,
        cryptoManager: CryptoManager
    ): AccountRepository {
        return AccountRepository(accountDao, cryptoManager)
    }

    /**
     * 提供设置仓库
     */
    @Provides
    @Singleton
    fun provideSettingRepository(
        settingDao: SettingDao,
        cryptoManager: CryptoManager
    ): SettingRepository {
        return SettingRepository(settingDao, cryptoManager)
    }
}
