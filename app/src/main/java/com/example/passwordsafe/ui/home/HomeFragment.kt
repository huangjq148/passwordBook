package com.example.passwordsafe.ui.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.passwordsafe.R
import com.example.passwordsafe.databinding.FragmentHomeBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * 首页 Fragment
 * 显示最近使用的账号列表，支持搜索功能
 */
@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: HomeViewModel by viewModels()
    
    private lateinit var accountAdapter: AccountAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        setupSearchView()
        setupFab()
        observeUiState()
    }

    /**
     * 设置 RecyclerView
     */
    private fun setupRecyclerView() {
        accountAdapter = AccountAdapter(
            onItemClickListener = { account ->
                val accountId = viewModel.onAccountClicked(account.id)
                val bundle = Bundle().apply {
                    putLong("accountId", accountId)
                }
                findNavController().navigate(R.id.navigation_account_detail, bundle)
            },
            onCopyClickListener = { account ->
                // 复制密码到剪贴板
                copyToClipboard(account.password)
                viewModel.onAccountClicked(account.id)
                Toast.makeText(requireContext(), R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
            }
        )
        
        binding.rvAccounts.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = accountAdapter
            setHasFixedSize(false)
        }
    }

    /**
     * 设置搜索框
     */
    private fun setupSearchView() {
        binding.etSearch.apply {
            // 监听文本变化
            doOnTextChanged { text, _, _, _ ->
                viewModel.search(text?.toString() ?: "")
            }
            
            // 监听软键盘搜索按钮
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    viewModel.search(text?.toString() ?: "")
                    true
                } else {
                    false
                }
            }
        }
    }

    /**
     * 设置 FAB 按钮
     */
    private fun setupFab() {
        binding.fabAdd.setOnClickListener {
            // 导航到添加账号页面
            findNavController().navigate(R.id.action_navigation_home_to_addAccountFragment)
        }
    }

    /**
     * 观察 UI 状态
     */
    private fun observeUiState() {
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
    private fun updateUi(state: HomeUiState) {
        // 显示/隐藏加载进度
        binding.progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE
        
        // 根据搜索状态显示不同的列表
        if (state.isSearching) {
            // 搜索中
            binding.rvAccounts.visibility = View.GONE
            binding.llEmpty.visibility = View.GONE
            binding.llNoResults.visibility = View.GONE
        } else if (state.searchQuery.isNotEmpty()) {
            // 搜索模式
            if (state.searchResults.isEmpty()) {
                binding.rvAccounts.visibility = View.GONE
                binding.llNoResults.visibility = View.VISIBLE
                binding.llEmpty.visibility = View.GONE
            } else {
                binding.rvAccounts.visibility = View.VISIBLE
                binding.llNoResults.visibility = View.GONE
                binding.llEmpty.visibility = View.GONE
                accountAdapter.submitList(state.searchResults)
            }
            binding.tvRecentTitle.text = getString(R.string.search_hint)
        } else {
            // 正常模式
            binding.llNoResults.visibility = View.GONE
            binding.tvRecentTitle.text = getString(R.string.recent_accounts)
            
            if (state.recentAccounts.isEmpty()) {
                binding.rvAccounts.visibility = View.GONE
                binding.llEmpty.visibility = View.VISIBLE
            } else {
                binding.rvAccounts.visibility = View.VISIBLE
                binding.llEmpty.visibility = View.GONE
                accountAdapter.submitList(state.recentAccounts)
            }
        }
    }

    /**
     * 复制到剪贴板
     */
    private fun copyToClipboard(text: String) {
        val clipboardManager = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("password", text)
        clipboardManager.setPrimaryClip(clip)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
