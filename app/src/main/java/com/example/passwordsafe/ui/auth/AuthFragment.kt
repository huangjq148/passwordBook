package com.example.passwordsafe.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.passwordsafe.R
import com.example.passwordsafe.databinding.FragmentAuthBinding
import com.example.passwordsafe.util.AutoLockManager
import com.example.passwordsafe.util.BiometricHelper
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 验证页面 Fragment
 * 支持密码验证和生物识别验证
 */
@AndroidEntryPoint
class AuthFragment : Fragment() {

    private var _binding: FragmentAuthBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AuthViewModel by viewModels()

    @Inject
    lateinit var biometricHelper: BiometricHelper

    @Inject
    lateinit var autoLockManager: AutoLockManager

    // 标记是否已经导航过，避免重复导航
    private var hasNavigated = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAuthBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        hasNavigated = false
        setupViews()
        applyWindowInsets()
        observeViewModel()
        observeNavigation()
    }

    override fun onResume() {
        super.onResume()
        checkBiometricAvailability()
    }

    private fun setupViews() {
        // 监听密码输入，更新按钮状态
        binding.etPassword.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                updateUnlockButtonState()
            }
        })

        // 解锁按钮点击
        binding.btnUnlock.setOnClickListener {
            val password = binding.etPassword.text.toString()
            viewModel.verifyPassword(password)
        }

        // 生物识别按钮点击
        binding.btnBiometric.setOnClickListener {
            showBiometricPrompt()
        }

        // 键盘回车键处理
        binding.etPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val password = binding.etPassword.text.toString()
                if (password.isNotEmpty()) {
                    viewModel.verifyPassword(password)
                }
                true
            } else {
                false
            }
        }

    }

    private fun updateUnlockButtonState() {
        val hasPassword = binding.etPassword.text?.isNotEmpty() == true
        val state = viewModel.uiState.value
        binding.btnUnlock.isEnabled = hasPassword && !state.isLoading
    }

    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.logoContainer) { view, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = statusBars.top + resources.getDimensionPixelSize(R.dimen.top_bar_safe_spacing)
            }
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.cardPassword) { view, insets ->
            val navigationBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.updatePadding(
                bottom = navigationBars.bottom + resources.getDimensionPixelSize(R.dimen.bottom_nav_content_spacing_settings)
            )
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.llBiometric) { view, insets ->
            val navigationBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = navigationBars.bottom + resources.getDimensionPixelSize(R.dimen.bottom_nav_content_spacing_settings)
            }
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
                    updateUnlockButtonState()

                    // 错误信息
                    if (!state.errorMessage.isNullOrEmpty()) {
                        binding.tvError.text = state.errorMessage
                        binding.tvError.isVisible = true
                    } else {
                        binding.tvError.isVisible = false
                    }

                    // 生物识别按钮可见性
                    binding.llBiometric.isVisible = state.showBiometric && state.biometricAvailable
                }
            }
        }
    }
    
    /**
     * 监听导航事件（一次性事件）
     */
    private fun observeNavigation() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (!state.isConfigReady) return@collect
                    if (hasNavigated) return@collect
                    
                    // 首次使用，导航到设置密码页面
                    if (state.isFirstTime && !state.isAuthenticated) {
                        hasNavigated = true
                        navigateToSetupPassword()
                    }
                    // 验证成功，导航到主页
                    else if (state.isAuthenticated) {
                        hasNavigated = true
                        navigateToHome()
                    }
                }
            }
        }
    }

    private fun checkBiometricAvailability() {
        val isAvailable = biometricHelper.isBiometricAvailable(requireContext())
        viewModel.setBiometricAvailable(isAvailable)
    }

    private fun showBiometricPrompt() {
        biometricHelper.showUnlockPrompt(
            activity = requireActivity(),
            onSuccess = {
                viewModel.onBiometricAuthenticated()
                autoLockManager.unlock()
            },
            onError = { error ->
                if (_binding != null) {
                    Snackbar.make(binding.root, error, Snackbar.LENGTH_LONG).show()
                }
            },
            onCancel = {
                // 用户取消生物识别，继续显示密码输入
            }
        )
    }

    private fun navigateToHome() {
        autoLockManager.unlock()
        findNavController().navigate(
            R.id.action_authFragment_to_navigation_home,
            null,
            androidx.navigation.NavOptions.Builder()
                .setPopUpTo(R.id.navigation_auth, true)
                .build()
        )
    }

    private fun navigateToSetupPassword() {
        findNavController().navigate(
            R.id.action_authFragment_to_setupPasswordFragment,
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
