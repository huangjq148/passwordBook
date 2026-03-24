package com.example.passwordsafe.ui.accounts

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.method.PasswordTransformationMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
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
import com.example.passwordsafe.databinding.DialogPasswordGeneratorBinding
import com.example.passwordsafe.databinding.FragmentAddEditAccountBinding
import com.example.passwordsafe.util.PasswordStrength
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * 添加/编辑账号 Fragment
 * 支持账号信息输入、密码生成器、密码强度检测
 */
@AndroidEntryPoint
class AddEditAccountFragment : Fragment() {

    private var _binding: FragmentAddEditAccountBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AddEditAccountViewModel by viewModels()
    private val args: AddEditAccountFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddEditAccountBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupToolbar()
        setupForm()
        setupCategoryChips()
        setupPasswordGenerator()
        setupSaveButton()
        observeViewModel()
        
        // 加载账号数据（编辑模式）
        viewModel.loadAccount(args.accountId)
    }

    /**
     * 设置工具栏
     */
    private fun setupToolbar() {
        // 根据模式设置标题
        if (args.accountId > 0) {
            binding.tvTitle.text = getString(R.string.title_edit_account)
        } else {
            binding.tvTitle.text = getString(R.string.title_add_account)
        }

        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    /**
     * 设置表单
     */
    private fun setupForm() {
        // 网站名称
        binding.etWebsite.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.updateWebsite(s?.toString()?.trim() ?: "")
            }
        })
        setupClearButton(binding.etWebsite)

        // 网站URL
        binding.etWebsiteUrl.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.updateWebsiteUrl(s?.toString()?.trim() ?: "")
            }
        })
        setupClearButton(binding.etWebsiteUrl)

        // 账号
        binding.etAccount.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.updateAccount(s?.toString()?.trim() ?: "")
            }
        })
        setupClearButton(binding.etAccount)

        // 密码
        binding.etPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.updatePassword(s?.toString() ?: "")
            }
        })

        // 密码显示/隐藏切换
        binding.tilPassword.setEndIconOnClickListener {
            viewModel.togglePasswordVisibility()
        }

        // 备注
        binding.etNotes.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.updateNotes(s?.toString() ?: "")
            }
        })
        setupClearButton(binding.etNotes)
    }

    private fun setupClearButton(editText: EditText) {
        val clearDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.ic_close)?.mutate()
            ?: return
        clearDrawable.setBounds(0, 0, clearDrawable.intrinsicWidth, clearDrawable.intrinsicHeight)

        fun updateClearIcon(hasText: Boolean) {
            editText.setCompoundDrawablesRelative(
                null,
                null,
                if (hasText) clearDrawable else null,
                null
            )
        }

        updateClearIcon(!editText.text.isNullOrEmpty())

        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateClearIcon(!s.isNullOrEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        editText.setOnTouchListener { _, event ->
            val drawable = editText.compoundDrawablesRelative[2] ?: return@setOnTouchListener false
            if (event.rawX >= editText.right - drawable.bounds.width() - editText.paddingEnd - 40) {
                if (!editText.text.isNullOrEmpty()) {
                    editText.text?.clear()
                    return@setOnTouchListener true
                }
            }
            false
        }
    }

    /**
     * 设置分类 Chip
     */
    private fun setupCategoryChips() {
        viewModel.categories.forEach { (key, labelRes) ->
            val chip = Chip(requireContext()).apply {
                text = getString(labelRes)
                isCheckable = true
                isChecked = key.isEmpty()
                chipStrokeWidth = 1f
                chipCornerRadius = 20f
                chipStrokeColor = ContextCompat.getColorStateList(requireContext(), R.color.chip_category_stroke)
                setChipBackgroundColorResource(R.color.chip_category_bg)
                setTextColor(ContextCompat.getColorStateList(requireContext(), R.color.chip_category_text))
                setOnClickListener {
                    viewModel.updateCategory(key)
                }
            }
            binding.chipGroupCategory.addView(chip)
        }
    }

    /**
     * 设置密码生成器按钮
     */
    private fun setupPasswordGenerator() {
        binding.btnGeneratePassword.setOnClickListener {
            showPasswordGeneratorDialog()
        }
    }

    /**
     * 显示密码生成器对话框
     */
    private fun showPasswordGeneratorDialog() {
        val dialog = BottomSheetDialog(requireContext())
        val dialogBinding = DialogPasswordGeneratorBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        // 初始化生成密码
        viewModel.generatePassword()
        dialogBinding.tvGeneratedPassword.text = viewModel.generatedPassword.value

        // 设置密码长度滑块
        val settings = viewModel.generatorSettings.value
        dialogBinding.sliderLength.value = settings.length.toFloat()
        dialogBinding.tvLengthValue.text = settings.length.toString()

        // 设置复选框
        dialogBinding.cbUppercase.isChecked = settings.includeUppercase
        dialogBinding.cbLowercase.isChecked = settings.includeLowercase
        dialogBinding.cbDigits.isChecked = settings.includeDigits
        dialogBinding.cbSpecial.isChecked = settings.includeSpecial

        // 更新密码强度
        updateDialogPasswordStrength(dialogBinding, viewModel.generatedPasswordStrength.value)

        // 滑块监听
        dialogBinding.sliderLength.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                dialogBinding.tvLengthValue.text = value.toInt().toString()
                updateGeneratorSettings(dialogBinding)
            }
        }

        // 复选框监听
        val checkboxListener = View.OnClickListener {
            updateGeneratorSettings(dialogBinding)
        }
        dialogBinding.cbUppercase.setOnClickListener(checkboxListener)
        dialogBinding.cbLowercase.setOnClickListener(checkboxListener)
        dialogBinding.cbDigits.setOnClickListener(checkboxListener)
        dialogBinding.cbSpecial.setOnClickListener(checkboxListener)

        // 重新生成按钮
        dialogBinding.btnRegenerate.setOnClickListener {
            val password = viewModel.generatePassword()
            dialogBinding.tvGeneratedPassword.text = password
            updateDialogPasswordStrength(dialogBinding, viewModel.generatedPasswordStrength.value)
        }

        // 复制按钮
        dialogBinding.tvGeneratedPassword.setOnLongClickListener {
            val password = dialogBinding.tvGeneratedPassword.text.toString()
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("password", password)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(requireContext(), R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
            true
        }

        // 取消按钮
        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        // 使用密码按钮
        dialogBinding.btnUsePassword.setOnClickListener {
            val password = dialogBinding.tvGeneratedPassword.text.toString()
            viewModel.useGeneratedPassword(password)
            binding.etPassword.setText(password)
            dialog.dismiss()
        }

        dialog.show()
    }

    /**
     * 更新生成器设置
     */
    private fun updateGeneratorSettings(dialogBinding: DialogPasswordGeneratorBinding) {
        val settings = PasswordGeneratorSettings(
            length = dialogBinding.sliderLength.value.toInt(),
            includeUppercase = dialogBinding.cbUppercase.isChecked,
            includeLowercase = dialogBinding.cbLowercase.isChecked,
            includeDigits = dialogBinding.cbDigits.isChecked,
            includeSpecial = dialogBinding.cbSpecial.isChecked
        )
        viewModel.updateGeneratorSettings(settings)
        dialogBinding.tvGeneratedPassword.text = viewModel.generatedPassword.value
        updateDialogPasswordStrength(dialogBinding, viewModel.generatedPasswordStrength.value)
    }

    /**
     * 更新对话框密码强度显示
     */
    private fun updateDialogPasswordStrength(
        dialogBinding: DialogPasswordGeneratorBinding,
        strength: PasswordStrength
    ) {
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

        dialogBinding.tvPasswordStrength.text = text
        dialogBinding.tvPasswordStrength.setTextColor(colorInt)

        // 更新强度指示条
        val indicators = listOf(
            dialogBinding.strengthIndicator1,
            dialogBinding.strengthIndicator2,
            dialogBinding.strengthIndicator3
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
     * 设置保存按钮
     */
    private fun setupSaveButton() {
        binding.btnSave.setOnClickListener {
            viewModel.saveAccount()
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
    private fun updateUi(state: AddEditAccountUiState) {
        // 加载状态
        binding.progressBar.isVisible = state.isLoading

        // 表单数据（仅在加载时设置一次）
        if (state.isLoading) {
            // 不更新表单
        } else if (state.website.isNotEmpty() || state.account.isNotEmpty()) {
            if (binding.etWebsite.text?.toString() != state.website) {
                binding.etWebsite.setText(state.website)
            }
            if (binding.etWebsiteUrl.text?.toString() != state.websiteUrl) {
                binding.etWebsiteUrl.setText(state.websiteUrl)
            }
            if (binding.etAccount.text?.toString() != state.account) {
                binding.etAccount.setText(state.account)
            }
            if (binding.etPassword.text?.toString() != state.password) {
                binding.etPassword.setText(state.password)
            }
            if (binding.etNotes.text?.toString() != state.notes) {
                binding.etNotes.setText(state.notes)
            }
        }

        // 密码可见性
        updatePasswordVisibility(state.isPasswordVisible)

        // 密码强度
        updatePasswordStrength(state.passwordStrength)

        // 表单错误
        binding.tilWebsite.error = state.websiteError
        binding.tilAccount.error = state.accountError
        binding.tilPassword.error = state.passwordError

        // 保存状态
        binding.btnSave.isEnabled = !state.isSaving
        binding.progressBar.isVisible = state.isSaving

        // 保存成功
        if (state.isSaved) {
            findNavController().navigateUp()
        }

        // 错误信息
        state.errorMessage?.let { message ->
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            viewModel.clearError()
        }
    }

    /**
     * 更新密码可见性
     */
    private fun updatePasswordVisibility(isVisible: Boolean) {
        if (isVisible) {
            binding.etPassword.transformationMethod = null
            binding.tilPassword.endIconDrawable = ContextCompat.getDrawable(
                requireContext(),
                R.drawable.ic_visibility_off
            )
        } else {
            binding.etPassword.transformationMethod = PasswordTransformationMethod.getInstance()
            binding.tilPassword.endIconDrawable = ContextCompat.getDrawable(
                requireContext(),
                R.drawable.ic_visibility
            )
        }
        // 保持光标在末尾
        binding.etPassword.setSelection(binding.etPassword.text?.length ?: 0)
    }

    /**
     * 更新密码强度显示
     */
    private fun updatePasswordStrength(strength: PasswordStrength) {
        val password = binding.etPassword.text?.toString() ?: ""
        
        if (password.isEmpty()) {
            binding.passwordStrengthLayout.isVisible = false
            return
        }

        binding.passwordStrengthLayout.isVisible = true

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
                    ContextCompat.getColor(requireContext(), R.color.gray)
                }
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
