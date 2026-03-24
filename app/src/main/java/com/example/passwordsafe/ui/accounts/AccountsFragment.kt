package com.example.passwordsafe.ui.accounts

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.passwordsafe.R
import com.example.passwordsafe.data.model.Account
import com.example.passwordsafe.databinding.FragmentAccountsBinding
import com.example.passwordsafe.ui.home.AccountAdapter
import com.google.android.material.chip.Chip
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * 账号列表 Fragment
 * 显示所有账号列表，支持搜索和分类筛选
 */
@AndroidEntryPoint
class AccountsFragment : Fragment() {

    private var _binding: FragmentAccountsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AccountsViewModel by viewModels()
    private lateinit var accountAdapter: AccountAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAccountsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        setupSearch()
        setupCategoryChips()
        setupFab()
        applyWindowInsets()
        observeViewModel()
    }

    /**
     * 设置 RecyclerView
     */
    private fun setupRecyclerView() {
        accountAdapter = AccountAdapter(
            onItemClickListener = { account ->
                navigateToDetail(account)
            },
            onCopyClickListener = { account ->
                copyPasswordToClipboard(account)
            }
        )
        
        binding.rvAccounts.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = accountAdapter
            setHasFixedSize(true)
        }
    }

    /**
     * 设置搜索
     */
    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim() ?: ""
                viewModel.search(query)
                binding.btnClearSearch.isVisible = query.isNotEmpty()
            }
        })

        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = binding.etSearch.text?.toString()?.trim() ?: ""
                viewModel.search(query)
                true
            } else {
                false
            }
        }

        binding.btnClearSearch.setOnClickListener {
            binding.etSearch.text?.clear()
            viewModel.clearSearch()
        }
    }

    /**
     * 设置分类筛选 Chip
     */
    private fun setupCategoryChips() {
        // 添加"全部"选项
        val allChip = Chip(requireContext()).apply {
            text = getString(R.string.category_all)
            isCheckable = true
            isChecked = true
            chipStrokeWidth = 1f
            chipCornerRadius = 20f
            chipStrokeColor = ContextCompat.getColorStateList(requireContext(), R.color.chip_category_stroke)
            setChipBackgroundColorResource(R.color.chip_category_bg)
            setTextColor(ContextCompat.getColorStateList(requireContext(), R.color.chip_category_text))
            setOnClickListener {
                viewModel.filterByCategory(null)
            }
        }
        binding.chipGroupCategory.addView(allChip)

        // 添加分类选项
        val categories = listOf(
            "social" to R.string.category_social,
            "finance" to R.string.category_finance,
            "shopping" to R.string.category_shopping,
            "work" to R.string.category_work,
            "entertainment" to R.string.category_entertainment,
            "other" to R.string.category_other
        )

        categories.forEach { (key, labelRes) ->
            val chip = Chip(requireContext()).apply {
                text = getString(labelRes)
                isCheckable = true
                chipStrokeWidth = 1f
                chipCornerRadius = 20f
                chipStrokeColor = ContextCompat.getColorStateList(requireContext(), R.color.chip_category_stroke)
                setChipBackgroundColorResource(R.color.chip_category_bg)
                setTextColor(ContextCompat.getColorStateList(requireContext(), R.color.chip_category_text))
                setOnClickListener {
                    viewModel.filterByCategory(key)
                }
            }
            binding.chipGroupCategory.addView(chip)
        }
    }

    /**
     * 设置浮动按钮
     */
    private fun setupFab() {
        binding.fabAdd.setOnClickListener {
            navigateToAddAccount()
        }
    }

    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.appBarLayout) { view, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.updatePadding(
                top = statusBars.top + resources.getDimensionPixelSize(R.dimen.top_bar_safe_spacing)
            )
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.rvAccounts) { view, insets ->
            val navigationBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.updatePadding(
                bottom = navigationBars.bottom + resources.getDimensionPixelSize(R.dimen.bottom_nav_content_spacing_accounts)
            )
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.fabAdd) { view, insets ->
            val navigationBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.updateLayoutParams<android.view.ViewGroup.MarginLayoutParams> {
                bottomMargin = navigationBars.bottom + resources.getDimensionPixelSize(R.dimen.bottom_nav_margin_bottom)
            }
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
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
    private fun updateUi(state: AccountsUiState) {
        // 加载状态
        binding.progressBar.isVisible = state.isLoading

        // 账号列表
        accountAdapter.submitList(state.filteredAccounts)

        // 空状态
        val isEmpty = state.filteredAccounts.isEmpty() && !state.isLoading
        val isSearching = state.searchQuery.isNotEmpty()
        
        binding.emptyState.isVisible = isEmpty && !isSearching
        binding.noResultsState.isVisible = isEmpty && isSearching
        binding.rvAccounts.isVisible = !isEmpty

        // 错误信息
        state.errorMessage?.let { message ->
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            viewModel.clearError()
        }
    }

    /**
     * 导航到账号详情
     */
    private fun navigateToDetail(account: Account) {
        viewModel.incrementUseCount(account.id)
        val action = AccountsFragmentDirections
            .actionNavigationAccountsToAccountDetailFragment(account.id)
        findNavController().navigate(action)
    }

    /**
     * 导航到添加账号
     */
    private fun navigateToAddAccount() {
        val action = AccountsFragmentDirections
            .actionNavigationAccountsToAddAccountFragment(-1L)
        findNavController().navigate(action)
    }

    /**
     * 复制密码到剪贴板
     */
    private fun copyPasswordToClipboard(account: Account) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("password", account.password)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(requireContext(), R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
        
        // 增加使用次数
        viewModel.incrementUseCount(account.id)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
