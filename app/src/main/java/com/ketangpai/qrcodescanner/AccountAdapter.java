package com.ketangpai.qrcodescanner;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class AccountAdapter extends RecyclerView.Adapter<AccountAdapter.ViewHolder> {
    private final List<Account> accountList;
    private OnDeleteClickListener deleteListener;
    private OnRefreshClickListener refreshListener;

    public interface OnDeleteClickListener {
        void onDeleteClick(Account account, int position);
    }

    public interface OnRefreshClickListener {
        void onRefreshClick(Account account, int position);
    }

    public void setOnDeleteClickListener(OnDeleteClickListener listener) {
        this.deleteListener = listener;
    }

    public void setOnRefreshClickListener(OnRefreshClickListener listener) {
        this.refreshListener = listener;
    }

    public AccountAdapter(List<Account> accountList) {
        this.accountList = accountList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_account, parent, false);
        return new ViewHolder(view);
    }

    @SuppressLint({"SetTextI18n", "DefaultLocale"})
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Account account = accountList.get(position);

        // 设置用户名
        holder.tvUsername.setText(account.getUsername());

        // 获取登录状态
        KetangpaiSessionManager.KtpSession session =
                KetangpaiSessionManager.getInstance().getSession(account.getUsername());

        // 构建合并状态文本
        String loginStatus;
        int statusColor;

        if (session == null) {
            loginStatus = "○ 未登录";
            statusColor = Color.parseColor("#9E9E9E");
        } else if (session.isTokenValid()) {
            long minutes = (System.currentTimeMillis() - session.getLoginTime()) / (1000 * 60);
            loginStatus = String.format("✓ 已登录 (%dm)", minutes);
            statusColor = Color.parseColor("#4CAF50");
        } else {
            loginStatus = "⚠ 令牌过期";
            statusColor = Color.parseColor("#FF9800");
        }

        // 获取签到状态
        String signStatus = account.getStatus();
        if (signStatus == null || signStatus.isEmpty() || "未开始".equals(signStatus)) {
            signStatus = "未开始";
        }

        // 设置合并状态文本和颜色
        String combinedText = String.format("登录: %s | 签到: %s", loginStatus, signStatus);
        holder.tvCombinedStatus.setText(combinedText);
        holder.tvCombinedStatus.setTextColor(statusColor);

        // 长按重新登录
        holder.itemView.setOnLongClickListener(v -> {
            if (refreshListener != null) {
                refreshListener.onRefreshClick(account, position);
            }
            return true;
        });

        // 删除按钮
        holder.btnDelete.setOnClickListener(v -> {
            if (deleteListener != null) {
                deleteListener.onDeleteClick(account, position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return accountList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvUsername;
        TextView tvCombinedStatus;
        Button btnDelete;

        ViewHolder(View itemView) {
            super(itemView);
            tvUsername = itemView.findViewById(R.id.tv_username);
            tvCombinedStatus = itemView.findViewById(R.id.tv_combined_status);
            btnDelete = itemView.findViewById(R.id.btn_delete);
        }
    }

    public void updateAccountStatus(int position, Account account) {
        if (position >= 0 && position < accountList.size()) {
            accountList.set(position, account);
            notifyItemChanged(position);
        }
    }
}