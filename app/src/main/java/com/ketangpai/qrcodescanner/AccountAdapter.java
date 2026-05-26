package com.ketangpai.qrcodescanner;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class AccountAdapter extends RecyclerView.Adapter<AccountAdapter.ViewHolder> {
    private final List<Account> accountList;
    private final Context context;
    private OnDeleteClickListener deleteListener;
    private OnRefreshClickListener refreshListener;
    private OnSignToggleListener signToggleListener;
    // 新增：用于处理长按菜单中的编辑备注和账号详情
    private OnEditRemarkClickListener editRemarkListener;
    private OnViewDetailsClickListener viewDetailsListener;

    // 接口定义
    public interface OnDeleteClickListener {
        void onDeleteClick(Account account, int position);
    }

    public interface OnRefreshClickListener {
        void onRefreshClick(Account account, int position);
    }

    public interface OnSignToggleListener {
        void onSignToggled(Account account, boolean isChecked, int position);
    }

    public interface OnEditRemarkClickListener {
        void onEditRemarkClick(Account account, int position);
    }

    public interface OnViewDetailsClickListener {
        void onViewDetailsClick(Account account, int position);
    }

    // Setter 方法
    public void setOnDeleteClickListener(OnDeleteClickListener listener) {
        this.deleteListener = listener;
    }

    public void setOnRefreshClickListener(OnRefreshClickListener listener) {
        this.refreshListener = listener;
    }

    public void setOnSignToggleListener(OnSignToggleListener listener) {
        this.signToggleListener = listener;
    }

    public void setOnEditRemarkClickListener(OnEditRemarkClickListener listener) {
        this.editRemarkListener = listener;
    }

    public void setOnViewDetailsClickListener(OnViewDetailsClickListener listener) {
        this.viewDetailsListener = listener;
    }

    public AccountAdapter(Context context, List<Account> accountList) {
        this.context = context;
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

        // 显示名称：优先显示备注，否则显示用户名
        String displayName = account.getRemark() != null && !account.getRemark().isEmpty()
                ? account.getRemark() : account.getUsername();
        holder.tvUsername.setText(displayName);

        // ---------- 优化开关处理 ----------
        holder.switchSign.setOnCheckedChangeListener(null); // 先移除旧监听器
        holder.switchSign.setChecked(account.isSignEnabled()); // 设置状态，此时不会触发监听器
        holder.switchSign.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (signToggleListener != null) {
                signToggleListener.onSignToggled(account, isChecked, position);
            }
        });

        // 获取登录状态
        LoginSessionManager.KtpSession session =
                LoginSessionManager.getInstance().getSession(account.getUsername());
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

        // 签到状态
        String signStatus = account.getStatus();
        if (signStatus == null || signStatus.isEmpty() || "未开始".equals(signStatus)) {
            signStatus = "未开始";
        }

        String combinedText = String.format("登录: %s | 签到: %s", loginStatus, signStatus);
        holder.tvCombinedStatus.setText(combinedText);
        holder.tvCombinedStatus.setTextColor(statusColor);

        // 长按弹出菜单（编辑备注、账号详情、重新登录）
        holder.itemView.setOnLongClickListener(v -> {
            PopupMenu popup = new PopupMenu(context, v);
            popup.inflate(R.menu.account_item_menu);
            popup.setOnMenuItemClickListener(item -> {
                int itemId = item.getItemId();
                if (itemId == R.id.action_edit_remark) {
                    if (editRemarkListener != null) {
                        editRemarkListener.onEditRemarkClick(account, position);
                    }
                    return true;
                } else if (itemId == R.id.action_view_details) {
                    if (viewDetailsListener != null) {
                        viewDetailsListener.onViewDetailsClick(account, position);
                    }
                    return true;
                } else if (itemId == R.id.action_refresh) {
                    if (refreshListener != null) {
                        refreshListener.onRefreshClick(account, position);
                    }
                    return true;
                }
                return false;
            });
            popup.show();
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
        SwitchCompat switchSign;
        TextView tvCombinedStatus;
        Button btnDelete;

        ViewHolder(View itemView) {
            super(itemView);
            tvUsername = itemView.findViewById(R.id.tv_username);
            switchSign = itemView.findViewById(R.id.switch_sign);
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