package com.example.passwordsafe.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.snackbar.Snackbar
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.passwordsafe.R
import com.example.passwordsafe.databinding.FragmentSetupPasswordBinding
import com.example.passwordsafe.util.AutoLockManager
import com.example.passwordsafe.util.BiometricHelper
import com.example.passwordsafe.util.PasswordStrength
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 设置密码页面 Fragment
 * 用于首次设置主密码
 */
@AndroidEntryPoint
class SetupPasswordFragment : Fragment() {

    private var _binding: FragmentSetupPasswordBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AuthViewModel by viewModels()

    @Inject
    lateinit var biometricHelper: BiometricHelper

    @Inject
    lateinit var autoLockManager: AutoLockManager
    
    private var biometricAvailable = false
    private var hasNavigated = false
    private var shouldEnableBiometricAfterSetup = false
    private var isHandlingSetupCompletion = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSetupPasswordBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        hasNavigated = false
        checkBiometricAvailability()
        setupViews()
        applyWindowInsets()
        observeViewModel()
    }
    
    private fun checkBiometricAvailability() {
        biometricAvailable = biometricHelper.isBiometricAvailable(requireContext())
        binding.llBiometricOption.isVisible = biometricAvailable
    }

    private fun setupViews() {
        // 监听密码输入，实时检测强度
        binding.etPassword.doAfterTextChanged { text ->
            val password = text?.toString() ?: ""
            
            // 显示/隐藏强度指示器
            binding.llStrength.isVisible = password.isNotEmpty()
            
            if (password.isNotEmpty()) {
                viewModel.checkPasswordStrength(password)
            }
            
            validateForm()
        }

        // 监听确认密码输入
        binding.etConfirmPassword.doAfterTextChanged {
            validateForm()
        }

        // 确认密码回车键处理
        binding.etConfirmPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptSetupPassword()
                true
            } else {
                false
            }
        }

        // 开始使用按钮点击
        binding.btnStart.setOnClickListener {
            attemptSetupPassword()
        }
    }

    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.scrollContent) { view, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navigationBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.updatePadding(
                top = statusBars.top + resources.getDimensionPixelSize(R.dimen.top_bar_safe_spacing) + resources.getDimensionPixelSize(R.dimen.activity_vertical_margin),
                bottom = navigationBars.bottom + resources.getDimensionPixelSize(R.dimen.bottom_nav_content_spacing_settings)
            )
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    // 加载状态
                    binding.progressBar.isVisible = state.isLoading
                    binding.btnStart.isEnabled = !state.isLoading && validateForm()

                    // 更新密码强度显示
                    updatePasswordStrength(state.passwordStrength)

                    // 错误信息
                    if (!state.errorMessage.isNullOrEmpty()) {
                        binding.tvError.text = state.errorMessage
                        binding.tvError.isVisible = true
                    } else {
                        binding.tvError.isVisible = false
                    }

                    if (state.setupCompleted && !isHandlingSetupCompletion) {
                        isHandlingSetupCompletion = true
                        handleSetupCompleted()
                    }

                    // 设置成功，导航到主页（只触发一次）
                    if (state.isAuthenticated && !hasNavigated) {
                        hasNavigated = true
                        navigateToHome()
                    }
                }
            }
        }
    }

    private fun updatePasswordStrength(strength: PasswordStrength) {
        // 更新颜色和文字
        val (colorRes, textRes) = when (strength) {
            PasswordStrength.WEAK -> R.color.error to R.string.password_strength_weak
            PasswordStrength.MEDIUM -> R.color.warning to R.string.password_strength_medium
            PasswordStrength.STRONG -> R.color.success to R.string.password_strength_strong
        }

        val color = ContextCompat.getColor(requireContext(), colorRes)
        
        // Update indicator colors based on strength
        val indicator1 = binding.strengthIndicator1
        val indicator2 = binding.strengthIndicator2
        val indicator3 = binding.strengthIndicator3
        
        // Reset all indicators
        val inactiveColor = ContextCompat.getColor(requireContext(), R.color.border)
        indicator1.setBackgroundColor(inactiveColor)
        indicator2.setBackgroundColor(inactiveColor)
        indicator3.setBackgroundColor(inactiveColor)
        
        // Set active indicators based on strength
        when (strength) {
            PasswordStrength.WEAK -> {
                indicator1.setBackgroundColor(color)
            }
            PasswordStrength.MEDIUM -> {
                indicator1.setBackgroundColor(color)
                indicator2.setBackgroundColor(color)
            }
            PasswordStrength.STRONG -> {
                indicator1.setBackgroundColor(color)
                indicator2.setBackgroundColor(color)
                indicator3.setBackgroundColor(color)
            }
        }
        
        binding.tvStrength.setTextColor(color)
        binding.tvStrength.text = getString(textRes)
    }

    private fun validateForm(): Boolean {
        val password = binding.etPassword.text.toString()
        val confirmPassword = binding.etConfirmPassword.text.toString()

        val isValid = password.length >= 8 && 
                      password == confirmPassword &&
                      confirmPassword.isNotEmpty()

        binding.btnStart.isEnabled = isValid
        return isValid
    }

    private fun attemptSetupPassword() {
        val password = binding.etPassword.text.toString()
        val confirmPassword = binding.etConfirmPassword.text.toString()
        
        // 获取生物识别开关状态
        val enableBiometric = binding.switchBiometric.isChecked && biometricAvailable
        shouldEnableBiometricAfterSetup = enableBiometric
        isHandlingSetupCompletion = false

        // 先隐藏错误
        binding.tvError.isVisible = false

        // 清除之前的错误
        viewModel.clearError()

        // 执行设置，同时设置生物识别选项
        val success = viewModel.setupPassword(
            password = password,
            confirmPassword = confirmPassword,
            authenticateImmediately = !enableBiometric
        )
        
        if (!success) {
            shouldEnableBiometricAfterSetup = false
            // 表单验证失败，错误信息会在 ViewModel 中设置
            // 这里不需要额外处理，观察者会更新 UI
        }
    }

    private fun handleSetupCompleted() {
        if (!shouldEnableBiometricAfterSetup) {
            viewModel.completeAuthenticationAfterSetup()
            return
        }

        biometricHelper.showBiometricPrompt(
            activity = requireActivity(),
            title = getString(R.string.biometric_enable_title),
            subtitle = getString(R.string.biometric_enable_subtitle),
            onSuccess = {
                viewModel.enableBiometric()
                viewModel.completeAuthenticationAfterSetup()
            },
            onError = { error ->
                Snackbar.make(binding.root, error, Snackbar.LENGTH_LONG).show()
                viewModel.completeAuthenticationAfterSetup()
            },
            onCancel = {
                viewModel.completeAuthenticationAfterSetup()
            }
        )
    }

    private fun navigateToHome() {
        autoLockManager.unlock()
        findNavController().navigate(
            R.id.action_setupPasswordFragment_to_navigation_home,
            null,
            androidx.navigation.NavOptions.Builder()
                .setPopUpTo(R.id.navigation_auth, true)
                .build()
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
