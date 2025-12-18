package com.ketangpai.qrcodescanner;

import android.util.Log;
import java.util.HashMap;
import java.util.Map;

public class KetangpaiSessionManager {
    private static final String TAG = "KTP_Session";
    private static KetangpaiSessionManager instance;

    public static class KtpSession {
        private final String username;
        private final String token;
        private final long loginTime;
        private boolean isValid;

        public KtpSession(String username, String token) {
            this.username = username;
            this.token = token;
            this.loginTime = System.currentTimeMillis();
            this.isValid = true;
        }

        public boolean isTokenValid() {
            if (!isValid) return false;

            long currentTime = System.currentTimeMillis();
            long hoursSinceLogin = (currentTime - loginTime) / (1000 * 60 * 60);

            boolean valid = hoursSinceLogin < 2;

            if (!valid) {
                Log.i(TAG, username + " 的token已过期 (" + hoursSinceLogin + "小时)");
                isValid = false;
            }

            return valid;
        }

        public String getToken() { return token; }
        public long getLoginTime() { return loginTime; }
    }

    private final Map<String, KtpSession> sessionMap = new HashMap<>();

    public static synchronized KetangpaiSessionManager getInstance() {
        if (instance == null) {
            instance = new KetangpaiSessionManager();
        }
        return instance;
    }

    private KetangpaiSessionManager() {}

    public void addSession(String username, String token) {
        KtpSession session = new KtpSession(username, token);
        sessionMap.put(username, session);
        Log.i(TAG, "已保存会话: " + username);
    }

    public KtpSession getSession(String username) {
        return sessionMap.get(username);
    }

    public void removeSession(String username) {
        sessionMap.remove(username);
        Log.i(TAG, "已移除会话: " + username);
    }

    public Map<String, String> getAllValidTokens() {
        Map<String, String> validTokens = new HashMap<>();
        for (Map.Entry<String, KtpSession> entry : sessionMap.entrySet()) {
            if (entry.getValue().isTokenValid()) {
                validTokens.put(entry.getKey(), entry.getValue().getToken());
            }
        }
        return validTokens;
    }

}