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

    // 处理所有账号
    public String processAllAccounts(String signUrl, List<Account> accounts) {
        StringBuilder result = new StringBuilder();

        for (Account account : accounts) {
            String accountResult = processSingleAccount(signUrl, account);
            result.append(accountResult).append("\n\n");

            // 更新账号状态
            account.setStatus(accountResult.contains("成功") ? "签到成功" : "签到失败");
        }

        return result.toString();
    }

    // 处理单个账号
    private String processSingleAccount(String signUrl, Account account) {
        try {
            // 1. 登录获取token
            String token = login(account);
            if (token == null) {
                return String.format("用户 %s 登录失败", account.getUsername());
            }

            // 2. 解析URL参数
            JSONObject signData = parseUrlToJson(signUrl);

            // 3. 提交签到
            return submitSign(token, signData, account.getUsername());

        } catch (Exception e) {
            Log.e(TAG, "处理账号异常: " + account.getUsername(), e);
            return String.format("用户 %s 处理异常: %s", account.getUsername(), e.getMessage());
        }
    }

    // 登录方法
    private String login(Account account) throws IOException {
        long timestamp = System.currentTimeMillis();

        // 构建登录请求体
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

    // 解析URL为JSON
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

        // 添加时间戳
        result.put("reqtimestamp", System.currentTimeMillis());

        return result;
    }

    // 提交签到
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
}