package com.ketangpai.qrcodescanner;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Type;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;

public class AccountManager {
    private static final String PREFS_NAME = "encrypted_accounts";
    private static final String KEY_ACCOUNT_LIST = "account_list";
    private SharedPreferences encryptedPrefs;
    private Gson gson = new Gson();

    public AccountManager(Context context) {
        try {
            String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
            encryptedPrefs = EncryptedSharedPreferences.create(
                    PREFS_NAME,
                    masterKeyAlias,
                    context,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (GeneralSecurityException | IOException e) {
            e.printStackTrace();
            encryptedPrefs = context.getSharedPreferences("fallback_" + PREFS_NAME, Context.MODE_PRIVATE);
        }
    }

    public boolean saveAccount(Account account) {
        List<Account> accounts = getAllAccounts();
        for (int i = 0; i < accounts.size(); i++) {
            if (accounts.get(i).getUsername().equals(account.getUsername())) {
                accounts.set(i, account);
                saveAccountList(accounts);
                return true;
            }
        }
        accounts.add(account);
        return saveAccountList(accounts);
    }

    public List<Account> getAllAccounts() {
        String json = encryptedPrefs.getString(KEY_ACCOUNT_LIST, "[]");
        Type type = new TypeToken<List<Account>>() {}.getType();
        List<Account> list = gson.fromJson(json, type);
        return list != null ? list : new ArrayList<>();
    }

    public boolean deleteAccount(Account account) {
        List<Account> accounts = getAllAccounts();
        boolean removed = accounts.removeIf(a -> a.getUsername().equals(account.getUsername()));
        if (removed) {
            saveAccountList(accounts);
        }
        return removed;
    }

    private boolean saveAccountList(List<Account> accounts) {
        String json = gson.toJson(accounts);
        return encryptedPrefs.edit().putString(KEY_ACCOUNT_LIST, json).commit();
    }
}