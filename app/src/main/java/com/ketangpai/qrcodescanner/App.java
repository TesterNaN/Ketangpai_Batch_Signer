package com.ketangpai.qrcodescanner;

import android.app.Application;
import com.king.wechat.qrcode.WeChatQRCodeDetector;
import org.opencv.OpenCV;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // 1. 初始化 OpenCV
        OpenCV.initOpenCV();
        // 2. 初始化 WeChatQRCodeDetector
        WeChatQRCodeDetector.init(this);
    }
}