package com.ketangpai.qrcodescanner;

public class Account {
    private String username;
    private String password;
    private transient String status; // 关键修改：transient 阻止 Gson 序列化
    private boolean signEnabled = true;
    private String remark = "";

    // 无参构造函数（Gson 反序列化时调用）
    public Account() {
        this.status = "未开始"; // 反序列化后赋予默认值
    }

    public Account(String username, String password) {
        this.username = username;
        this.password = password;
        this.status = "未开始";
        this.signEnabled = true;
        this.remark = "";
    }

    // Getter 和 Setter
    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isSignEnabled() {
        return signEnabled;
    }

    public void setSignEnabled(boolean signEnabled) {
        this.signEnabled = signEnabled;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}