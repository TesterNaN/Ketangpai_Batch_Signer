package com.ketangpai.qrcodescanner;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.permissionx.guolindev.PermissionX;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private Button btnScan;
    private Button btnAddAccount;
    private TextView tvResult;
    private AccountAdapter adapter;
    private final List<Account> accountList = new ArrayList<>();
    private AccountManager accountManager;
    private LoginSessionManager sessionManager;
    private String scannedUrl = "";

    private final AtomicInteger ongoingLoginTasks = new AtomicInteger(0);
    private boolean isSignInProgress = false;
    private final AtomicBoolean preLoginCompleted = new AtomicBoolean(false);

    private final ActivityResultLauncher<Intent> scanLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    scannedUrl = result.getData().getStringExtra("SCAN_RESULT");
                    tvResult.setText("二维码已识别，正在自动签到...");
                    startSignProcess();
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        accountManager = new AccountManager(this);
        sessionManager = LoginSessionManager.getInstance();

        initViews();
        loadAccounts();
        setupListeners();
        setupItemActionListeners(); // 设置独立监听器

        updateScanButtonState(false, "正在准备账号...", "#9E9E9E");
        performAutoLogin();
    }

    private void initViews() {
        btnScan = findViewById(R.id.btn_scan);
        btnAddAccount = findViewById(R.id.btn_add_account);
        tvResult = findViewById(R.id.tv_result);
        RecyclerView recyclerView = findViewById(R.id.recycler_view);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AccountAdapter(this, accountList);
        recyclerView.setAdapter(adapter);
    }

    @SuppressLint("NotifyDataSetChanged")
    private void loadAccounts() {
        accountList.clear();
        accountList.addAll(accountManager.getAllAccounts());
        adapter.notifyDataSetChanged();
    }

    @SuppressLint("SetTextI18n")
    private void setupListeners() {
        btnScan.setOnClickListener(v -> {
            if (ongoingLoginTasks.get() > 0) {
                Toast.makeText(this, "账号登录中，请稍候...", Toast.LENGTH_SHORT).show();
                return;
            }
            if (isSignInProgress) {
                Toast.makeText(this, "签到进行中，请等待完成...", Toast.LENGTH_SHORT).show();
                return;
            }
            PermissionX.init(this)
                    .permissions(android.Manifest.permission.CAMERA)
                    .request((allGranted, grantedList, deniedList) -> {
                        if (allGranted) {
                            startScanActivity();
                        } else {
                            tvResult.setText("需要相机权限才能扫描二维码");
                        }
                    });
        });

        btnAddAccount.setOnClickListener(v -> {
            if (ongoingLoginTasks.get() > 0) {
                Toast.makeText(this, "正在登录其他账号，请稍候...", Toast.LENGTH_SHORT).show();
                return;
            }
            showAddAccountDialog();
        });

        adapter.setOnDeleteClickListener((account, position) -> new AlertDialog.Builder(this)
                .setTitle("确认删除")
                .setMessage("确定要删除账号 " + account.getUsername() + " 吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    if (accountManager.deleteAccount(account)) {
                        sessionManager.removeSession(account.getUsername());
                        accountList.remove(position);
                        adapter.notifyItemRemoved(position);
                        tvResult.setText("账号已删除: " + account.getUsername());
                        updateButtonState();
                    }
                })
                .setNegativeButton("取消", null)
                .show());

        // 签到开关监听
        adapter.setOnSignToggleListener((account, isChecked, position) -> {
            account.setSignEnabled(isChecked);
            // 异步保存，避免阻塞 UI
            new Thread(() -> accountManager.saveAccount(account)).start();
            updateButtonState(); // 立即更新按钮状态
        });
    }

    // 设置长按菜单的三个监听器
    private void setupItemActionListeners() {
        // 方法引用（参数完全匹配）
        adapter.setOnEditRemarkClickListener(this::showEditRemarkDialog);

        // 表达式 lambda（忽略 position 参数）
        adapter.setOnViewDetailsClickListener((account, position) -> showAccountDetailsDialog(account));

        // 方法引用（参数完全匹配）
        adapter.setOnRefreshClickListener(this::refreshSingleAccount);
    }

    private void showEditRemarkDialog(Account account, int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("编辑备注");

        final EditText input = new EditText(this);
        input.setText(account.getRemark());
        input.setHint("输入备注（留空则显示用户名）");
        builder.setView(input);

        builder.setPositiveButton("保存", (dialog, which) -> {
            String remark = input.getText().toString().trim();
            account.setRemark(remark);
            accountManager.saveAccount(account);
            adapter.updateAccountStatus(position, account);
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void showAccountDetailsDialog(Account account) {
        String message = "用户名/手机号: " + account.getUsername() + "\n"
                + (account.getRemark() != null && !account.getRemark().isEmpty()
                ? "备注: " + account.getRemark() : "备注: 无");
        new AlertDialog.Builder(this)
                .setTitle("账号详情")
                .setMessage(message)
                .setPositiveButton("确定", null)
                .show();
    }

    private void startScanActivity() {
        if (accountList.isEmpty()) {
            tvResult.setText("请先添加至少一个签到账号");
            return;
        }
        Intent intent = new Intent(this, QRCodeScanActivity.class);
        scanLauncher.launch(intent);
    }

    private void updateScanButtonState(boolean enabled, String text, String colorHex) {
        runOnUiThread(() -> {
            btnScan.setEnabled(enabled);
            btnScan.setText(text);
            btnScan.setBackgroundTintList(ColorStateList.valueOf(android.graphics.Color.parseColor(colorHex)));
            btnScan.setTextColor(android.graphics.Color.WHITE);
        });
    }

    @SuppressLint("NotifyDataSetChanged")
    private void performAutoLogin() {
        if (accountList.isEmpty()) {
            updateScanButtonState(true, "添加账号后开始扫码", "#4CAF50");
            tvResult.setText("请先添加至少一个签到账号");
            return;
        }

        ongoingLoginTasks.set(0);
        preLoginCompleted.set(false);

        int needLoginCount = 0;
        for (Account account : accountList) {
            LoginSessionManager.KtpSession session = sessionManager.getSession(account.getUsername());
            if (session == null || !session.isTokenValid()) {
                needLoginCount++;
            }
        }

        if (needLoginCount == 0) {
            updateButtonState();
            return;
        }

        final int finalNeedLoginCount = needLoginCount;
        updateScanButtonState(false, "预登录中 (0/" + finalNeedLoginCount + ")", "#9E9E9E");
        tvResult.setText("正在预登录账号，请稍候...");

        final int[] completedCount = {0};
        final int[] successCount = {0};

        for (Account account : accountList) {
            LoginSessionManager.KtpSession existingSession = sessionManager.getSession(account.getUsername());
            if (existingSession != null && existingSession.isTokenValid()) {
                continue;
            }

            ongoingLoginTasks.incrementAndGet();

            new Thread(() -> {
                try {
                    NetworkManager networkManager = new NetworkManager();
                    String token = networkManager.loginAndGetToken(account);
                    if (token != null && !token.isEmpty()) {
                        sessionManager.addSession(account.getUsername(), token);
                        successCount[0]++;
                        Log.i(TAG, account.getUsername() + " 预登录成功");
                    } else {
                        Log.w(TAG, account.getUsername() + " 预登录失败");
                    }
                } catch (Exception e) {
                    Log.e(TAG, account.getUsername() + " 登录异常", e);
                } finally {
                    completedCount[0]++;
                    ongoingLoginTasks.decrementAndGet();

                    runOnUiThread(() -> {
                        if (ongoingLoginTasks.get() == 0 && completedCount[0] >= finalNeedLoginCount) {
                            if (preLoginCompleted.compareAndSet(false, true)) {
                                onAllLoginTasksComplete(successCount[0]);
                            }
                        } else {
                            String progressText = "预登录中 (" + completedCount[0] + "/" + finalNeedLoginCount + ")";
                            updateScanButtonState(false, progressText, "#9E9E9E");

                            @SuppressLint("DefaultLocale") String infoText = String.format("正在准备账号...\n已完成: %d/%d\n成功: %d个",
                                    completedCount[0], finalNeedLoginCount, successCount[0]);
                            tvResult.setText(infoText);
                        }
                        adapter.notifyDataSetChanged();
                    });
                }
            }).start();

            try { Thread.sleep(300); } catch (InterruptedException e) { break; }
        }
    }

    private void onAllLoginTasksComplete(int successCount) {
        runOnUiThread(() -> {
            Map<String, String> validTokens = sessionManager.getAllValidTokens();
            int readyCount = 0;
            for (Account account : accountList) {
                if (account.isSignEnabled() && validTokens.containsKey(account.getUsername())) {
                    readyCount++;
                }
            }

            if (readyCount > 0) {
                @SuppressLint("DefaultLocale") String resultText = String.format("预登录完成！%d个账号已准备就绪", readyCount);
                tvResult.setText(resultText);
                Toast.makeText(this, "账号准备就绪，可以开始扫码签到", Toast.LENGTH_SHORT).show();
            } else if (successCount == 0) {
                tvResult.setText("预登录失败，所有账号登录未成功，将使用普通签到模式");
                Toast.makeText(this, "所有账号登录失败，请检查账号密码或网络", Toast.LENGTH_LONG).show();
            } else {
                tvResult.setText("部分账号登录成功，但均未开启签到或令牌已过期");
            }

            updateButtonState();
        });
    }

    @SuppressLint("NotifyDataSetChanged")
    private void startSignProcess() {
        Map<String, String> validTokens = sessionManager.getAllValidTokens();

        if (validTokens.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("提示")
                    .setMessage("当前没有已登录的账号，签到可能需要更长时间。是否继续？")
                    .setPositiveButton("继续", (dialog, which) -> executeSignProcess())
                    .setNegativeButton("取消", null)
                    .show();
        } else {
            executeSignProcess();
        }
    }

    @SuppressLint({"NotifyDataSetChanged", "SetTextI18n"})
    private void executeSignProcess() {
        isSignInProgress = true;
        updateScanButtonState(false, "签到进行中...", "#FF9800");

        new Thread(() -> {
            try {
                NetworkManager networkManager = new NetworkManager();
                String result;

                Map<String, String> allValidTokens = sessionManager.getAllValidTokens();

                List<Account> signAccounts = new ArrayList<>();
                for (Account account : accountList) {
                    if (account.isSignEnabled()) {
                        signAccounts.add(account);
                    }
                }

                if (signAccounts.isEmpty()) {
                    result = "没有选择任何要签到的账号";
                } else {
                    Map<String, String> signTokens = new HashMap<>();
                    for (Account account : signAccounts) {
                        String token = allValidTokens.get(account.getUsername());
                        if (token != null) {
                            signTokens.put(account.getUsername(), token);
                        }
                    }

                    if (!signTokens.isEmpty()) {
                        result = networkManager.quickSignReadyAccounts(scannedUrl, signAccounts, signTokens);
                    } else {
                        result = networkManager.processAllAccounts(scannedUrl, signAccounts);
                    }
                }

                final String finalResult = result;
                runOnUiThread(() -> {
                    tvResult.setText(finalResult);
                    adapter.notifyDataSetChanged();
                    isSignInProgress = false;
                    updateButtonState();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    tvResult.setText("签到异常: " + e.getMessage());
                    isSignInProgress = false;
                    updateButtonState();
                });
            }
        }).start();
    }

    @SuppressLint("SetTextI18n")
    private void showAddAccountDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("添加签到账号");
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_account, null);
        EditText etUsername = dialogView.findViewById(R.id.et_username);
        EditText etPassword = dialogView.findViewById(R.id.et_password);
        builder.setView(dialogView);

        builder.setPositiveButton("保存", (dialog, which) -> {
            String username = etUsername.getText().toString().trim();
            String password = etPassword.getText().toString().trim();
            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "账号和密码不能为空", Toast.LENGTH_SHORT).show();
                return;
            }

            Account newAccount = new Account(username, password);
            if (accountManager.saveAccount(newAccount)) {
                loadAccounts();
                tvResult.setText("账号已添加: " + username);
                loginNewAccountImmediately(newAccount);
            }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    @SuppressLint("NotifyDataSetChanged")
    private void loginNewAccountImmediately(Account account) {
        LoginSessionManager.KtpSession existingSession = sessionManager.getSession(account.getUsername());
        if (existingSession != null && existingSession.isTokenValid()) {
            Toast.makeText(this, "账号已登录", Toast.LENGTH_SHORT).show();
            updateButtonState();
            return;
        }

        ongoingLoginTasks.incrementAndGet();
        updateScanButtonState(false, "新账号登录中...", "#9E9E9E");

        new Thread(() -> {
            try {
                NetworkManager networkManager = new NetworkManager();
                String token = networkManager.loginAndGetToken(account);

                if (token != null) {
                    sessionManager.addSession(account.getUsername(), token);
                    runOnUiThread(() -> {
                        Toast.makeText(this,
                                account.getUsername() + " 登录成功，可快速签到",
                                Toast.LENGTH_SHORT).show();
                        adapter.notifyDataSetChanged();
                    });
                } else {
                    runOnUiThread(() -> Toast.makeText(this,
                            account.getUsername() + " 登录失败，将使用普通签到",
                            Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this,
                        "登录异常: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show());
            } finally {
                ongoingLoginTasks.decrementAndGet();
                runOnUiThread(this::updateButtonState);
            }
        }).start();
    }

    private void refreshSingleAccount(Account account, int position) {
        ongoingLoginTasks.incrementAndGet();
        updateScanButtonState(false, "重新登录中...", "#9E9E9E");

        new Thread(() -> {
            try {
                NetworkManager networkManager = new NetworkManager();
                String token = networkManager.loginAndGetToken(account);

                if (token != null) {
                    sessionManager.addSession(account.getUsername(), token);
                    runOnUiThread(() -> {
                        Toast.makeText(this,
                                account.getUsername() + " 重新登录成功",
                                Toast.LENGTH_SHORT).show();
                        adapter.updateAccountStatus(position, account);
                    });
                } else {
                    runOnUiThread(() -> Toast.makeText(this,
                            account.getUsername() + " 重新登录失败",
                            Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this,
                        "重新登录异常: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show());
            } finally {
                ongoingLoginTasks.decrementAndGet();
                runOnUiThread(this::updateButtonState);
            }
        }).start();
    }

    private void updateButtonState() {
        runOnUiThread(() -> {
            boolean anySignEnabled = false;
            for (Account account : accountList) {
                if (account.isSignEnabled()) {
                    anySignEnabled = true;
                    break;
                }
            }

            if (!anySignEnabled) {
                updateScanButtonState(false, "无签到账号", "#9E9E9E");
                return;
            }

            if (ongoingLoginTasks.get() > 0) {
                updateScanButtonState(false, "账号登录中...", "#9E9E9E");
            } else if (isSignInProgress) {
                updateScanButtonState(false, "签到进行中...", "#FF9800");
            } else {
                Map<String, String> validTokens = sessionManager.getAllValidTokens();
                int readyCount = 0;
                int totalSignEnabled = 0;
                for (Account account : accountList) {
                    if (account.isSignEnabled()) {
                        totalSignEnabled++;
                        if (validTokens.containsKey(account.getUsername())) {
                            readyCount++;
                        }
                    }
                }

                if (readyCount > 0) {
                    @SuppressLint("DefaultLocale") String buttonText = String.format("开始扫码签到 (%d/%d就绪)", readyCount, totalSignEnabled);
                    updateScanButtonState(true, buttonText, "#4CAF50");
                } else {
                    @SuppressLint("DefaultLocale") String buttonText = String.format("开始扫码签到（0/%d就绪）", totalSignEnabled);
                    updateScanButtonState(true, buttonText, "#2196F3");
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateButtonState();
    }
}