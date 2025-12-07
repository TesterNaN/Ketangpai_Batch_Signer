package com.ketangpai.qrcodescanner;

// 确保Account类可被Gson序列化/反序列化
public class Account {
    private String username;
    private String password;
    private String status;

    // 必须有这个无参构造函数供Gson使用
    public Account() {
    }

    public Account(String username, String password) {
        this.username = username;
        this.password = password;
        this.status = "未开始";
    }

    // Getter 和 Setter 方法
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}