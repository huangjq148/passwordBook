package com.example.passwordsafe.ui.settings

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.graphics.drawable.InsetDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.passwordsafe.R
import com.example.passwordsafe.databinding.DialogChangePasswordBinding
import com.example.passwordsafe.databinding.FragmentSettingsBinding
import com.example.passwordsafe.util.AutoLockManager
import com.example.passwordsafe.util.BiometricHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * 设置 Fragment
 */
@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SettingsViewModel by viewModels()

    @Inject
    lateinit var biometricHelper: BiometricHelper

    @Inject
    lateinit var autoLockManager: AutoLockManager

    private var suppressBiometricListener = false

    // 导出文件选择器
    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        uri?.let { viewModel.exportToExcel(it) }
    }

    // 导入文件选择器
    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importFromExcel(it) }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews()
        applyWindowInsets()
        observeViewModel()
    }

    private fun setupViews() {
        // 生物识别开关
        binding.switchBiometric.setOnCheckedChangeListener { _, isChecked ->
            if (suppressBiometricListener) return@setOnCheckedChangeListener
            if (isChecked) {
                if (!viewModel.biometricAvailable.value) {
                    binding.switchBiometric.isChecked = false
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.settings_biometric_enable_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnCheckedChangeListener
                }
                biometricHelper.showBiometricPrompt(
                    activity = requireActivity(),
                    title = getString(R.string.biometric_enable_title),
                    subtitle = getString(R.string.biometric_enable_subtitle),
                    onSuccess = { viewModel.toggleBiometric(true) },
                    onError = { error ->
                        binding.switchBiometric.isChecked = false
                        Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
                    }
                )
            } else {
                viewModel.toggleBiometric(false)
            }
        }

        // 密码验证开关
        binding.llPassword.setOnClickListener {
            showChangePasswordDialog()
        }

        // 自动锁定时间点击
        binding.llAutoLock.setOnClickListener {
            showAutoLockDialog()
        }

        binding.llLockNow.setOnClickListener {
            autoLockManager.lock()
            findNavController().navigate(R.id.navigation_auth)
        }

        binding.llTheme.setOnClickListener {
            showThemeDialog()
        }

        // 导出点击
        binding.llExport.setOnClickListener {
            exportLauncher.launch("password_safe_${System.currentTimeMillis()}.xlsx")
        }

        // 导入点击
        binding.llImport.setOnClickListener {
            importLauncher.launch(arrayOf(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.ms-excel",
                "*/*"
            ))
        }

        // 复制为文本
        binding.llCopyText.setOnClickListener {
            copyAllAccountsAsText()
        }

        // 重置应用
        binding.llResetApp.setOnClickListener {
            showResetConfirmDialog()
        }

        // 设置版本号
        try {
            val packageInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            binding.tvVersion.text = "版本 ${packageInfo.versionName}"
        } catch (e: Exception) {
            binding.tvVersion.text = getString(R.string.settings_version)
        }
    }

    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.llHeader) { view, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.updatePadding(
                top = statusBars.top + resources.getDimensionPixelSize(R.dimen.top_bar_safe_spacing)
            )
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.scrollContent) { view, insets ->
            val navigationBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.updatePadding(
                bottom = navigationBars.bottom + resources.getDimensionPixelSize(R.dimen.bottom_nav_content_spacing_settings)
            )
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // 生物识别可用性
                launch {
                    viewModel.biometricAvailable.collect { available ->
                        binding.llBiometric.isEnabled = available
                        binding.switchBiometric.isEnabled = available
                        if (!available) {
                            binding.tvBiometricStatus.text = viewModel.getBiometricStatusText(
                                enabled = binding.switchBiometric.isChecked,
                                available = false
                            )
                        }
                    }
                }

                // 生物识别开关状态
                launch {
                    viewModel.biometricEnabled.collect { enabled ->
                        if (binding.switchBiometric.isChecked != enabled) {
                            suppressBiometricListener = true
                            binding.switchBiometric.isChecked = enabled
                            suppressBiometricListener = false
                        }
                        binding.tvBiometricStatus.text = viewModel.getBiometricStatusText(
                            enabled = enabled,
                            available = viewModel.biometricAvailable.value
                        )
                    }
                }

                // 密码验证开关状态
                launch {
                    viewModel.passwordEnabled.collect { enabled ->
                        binding.tvPasswordStatus.text = if (enabled) {
                            getString(R.string.settings_password_required)
                        } else {
                            getString(R.string.settings_change_password_summary)
                        }
                    }
                }

                // 自动锁定时间
                launch {
                    viewModel.autoLockTimeout.collect { timeout ->
                        updateAutoLockDisplay(timeout)
                    }
                }

                // 账号数量
                launch {
                    viewModel.accountCount.collect { count ->
                        binding.tvAccountCount.text = getString(R.string.account_count, count)
                    }
                }

                launch {
                    viewModel.lastBackupTime.collect { timestamp ->
                        binding.tvLastBackup.text = formatBackupTime(timestamp)
                    }
                }

                launch {
                    viewModel.themeMode.collect { mode ->
                        binding.tvThemeValue.text = getThemeDisplayText(mode)
                    }
                }

                // 加载状态
                launch {
                    viewModel.isLoading.collect { isLoading ->
                        binding.progressBar.isVisible = isLoading
                    }
                }

                // 消息
                launch {
                    viewModel.message.collect { message ->
                        message?.let {
                            Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                            viewModel.clearMessage()
                        }
                    }
                }

                // 导出结果
                launch {
                    viewModel.exportResult.collect { uri ->
                        uri?.let {
                            Toast.makeText(
                                requireContext(),
                                getString(R.string.export_success),
                                Toast.LENGTH_SHORT
                            ).show()
                            viewModel.clearExportResult()
                        }
                    }
                }

                // 导入结果
                launch {
                    viewModel.importResult.collect { count ->
                        if (count > 0) {
                            Toast.makeText(
                                requireContext(),
                                getString(R.string.import_success) + ": $count 个账号",
                                Toast.LENGTH_SHORT
                            ).show()
                            viewModel.clearImportResult()
                        }
                    }
                }
            }
        }
    }

    /**
     * 更新自动锁定时间显示
     */
    private fun updateAutoLockDisplay(timeoutMinutes: Long) {
        val displayText = when (timeoutMinutes.toInt()) {
            1 -> getString(R.string.auto_lock_1min)
            5 -> getString(R.string.auto_lock_5min)
            15 -> getString(R.string.auto_lock_15min)
            30 -> getString(R.string.auto_lock_30min)
            -1 -> getString(R.string.auto_lock_never)
            else -> getString(R.string.auto_lock_5min)
        }
        binding.tvAutoLockValue.text = if (timeoutMinutes == 0L) {
            getString(R.string.settings_lock_immediately)
        } else {
            "闲置 $displayText 后自动锁定"
        }
        autoLockManager.updateAutoLockTimeout(timeoutMinutes)
    }

    /**
     * 显示自动锁定时间选择对话框
     */
    private fun showAutoLockDialog() {
        val options = arrayOf(
            getString(R.string.auto_lock_immediately),
            getString(R.string.auto_lock_1min),
            getString(R.string.auto_lock_5min),
            getString(R.string.auto_lock_15min),
            getString(R.string.auto_lock_30min),
            getString(R.string.auto_lock_never)
        )
        val timeoutValues = listOf(0L, 1L, 5L, 15L, 30L, -1L)
        val currentTimeout = viewModel.autoLockTimeout.value
        val currentIndex = timeoutValues.indexOf(currentTimeout).takeIf { it >= 0 } ?: 1

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_auto_lock)
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                viewModel.setAutoLockTimeout(timeoutValues[which])
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /**
     * 复制所有账号为文本
     */
    private fun copyAllAccountsAsText() {
        viewLifecycleOwner.lifecycleScope.launch {
            val clipboardManager = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            
            // 构建文本内容
            val sb = StringBuilder()
            sb.append("=== 密码保险箱账号列表 ===\n\n")
            
            // 需要等待协程完成获取数据
            val accountsText = viewModel.copyAsText()
            sb.append(accountsText)
            
            val clip = ClipData.newPlainText("账号列表", sb.toString())
            clipboardManager.setPrimaryClip(clip)
            
            Toast.makeText(
                requireContext(),
                getString(R.string.copy_success),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * 显示重置确认对话框
     */
    private fun showResetConfirmDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.confirm_reset_title)
            .setMessage(R.string.confirm_reset_message)
            .setPositiveButton(R.string.reset) { _, _ ->
                viewModel.resetApp {
                    // 重置成功后重启应用
                    val intent = requireContext().packageManager
                        .getLaunchIntentForPackage(requireContext().packageName)
                    intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                    requireActivity().finish()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showChangePasswordDialog() {
        val dialogBinding = DialogChangePasswordBinding.inflate(layoutInflater)
        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.confirm, null)
            .show()
            .also { dialog ->
                dialog.window?.setBackgroundDrawable(
                    InsetDrawable(
                        requireContext().getDrawable(R.drawable.bg_dialog_modal),
                        32,
                        24,
                        32,
                        24
                    )
                )
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    viewLifecycleOwner.lifecycleScope.launch {
                        val result = viewModel.changePassword(
                            currentPassword = dialogBinding.etCurrentPassword.text?.toString().orEmpty().trim(),
                            newPassword = dialogBinding.etNewPassword.text?.toString().orEmpty(),
                            confirmPassword = dialogBinding.etConfirmPassword.text?.toString().orEmpty()
                        )

                        result.onSuccess {
                            Toast.makeText(
                                requireContext(),
                                getString(R.string.settings_change_password_success),
                                Toast.LENGTH_SHORT
                            ).show()
                            dialog.dismiss()
                        }.onFailure { error ->
                            Toast.makeText(
                                requireContext(),
                                error.message ?: getString(R.string.error_generic),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
    }

    private fun showThemeDialog() {
        val options = arrayOf(
            getString(R.string.theme_follow_system),
            getString(R.string.theme_light),
            getString(R.string.theme_dark)
        )
        val modes = listOf("system", "light", "dark")
        val currentIndex = modes.indexOf(viewModel.themeMode.value).takeIf { it >= 0 } ?: 0

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_theme)
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                val mode = modes[which]
                viewModel.setThemeMode(mode)
                applyThemeMode(mode)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun applyThemeMode(mode: String) {
        val nightMode = when (mode) {
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }

    private fun getThemeDisplayText(mode: String): String {
        return when (mode) {
            "light" -> getString(R.string.theme_light)
            "dark" -> getString(R.string.theme_dark)
            else -> getString(R.string.theme_follow_system)
        }
    }

    private fun formatBackupTime(timestamp: Long): String {
        if (timestamp <= 0L) {
            return getString(R.string.settings_last_backup_never)
        }
        val now = System.currentTimeMillis()
        if (now - timestamp < 60_000) {
            return getString(R.string.settings_last_backup_just_now)
        }
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return "${getString(R.string.settings_last_backup)}：${formatter.format(Date(timestamp))}"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
