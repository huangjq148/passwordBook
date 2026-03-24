package com.example.passwordsafe.ui.accounts

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.passwordsafe.R
import com.example.passwordsafe.data.model.Account
import com.example.passwordsafe.databinding.FragmentAccountDetailBinding
import com.example.passwordsafe.util.PasswordStrength
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * 账号详情 Fragment
 * 显示账号详情，支持编辑、删除、复制等功能
 */
@AndroidEntryPoint
class AccountDetailFragment : Fragment() {

    private var _binding: FragmentAccountDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AccountDetailViewModel by viewModels()
    private val args: AccountDetailFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAccountDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupToolbar()
        setupPasswordToggle()
        setupButtons()
        observeViewModel()
        
        // 加载账号数据
        viewModel.loadAccount(args.accountId)
    }

    /**
     * 设置工具栏
     */
    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnEdit.setOnClickListener {
            navigateToEdit()
        }
    }

    /**
     * 设置密码显示/隐藏切换
     */
    private fun setupPasswordToggle() {
        binding.btnTogglePassword.setOnClickListener {
            viewModel.togglePasswordVisibility()
        }
    }

    /**
     * 设置按钮
     */
    private fun setupButtons() {
        binding.btnCopyAccount.setOnClickListener {
            copyAccountToClipboard()
        }

        binding.btnCopyPassword.setOnClickListener {
            copyPasswordToClipboard()
        }

        binding.btnDelete.setOnClickListener {
            showDeleteConfirmation()
        }
    }

    /**
     * 观察 ViewModel 状态
     */
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    updateUi(state)
                }
            }
        }
    }

    /**
     * 更新 UI
     */
    private fun updateUi(state: AccountDetailUiState) {
        // 加载状态
        binding.progressBar.isVisible = state.isLoading

        // 账号数据
        state.account?.let { account ->
            displayAccountInfo(account, state)
        }

        // 密码可见性
        updatePasswordVisibility(state.isPasswordVisible, state.decryptedPassword)

        // 密码强度
        updatePasswordStrength(state.passwordStrength)

        // 删除成功
        if (state.isDeleted) {
            findNavController().navigateUp()
        }

        // 错误信息
        state.errorMessage?.let { message ->
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            viewModel.clearError()
        }
    }

    /**
     * 显示账号信息
     */
    private fun displayAccountInfo(account: Account, state: AccountDetailUiState) {
        // 头像（首字母）
        val firstChar = account.website.takeIf { it.isNotEmpty() }?.first() ?: '?'
        binding.tvAvatar.text = firstChar.toString().uppercase()

        // 网站名称
        binding.tvWebsite.text = account.website

        // 网站URL
        if (account.websiteUrl.isNotEmpty()) {
            binding.tvWebsiteUrl.isVisible = true
            binding.tvWebsiteUrl.text = account.websiteUrl
        } else {
            binding.tvWebsiteUrl.isVisible = false
        }

        // 分类
        if (account.category.isNotEmpty()) {
            binding.tvCategory.isVisible = true
            binding.tvCategory.text = getCategoryDisplayName(account.category)
        } else {
            binding.tvCategory.isVisible = false
        }

        // 账号
        binding.tvAccount.text = account.account

        // 密码
        if (state.isPasswordVisible) {
            binding.tvPassword.text = state.decryptedPassword
        } else {
            binding.tvPassword.text = "••••••••"
        }

        // 备注
        if (account.notes.isNotEmpty()) {
            binding.notesCard.isVisible = true
            binding.tvNotes.text = account.notes
        } else {
            binding.notesCard.isVisible = false
        }

        // 使用次数
        binding.tvUseCount.text = account.useCount.toString()

        // 最后使用时间
        binding.tvLastUsed.text = formatLastUsedTime(account.lastUsedAt)
    }

    /**
     * 更新密码可见性
     */
    private fun updatePasswordVisibility(isVisible: Boolean, password: String) {
        if (isVisible) {
            binding.tvPassword.text = password
            binding.btnTogglePassword.setImageResource(R.drawable.ic_visibility_off)
            binding.passwordStrengthLayout.isVisible = true
        } else {
            binding.tvPassword.text = "••••••••"
            binding.btnTogglePassword.setImageResource(R.drawable.ic_visibility)
            binding.passwordStrengthLayout.isVisible = false
        }
    }

    /**
     * 更新密码强度指示器
     */
    private fun updatePasswordStrength(strength: PasswordStrength) {
        val color = when (strength) {
            PasswordStrength.WEAK -> R.color.red
            PasswordStrength.MEDIUM -> R.color.orange
            PasswordStrength.STRONG -> R.color.green
        }
        val colorInt = ContextCompat.getColor(requireContext(), color)

        val text = when (strength) {
            PasswordStrength.WEAK -> getString(R.string.password_strength_weak)
            PasswordStrength.MEDIUM -> getString(R.string.password_strength_medium)
            PasswordStrength.STRONG -> getString(R.string.password_strength_strong)
        }

        binding.tvPasswordStrength.text = text
        binding.tvPasswordStrength.setTextColor(colorInt)

        // 更新强度指示条
        val indicators = listOf(
            binding.strengthIndicator1,
            binding.strengthIndicator2,
            binding.strengthIndicator3
        )

        indicators.forEachIndexed { index, view ->
            view.setBackgroundColor(
                if (index <= strength.ordinal) {
                    colorInt
                } else {
                    ContextCompat.getColor(requireContext(), R.color.border)
                }
            )
        }
    }

    /**
     * 格式化最后使用时间
     */
    private fun formatLastUsedTime(timestamp: Long): String {
        if (timestamp == 0L) return getString(R.string.never_used)
        
        val now = System.currentTimeMillis()
        return when {
            DateUtils.isToday(timestamp) -> getString(R.string.today)
            DateUtils.isToday(timestamp - DateUtils.DAY_IN_MILLIS) -> getString(R.string.yesterday)
            timestamp > now - 7 * DateUtils.DAY_IN_MILLIS -> getString(R.string.this_week)
            timestamp > now - 30 * DateUtils.DAY_IN_MILLIS -> getString(R.string.this_month)
            else -> DateUtils.getRelativeTimeSpanString(
                timestamp,
                now,
                DateUtils.DAY_IN_MILLIS
            ).toString()
        }
    }

    /**
     * 获取分类显示名称
     */
    private fun getCategoryDisplayName(category: String): String {
        return when (category) {
            "social" -> getString(R.string.category_social)
            "finance" -> getString(R.string.category_finance)
            "shopping" -> getString(R.string.category_shopping)
            "work" -> getString(R.string.category_work)
            "entertainment" -> getString(R.string.category_entertainment)
            else -> getString(R.string.category_other)
        }
    }

    /**
     * 复制账号到剪贴板
     */
    private fun copyAccountToClipboard() {
        val account = viewModel.uiState.value.account ?: return
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("account", account.account)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(requireContext(), R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
        
        viewModel.incrementUseCount()
    }

    /**
     * 复制密码到剪贴板
     */
    private fun copyPasswordToClipboard() {
        val password = viewModel.uiState.value.decryptedPassword
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("password", password)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(requireContext(), R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
        
        viewModel.incrementUseCount()
    }

    /**
     * 显示删除确认对话框
     */
    private fun showDeleteConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.confirm_delete_title)
            .setMessage(R.string.confirm_delete_message)
            .setNegativeButton(R.string.cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .setPositiveButton(R.string.delete) { _, _ ->
                viewModel.deleteAccount()
            }
            .show()
    }

    /**
     * 导航到编辑页面
     */
    private fun navigateToEdit() {
        val accountId = viewModel.uiState.value.account?.id ?: return
        val action = AccountDetailFragmentDirections
            .actionAccountDetailFragmentToAddAccountFragment(accountId)
        findNavController().navigate(action)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
