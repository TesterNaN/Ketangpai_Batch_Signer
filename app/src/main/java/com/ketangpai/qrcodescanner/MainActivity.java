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
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends AppCompatActivity {
    private Button btnScan;
    private Button btnAddAccount;
    private TextView tvResult;
    private AccountAdapter adapter;
    private final List<Account> accountList = new ArrayList<>();
    private AccountManager accountManager;
    private KetangpaiSessionManager sessionManager;
    private String scannedUrl = "";

    // 登录任务管理
    private final AtomicInteger ongoingLoginTasks = new AtomicInteger(0);
    private boolean isSignInProgress = false;

    // 替换 startActivityForResult
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
        sessionManager = KetangpaiSessionManager.getInstance();

        initViews();
        loadAccounts();
        setupListeners();
        setupRefreshListener();

        // 初始时禁用签到按钮
        updateScanButtonState(false, "正在准备账号...", "#9E9E9E");

        // 启动时预登录
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
    }

    private void setupRefreshListener() {
        adapter.setOnRefreshClickListener((account, position) -> {
            if (ongoingLoginTasks.get() > 0) {
                Toast.makeText(this, "已有登录任务进行中，请稍候", Toast.LENGTH_SHORT).show();
                return;
            }

            new AlertDialog.Builder(this)
                    .setTitle("重新登录")
                    .setMessage("确定要重新登录账号 " + account.getUsername() + " 吗？")
                    .setPositiveButton("确定", (dialog, which) -> {
                        refreshSingleAccount(account, position);
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });
    }

    private void startScanActivity() {
        if (accountList.isEmpty()) {
            tvResult.setText("请先添加至少一个签到账号");
            return;
        }
        Intent intent = new Intent(this, QRCodeScanActivity.class);
        scanLauncher.launch(intent);
    }

    /**
     * 更新扫描按钮状态
     */
    private void updateScanButtonState(boolean enabled, String text, String colorHex) {
        runOnUiThread(() -> {
            btnScan.setEnabled(enabled);
            btnScan.setText(text);
            btnScan.setBackgroundTintList(ColorStateList.valueOf(android.graphics.Color.parseColor(colorHex)));
            btnScan.setTextColor(android.graphics.Color.WHITE);
        });
    }

    /**
     * 自动预登录所有账号
     */
    @SuppressLint("NotifyDataSetChanged")
    private void performAutoLogin() {
        if (accountList.isEmpty()) {
            updateScanButtonState(true, "添加账号后开始扫码", "#4CAF50");
            tvResult.setText("请先添加至少一个签到账号");
            return;
        }

        // 重置任务计数
        ongoingLoginTasks.set(0);

        // 统计需要登录的账号数
        int needLoginCount = (int) accountList.stream().map(account -> sessionManager.getSession(account.getUsername())).filter(session -> session == null || !session.isTokenValid()).count();

        if (needLoginCount == 0) {
            // 所有账号都已登录
            updateButtonState();
            return;
        }

        // 禁用按钮并显示进度
        updateScanButtonState(false, "预登录中 (0/" + needLoginCount + ")", "#9E9E9E");
        tvResult.setText("正在预登录账号，请稍候...");

        final int[] completedCount = {0};
        final int[] successCount = {0};

        for (Account account : accountList) {
            // 检查是否已有有效会话
            KetangpaiSessionManager.KtpSession existingSession = sessionManager.getSession(account.getUsername());

            if (existingSession != null && existingSession.isTokenValid()) {
                // 已有有效会话，跳过
                continue;
            }

            // 启动登录任务
            ongoingLoginTasks.incrementAndGet();

            new Thread(() -> {
                try {
                    NetworkManager networkManager = new NetworkManager();
                    String token = networkManager.loginAndGetToken(account);

                    if (token != null) {
                        sessionManager.addSession(account.getUsername(), token);
                        successCount[0]++;
                        Log.i("AutoLogin", account.getUsername() + " 预登录成功");
                    } else {
                        Log.w("AutoLogin", account.getUsername() + " 预登录失败");
                    }
                } catch (Exception e) {
                    Log.e("AutoLogin", account.getUsername() + " 登录异常", e);
                } finally {
                    completedCount[0]++;
                    ongoingLoginTasks.decrementAndGet();

                    runOnUiThread(() -> {
                        // 更新进度显示
                        String progressText = "预登录中 (" + completedCount[0] + "/" + needLoginCount + ")";
                        updateScanButtonState(false, progressText, "#9E9E9E");

                        // 更新结果框
                        @SuppressLint("DefaultLocale") String infoText = String.format("正在准备账号...\n已完成: %d/%d\n成功: %d个",
                                completedCount[0], needLoginCount, successCount[0]);
                        tvResult.setText(infoText);

                        // 刷新列表
                        adapter.notifyDataSetChanged();

                        // 检查是否所有任务完成
                        if (ongoingLoginTasks.get() == 0 && completedCount[0] >= needLoginCount) {
                            onAllLoginTasksComplete(successCount[0], needLoginCount);
                        }
                    });
                }
            }).start();

            // 延迟一下，避免请求过快
            try { Thread.sleep(300); } catch (InterruptedException e) { break; }
        }
    }

    /**
     * 所有登录任务完成后的处理
     */
    private void onAllLoginTasksComplete(int successCount, int totalCount) {
        runOnUiThread(() -> {
            Map<String, String> validTokens = sessionManager.getAllValidTokens();
            int readyCount = validTokens.size();

            if (readyCount > 0) {
                // 有已登录的账号
                @SuppressLint("DefaultLocale") String buttonText = String.format("开始扫码签到 (%d/%d就绪)", readyCount, accountList.size());
                updateScanButtonState(true, buttonText, "#4CAF50");

                @SuppressLint("DefaultLocale") String resultText = String.format("预登录完成！%d个账号已准备就绪", readyCount);
                tvResult.setText(resultText);

                Toast.makeText(this, "账号准备就绪，可以开始扫码签到", Toast.LENGTH_SHORT).show();
            } else if (successCount == 0) {
                // 全部登录失败，但仍允许使用普通模式
                updateScanButtonState(true, "开始扫码签到（普通模式）", "#2196F3");
                tvResult.setText("预登录失败，将使用普通签到模式");
                Toast.makeText(this, "登录失败，签到可能需要更长时间", Toast.LENGTH_LONG).show();
            } else {
                // 有成功但令牌可能已过期
                updateScanButtonState(true, "开始扫码签到", "#4CAF50");
                tvResult.setText("账号已尝试登录");
            }
        });
    }

    /**
     * 开始签到流程
     */
    @SuppressLint("NotifyDataSetChanged")
    private void startSignProcess() {
        // 检查登录状态
        Map<String, String> validTokens = sessionManager.getAllValidTokens();

        if (validTokens.isEmpty()) {
            // 没有有效令牌，确认是否继续
            new AlertDialog.Builder(this)
                    .setTitle("提示")
                    .setMessage("当前没有已登录的账号，签到可能需要更长时间。是否继续？")
                    .setPositiveButton("继续", (dialog, which) -> {
                        executeSignProcess();
                    })
                    .setNegativeButton("取消", null)
                    .show();
        } else {
            executeSignProcess();
        }
    }

    /**
     * 实际执行签到流程
     */
    @SuppressLint({"NotifyDataSetChanged", "SetTextI18n"})
    private void executeSignProcess() {
        isSignInProgress = true;
        updateScanButtonState(false, "签到进行中...", "#FF9800");

        new Thread(() -> {
            try {
                NetworkManager networkManager = new NetworkManager();
                String result;

                Map<String, String> validTokens = sessionManager.getAllValidTokens();

                if (!validTokens.isEmpty()) {
                    result = networkManager.quickSignReadyAccounts(scannedUrl, accountList, validTokens);
                } else {
                    result = networkManager.processAllAccounts(scannedUrl, accountList);
                }

                final String finalResult = result;
                runOnUiThread(() -> {
                    tvResult.setText(finalResult);
                    adapter.notifyDataSetChanged();

                    // 签到完成后恢复按钮状态
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

                // 立即登录新账号（这会阻塞按钮）
                loginNewAccountImmediately(newAccount);
            }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    /**
     * 立即登录新添加的账号（阻塞按钮直到完成）
     */
    @SuppressLint("NotifyDataSetChanged")
    private void loginNewAccountImmediately(Account account) {
        // 检查是否已有有效会话
        KetangpaiSessionManager.KtpSession existingSession = sessionManager.getSession(account.getUsername());
        if (existingSession != null && existingSession.isTokenValid()) {
            Toast.makeText(this, "账号已登录", Toast.LENGTH_SHORT).show();
            updateButtonState();
            return;
        }

        // 启动登录任务
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
                    runOnUiThread(() -> {
                        Toast.makeText(this,
                                account.getUsername() + " 登录失败，将使用普通签到",
                                Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(this,
                            "登录异常: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
            } finally {
                // 任务完成
                ongoingLoginTasks.decrementAndGet();
                runOnUiThread(this::updateButtonState);
            }
        }).start();
    }

    /**
     * 刷新单个账号登录状态
     */
    private void refreshSingleAccount(Account account, int position) {
        // 启动登录任务
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
                    runOnUiThread(() -> {
                        Toast.makeText(this,
                                account.getUsername() + " 重新登录失败",
                                Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(this,
                            "重新登录异常: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
            } finally {
                // 任务完成
                ongoingLoginTasks.decrementAndGet();
                runOnUiThread(this::updateButtonState);
            }
        }).start();
    }

    /**
     * 更新按钮状态（根据当前任务状态）
     */
    private void updateButtonState() {
        runOnUiThread(() -> {
            if (ongoingLoginTasks.get() > 0) {
                // 有登录任务进行中
                updateScanButtonState(false, "账号登录中...", "#9E9E9E");
            } else if (isSignInProgress) {
                // 签到进行中
                updateScanButtonState(false, "签到进行中...", "#FF9800");
            } else if (accountList.isEmpty()) {
                // 没有账号
                updateScanButtonState(true, "添加账号后开始扫码", "#4CAF50");
            } else {
                // 无任务，根据登录状态更新
                Map<String, String> validTokens = sessionManager.getAllValidTokens();
                int readyCount = validTokens.size();
                int totalCount = accountList.size();

                if (readyCount > 0) {
                    @SuppressLint("DefaultLocale") String buttonText = String.format("开始扫码签到 (%d/%d就绪)", readyCount, totalCount);
                    updateScanButtonState(true, buttonText, "#4CAF50");
                } else {
                    updateScanButtonState(true, "开始扫码签到（普通模式）", "#2196F3");
                }
            }
        });
    }

    /**
     * 手动刷新所有账号登录状态
     */
    public void refreshAllLogins() {
        if (ongoingLoginTasks.get() > 0) {
            Toast.makeText(this, "已有登录任务进行中，请稍候", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("重新登录所有账号")
                .setMessage("确定要重新登录所有账号吗？这可能需要一些时间。")
                .setPositiveButton("确定", (dialog, which) -> {
                    // 清空现有会话
                    sessionManager.clearAllSessions();
                    // 重新预登录
                    performAutoLogin();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // 应用从后台恢复时，更新按钮状态
        updateButtonState();
    }
}