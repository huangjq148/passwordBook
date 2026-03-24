package com.example.passwordsafe.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.passwordsafe.R
import com.example.passwordsafe.data.model.Account
import com.example.passwordsafe.databinding.ItemAccountBinding

/**
 * 账号列表适配器
 */
class AccountAdapter(
    private val onItemClickListener: (Account) -> Unit,
    private val onCopyClickListener: (Account) -> Unit
) : ListAdapter<Account, AccountAdapter.AccountViewHolder>(AccountDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AccountViewHolder {
        val binding = ItemAccountBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AccountViewHolder(binding, onItemClickListener, onCopyClickListener)
    }

    override fun onBindViewHolder(holder: AccountViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class AccountViewHolder(
        private val binding: ItemAccountBinding,
        private val onItemClickListener: (Account) -> Unit,
        private val onCopyClickListener: (Account) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(account: Account) {
            binding.apply {
                // 设置网站名称首字母为头像
                val firstChar = account.website.takeIf { it.isNotEmpty() }?.first() ?: '?'
                tvAvatar.text = firstChar.toString().uppercase()
                
                // 设置网站名称
                tvWebsite.text = account.website
                
                // 设置账号
                tvAccount.text = account.account
                
                // 点击整个卡片
                root.setOnClickListener {
                    onItemClickListener(account)
                }
                
                // 复制按钮
                btnCopy.setOnClickListener {
                    onCopyClickListener(account)
                }
            }
        }
    }

    /**
     * DiffUtil 回调
     */
    class AccountDiffCallback : DiffUtil.ItemCallback<Account>() {
        override fun areItemsTheSame(oldItem: Account, newItem: Account): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Account, newItem: Account): Boolean {
            return oldItem == newItem
        }
    }
}
