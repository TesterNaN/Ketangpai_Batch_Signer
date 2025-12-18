package com.ketangpai.qrcodescanner;

import android.annotation.SuppressLint;
import android.util.Log;

import com.google.gson.Gson;

import okhttp3.*;

import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class NetworkManager {
    private static final String TAG = "NetworkManager";
    private static final String LOGIN_URL = "https://openapiv5.ketangpai.com/UserApi/login";
    private static final String SIGN_URL = "https://openapiv5.ketangpai.com/AttenceApi/AttenceResult";

    private final OkHttpClient client;
    private final Gson gson = new Gson();

    public NetworkManager() {
        client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build();
    }

    // 原有方法保持不变
    public String processAllAccounts(String signUrl, List<Account> accounts) {
        StringBuilder result = new StringBuilder();
        for (Account account : accounts) {
            String accountResult = processSingleAccount(signUrl, account);
            result.append(accountResult).append("\n\n");
            account.setStatus(accountResult.contains("成功") ? "签到成功" : "签到失败");
        }
        return result.toString();
    }

    private String processSingleAccount(String signUrl, Account account) {
        try {
            String token = login(account);
            if (token == null) {
                return String.format("用户 %s 登录失败", account.getUsername());
            }
            JSONObject signData = parseUrlToJson(signUrl);
            return submitSign(token, signData, account.getUsername());
        } catch (Exception e) {
            Log.e(TAG, "处理账号异常: " + account.getUsername(), e);
            return String.format("用户 %s 处理异常: %s", account.getUsername(), e.getMessage());
        }
    }

    private String login(Account account) throws IOException {
        long timestamp = System.currentTimeMillis();
        @SuppressLint("DefaultLocale") String requestBody = String.format(
                "{\"email\":\"%s\",\"password\":\"%s\",\"remember\":\"1\",\"source_type\":1,\"reqtimestamp\":%d}",
                account.getUsername(), account.getPassword(), timestamp
        );

        Request request = new Request.Builder()
                .url(LOGIN_URL)
                .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
                .addHeader("Accept", "application/json, text/plain, */*")
                .addHeader("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .addHeader("Content-Type", "application/json")
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) AppleWebKit/537.36")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String responseBody = response.body().string();
                JSONObject json = new JSONObject(responseBody);
                if (json.optInt("code", 0) == 10000) {
                    return json.getJSONObject("data").optString("token");
                } else {
                    Log.e(TAG, "登录失败: " + json.optString("message"));
                }
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    private JSONObject parseUrlToJson(String urlString) throws Exception {
        URL url = new URL(urlString);
        String query = url.getQuery();
        String[] pairs = query.split("&");

        JSONObject result = new JSONObject();
        for (String pair : pairs) {
            String[] keyValue = pair.split("=");
            if (keyValue.length == 2) {
                result.put(keyValue[0], keyValue[1]);
            }
        }
        result.put("reqtimestamp", System.currentTimeMillis());
        return result;
    }

    @SuppressLint("DefaultLocale")
    private String submitSign(String token, JSONObject signData, String username) throws IOException {
        Request request = new Request.Builder()
                .url(SIGN_URL)
                .post(RequestBody.create(signData.toString(), MediaType.parse("application/json")))
                .addHeader("Accept", "application/json, text/plain, */*")
                .addHeader("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .addHeader("Content-Type", "application/json")
                .addHeader("token", token)
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) AppleWebKit/537.36")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String responseBody = response.body().string();
                JSONObject json = new JSONObject(responseBody);
                if (json.optInt("code", 0) == 10000) {
                    return String.format("用户 %s 签到成功", username);
                } else {
                    return String.format("用户 %s 签到失败: %s",
                            username, json.optString("message", "未知错误"));
                }
            } else {
                return String.format("用户 %s 网络请求失败: %d",
                        username, response.code());
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    // 新增方法：独立登录获取token
    public String loginAndGetToken(Account account) throws IOException {
        long timestamp = System.currentTimeMillis();
        @SuppressLint("DefaultLocale") String requestBody = String.format(
                "{\"email\":\"%s\",\"password\":\"%s\",\"remember\":\"1\",\"source_type\":1,\"reqtimestamp\":%d}",
                account.getUsername(), account.getPassword(), timestamp
        );

        Request request = new Request.Builder()
                .url(LOGIN_URL)
                .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
                .addHeader("Accept", "application/json, text/plain, */*")
                .addHeader("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .addHeader("Content-Type", "application/json")
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) AppleWebKit/537.36")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String responseBody = response.body().string();
                JSONObject json = new JSONObject(responseBody);
                if (json.optInt("code", 0) == 10000) {
                    String token = json.getJSONObject("data").optString("token");
                    Log.i(TAG, account.getUsername() + " 登录成功，获取到token");
                    return token;
                } else {
                    String errorMsg = json.optString("message", "未知错误");
                    Log.e(TAG, account.getUsername() + " 登录失败: " + errorMsg);
                    return null;
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "JSON解析异常: " + e.getMessage());
            return null;
        }
        return null;
    }

    // 新增方法：快速签到（使用已有token）
    @SuppressLint("DefaultLocale")
    public String quickSignWithToken(String signUrl, String token, String username) throws Exception {
        JSONObject signData = parseUrlToJson(signUrl);
        Request request = new Request.Builder()
                .url(SIGN_URL)
                .post(RequestBody.create(signData.toString(), MediaType.parse("application/json")))
                .addHeader("Accept", "application/json, text/plain, */*")
                .addHeader("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .addHeader("Content-Type", "application/json")
                .addHeader("token", token)
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) AppleWebKit/537.36")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String responseBody = response.body().string();
                JSONObject json = new JSONObject(responseBody);
                if (json.optInt("code", 0) == 10000) {
                    Log.i(TAG, username + " 快速签到成功");
                    return String.format("用户 %s 签到成功（快速）", username);
                } else {
                    String errorMsg = json.optString("message", "未知错误");
                    Log.w(TAG, username + " 快速签到失败: " + errorMsg);
                    return String.format("用户 %s 签到失败: %s", username, errorMsg);
                }
            } else {
                Log.w(TAG, username + " 网络请求失败: " + response.code());
                return String.format("用户 %s 网络请求失败: %d", username, response.code());
            }
        }
    }

    // 新增方法：批量快速签到
    @SuppressLint("DefaultLocale")
    public String quickSignReadyAccounts(String signUrl, List<Account> accounts,
                                         Map<String, String> tokenMap) {
        StringBuilder result = new StringBuilder();
        int successCount = 0;

        for (Account account : accounts) {
            String token = tokenMap.get(account.getUsername());
            if (token != null && !token.isEmpty()) {
                try {
                    String accountResult = quickSignWithToken(signUrl, token, account.getUsername());
                    result.append(accountResult).append("\n\n");

                    if (accountResult.contains("成功")) {
                        account.setStatus("签到成功");
                        successCount++;
                    } else {
                        account.setStatus("签到失败");
                    }
                } catch (Exception e) {
                    String error = String.format("用户 %s 快速签到异常: %s",
                            account.getUsername(), e.getMessage());
                    result.append(error).append("\n\n");
                    account.setStatus("签到失败");
                    Log.e(TAG, error, e);
                }
            } else {
                result.append(String.format("用户 %s: 未登录，跳过\n\n", account.getUsername()));
            }

            try { Thread.sleep(200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }

        result.append(String.format("\n快速签到完成: %d/%d 个账号", successCount, tokenMap.size()));
        return result.toString();
    }

    public Gson getGson() {
        return gson;
    }
}