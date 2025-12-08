package com.ketangpai.qrcodescanner;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.permissionx.guolindev.PermissionX;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private Button btnScan;
    private Button btnAddAccount;
    private TextView tvResult;
    private AccountAdapter adapter;
    private final List<Account> accountList = new ArrayList<>();
    private AccountManager accountManager;
    private String scannedUrl = "";
    private static final int REQUEST_SCAN = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        accountManager = new AccountManager(this);
        initViews();
        loadAccounts();
        setupListeners();
    }

    private void initViews() {
        btnScan = findViewById(R.id.btn_scan);
        btnAddAccount = findViewById(R.id.btn_add_account);
        tvResult = findViewById(R.id.tv_result);
        RecyclerView recyclerView = findViewById(R.id.recycler_view);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AccountAdapter(accountList);
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
        btnScan.setOnClickListener(v -> PermissionX.init(this)
                .permissions(android.Manifest.permission.CAMERA)
                .request((allGranted, grantedList, deniedList) -> {
                    if (allGranted) {
                        startScanActivity();
                    } else {
                        tvResult.setText("需要相机权限才能扫描二维码");
                    }
                }));

        btnAddAccount.setOnClickListener(v -> showAddAccountDialog());

        adapter.setOnDeleteClickListener((account, position) -> new AlertDialog.Builder(this)
                .setTitle("确认删除")
                .setMessage("确定要删除账号 " + account.getUsername() + " 吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    if (accountManager.deleteAccount(account)) {
                        accountList.remove(position);
                        adapter.notifyItemRemoved(position);
                        tvResult.setText("账号已删除: " + account.getUsername());
                    }
                })
                .setNegativeButton("取消", null)
                .show());
    }

    private void startScanActivity() {
        if (accountList.isEmpty()) {
            tvResult.setText("请先添加至少一个签到账号");
            return;
        }
        Intent intent = new Intent(this, QRCodeScanActivity.class);
        startActivityForResult(intent, REQUEST_SCAN);
    }

    @SuppressLint("SetTextI18n")
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SCAN && resultCode == RESULT_OK && data != null) {
            scannedUrl = data.getStringExtra("SCAN_RESULT");
            tvResult.setText("二维码已识别，正在自动签到...");
            startSignProcess();
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private void startSignProcess() {
        new Thread(() -> {
            NetworkManager networkManager = new NetworkManager();
            String result = networkManager.processAllAccounts(scannedUrl, accountList);
            runOnUiThread(() -> {
                tvResult.setText(result);
                adapter.notifyDataSetChanged();
                btnScan.setText("重新扫描二维码");
            });
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
            }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }
}