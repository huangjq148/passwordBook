package com.example.passwordsafe.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.passwordsafe.R
import com.example.passwordsafe.data.model.Account
import com.example.passwordsafe.data.repository.AccountRepository
import com.example.passwordsafe.data.repository.SettingRepository
import com.example.passwordsafe.util.BiometricHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.FileOutputStream
import javax.inject.Inject

/**
 * 设置 ViewModel
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingRepository: SettingRepository,
    private val accountRepository: AccountRepository,
    private val biometricHelper: BiometricHelper,
    @ApplicationContext private val context: Context
) : ViewModel() {

    // 生物识别可用性
    private val _biometricAvailable = MutableStateFlow(false)
    val biometricAvailable: StateFlow<Boolean> = _biometricAvailable.asStateFlow()

    // 生物识别是否启用
    val biometricEnabled: StateFlow<Boolean> = settingRepository.biometricEnabled

    // 密码验证是否启用
    val passwordEnabled: StateFlow<Boolean> = settingRepository.passwordEnabled

    // 自动锁定时间
    val autoLockTimeout: StateFlow<Long> = settingRepository.autoLockTimeout

    // 最近备份时间
    private val _lastBackupTime = MutableStateFlow(0L)
    val lastBackupTime: StateFlow<Long> = _lastBackupTime.asStateFlow()

    private val _themeMode = MutableStateFlow("system")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    // 账号数量
    val accountCount: StateFlow<Int> = accountRepository.getAccountCount()
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    // 加载状态
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // 消息
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    // 导出结果
    private val _exportResult = MutableStateFlow<Uri?>(null)
    val exportResult: StateFlow<Uri?> = _exportResult.asStateFlow()

    // 导入结果
    private val _importResult = MutableStateFlow(0)
    val importResult: StateFlow<Int> = _importResult.asStateFlow()

    init {
        checkBiometricAvailability()
        loadBackupTime()
        loadThemeMode()
    }

    /**
     * 检查生物识别可用性
     */
    private fun checkBiometricAvailability() {
        _biometricAvailable.value = biometricHelper.isBiometricAvailable(context)
    }

    private fun loadThemeMode() {
        viewModelScope.launch {
            _themeMode.value = settingRepository.getThemeMode()
        }
    }

    /**
     * 切换生物识别开关
     */
    fun toggleBiometric(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled && !_biometricAvailable.value) {
                _message.value = "设备不支持生物识别"
                return@launch
            }
            settingRepository.setBiometricEnabled(enabled)
        }
    }

    /**
     * 切换密码验证开关
     */
    fun togglePassword(enabled: Boolean) {
        viewModelScope.launch {
            settingRepository.setPasswordEnabled(enabled)
        }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            settingRepository.setThemeMode(mode)
            _themeMode.value = mode
        }
    }

    /**
     * 设置自动锁定时间
     */
    fun setAutoLockTimeout(minutes: Long) {
        viewModelScope.launch {
            settingRepository.setAutoLockTimeout(minutes)
        }
    }

    /**
     * 导出到 Excel
     * @param uri 文件保存路径
     */
    fun exportToExcel(uri: Uri) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val accounts = accountRepository.getAllAccountsList()
                val workbook = createExcelWorkbook(accounts)
                
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    (outputStream as? FileOutputStream)?.let {
                        workbook.write(it)
                    } ?: run {
                        // 对于 ContentResolver 的输出流，使用 ByteArrayOutputStream
                        val tempStream = java.io.ByteArrayOutputStream()
                        workbook.write(tempStream)
                        outputStream.write(tempStream.toByteArray())
                    }
                }
                
                workbook.close()
                val now = System.currentTimeMillis()
                settingRepository.setLastBackupTime(now)
                _lastBackupTime.value = now
                _exportResult.value = uri
                _message.value = "导出成功：${accounts.size} 个账号"
            } catch (e: Exception) {
                e.printStackTrace()
                _message.value = "导出失败：${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 从 Excel 导入
     * @param uri 文件路径
     */
    fun importFromExcel(uri: Uri) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val accounts = parseExcelFile(uri)
                var importedCount = 0
                
                accounts.forEach { account ->
                    accountRepository.addAccount(account)
                    importedCount++
                }
                
                _importResult.value = importedCount
                _message.value = "导入成功：$importedCount 个账号"
            } catch (e: Exception) {
                e.printStackTrace()
                _message.value = "导入失败：${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 复制所有账号为文本
     */
    suspend fun copyAsText(): String {
        _isLoading.value = true
        return try {
            val accounts = accountRepository.getAllAccountsList()
            buildString {
                accounts.forEach { account ->
                    append("【${account.website}】\n")
                    append("账号：${account.account}\n")
                    append("密码：${account.password}\n")
                    if (account.websiteUrl.isNotEmpty()) {
                        append("网址：${account.websiteUrl}\n")
                    }
                    if (account.notes.isNotEmpty()) {
                        append("备注：${account.notes}\n")
                    }
                    if (account.category.isNotEmpty()) {
                        append("分类：${account.category}\n")
                    }
                    append("\n")
                }
            }
        } catch (e: Exception) {
            _message.value = "复制失败：${e.message}"
            ""
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * 重置应用
     */
    fun resetApp(onComplete: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                accountRepository.deleteAllAccounts()
                settingRepository.deleteAllSettings()
                settingRepository.setIsFirstTime(true)
                _message.value = "应用已重置"
                onComplete()
            } catch (e: Exception) {
                _message.value = "重置失败：${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 清除消息
     */
    fun clearMessage() {
        _message.value = null
    }

    /**
     * 清除导出结果
     */
    fun clearExportResult() {
        _exportResult.value = null
    }

    /**
     * 清除导入结果
     */
    fun clearImportResult() {
        _importResult.value = 0
    }

    fun getBiometricStatusText(enabled: Boolean, available: Boolean): String {
        return when {
            !available -> biometricHelper.getBiometricStatusText(context)
            enabled -> context.getString(R.string.settings_biometric_enabled)
            else -> context.getString(R.string.settings_biometric_disabled)
        }
    }

    suspend fun changePassword(
        currentPassword: String,
        newPassword: String,
        confirmPassword: String
    ): Result<Unit> {
        if (currentPassword.isBlank()) {
            return Result.failure(IllegalArgumentException("请输入当前主密码"))
        }
        if (newPassword.length < 8) {
            return Result.failure(IllegalArgumentException(context.getString(R.string.password_too_short_error)))
        }
        if (!newPassword.matches(Regex("^[A-Za-z0-9]+$"))) {
            return Result.failure(IllegalArgumentException(context.getString(R.string.password_alphanumeric_only_error)))
        }
        if (newPassword != confirmPassword) {
            return Result.failure(IllegalArgumentException(context.getString(R.string.password_mismatch_error)))
        }
        if (currentPassword == newPassword) {
            return Result.failure(IllegalArgumentException("新主密码不能与当前密码相同"))
        }

        return try {
            if (settingRepository.changeAppPassword(currentPassword, newPassword)) {
                Result.success(Unit)
            } else {
                Result.failure(IllegalArgumentException(context.getString(R.string.settings_wrong_current_password)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 创建 Excel 工作簿
     */
    private fun createExcelWorkbook(accounts: List<Account>): Workbook {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("账号列表")

        // 创建表头
        val headerRow = sheet.createRow(0)
        val headers = listOf("网站名称", "网站URL", "账号", "密码", "备注", "分类", "使用次数", "创建时间")
        headers.forEachIndexed { index, header ->
            headerRow.createCell(index).setCellValue(header)
        }

        // 填充数据
        accounts.forEachIndexed { index, account ->
            val row = sheet.createRow(index + 1)
            row.createCell(0).setCellValue(account.website)
            row.createCell(1).setCellValue(account.websiteUrl)
            row.createCell(2).setCellValue(account.account)
            row.createCell(3).setCellValue(account.password)
            row.createCell(4).setCellValue(account.notes)
            row.createCell(5).setCellValue(account.category)
            row.createCell(6).setCellValue(account.useCount.toDouble())
            row.createCell(7).setCellValue(formatTimestamp(account.createdAt))
        }

        // 自动调整列宽
        for (i in headers.indices) {
            sheet.autoSizeColumn(i)
        }

        return workbook
    }

    /**
     * 解析 Excel 文件
     */
    private fun parseExcelFile(uri: Uri): List<Account> {
        val accounts = mutableListOf<Account>()
        
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val workbook = XSSFWorkbook(inputStream)
            val sheet = workbook.getSheetAt(0)

            // 跳过表头，从第二行开始
            for (i in 1..sheet.lastRowNum) {
                val row = sheet.getRow(i) ?: continue
                
                val website = getCellValue(row.getCell(0))
                val websiteUrl = getCellValue(row.getCell(1))
                val account = getCellValue(row.getCell(2))
                val password = getCellValue(row.getCell(3))
                val notes = getCellValue(row.getCell(4))
                val category = getCellValue(row.getCell(5))

                if (website.isNotEmpty() && account.isNotEmpty() && password.isNotEmpty()) {
                    accounts.add(
                        Account(
                            website = website,
                            websiteUrl = websiteUrl,
                            account = account,
                            password = password,
                            notes = notes,
                            category = category
                        )
                    )
                }
            }

            workbook.close()
        }

        return accounts
    }

    /**
     * 获取单元格值
     */
    private fun getCellValue(cell: org.apache.poi.ss.usermodel.Cell?): String {
        return when (cell?.cellType) {
            org.apache.poi.ss.usermodel.CellType.STRING -> cell.stringCellValue ?: ""
            org.apache.poi.ss.usermodel.CellType.NUMERIC -> cell.numericCellValue.toLong().toString()
            org.apache.poi.ss.usermodel.CellType.BOOLEAN -> cell.booleanCellValue.toString()
            org.apache.poi.ss.usermodel.CellType.FORMULA -> cell.cellFormula ?: ""
            else -> ""
        }
    }

    /**
     * 格式化时间戳
     */
    private fun formatTimestamp(timestamp: Long): String {
        return try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            sdf.format(java.util.Date(timestamp))
        } catch (e: Exception) {
            timestamp.toString()
        }
    }

    private fun loadBackupTime() {
        viewModelScope.launch {
            _lastBackupTime.value = settingRepository.getLastBackupTime()
        }
    }
}
