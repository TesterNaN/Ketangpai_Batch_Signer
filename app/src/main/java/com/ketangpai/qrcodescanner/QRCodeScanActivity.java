package com.ketangpai.qrcodescanner;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.king.camera.scan.AnalyzeResult;
import com.king.wechat.qrcode.scanning.WeChatCameraScanActivity;
import com.king.wechat.qrcode.scanning.analyze.WeChatScanningAnalyzer;
import com.king.camera.scan.analyze.Analyzer;
import java.util.List;

public class QRCodeScanActivity extends WeChatCameraScanActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 此处可以设置界面，不设置则使用库默认界面
    }

    // 创建分析器
    @Override
    public Analyzer<List<String>> createAnalyzer() {
        // 参数传 false 表示不返回二维码位置信息（你只需要内容）
        return new WeChatScanningAnalyzer(false);
    }

    // 扫码结果回调（核心）
    @Override
    public void onScanResultCallback(@NonNull AnalyzeResult<List<String>> result) {
        // 1. 暂停扫描，防止重复处理
        getCameraScan().setAnalyzeImage(false);

        // 2. 获取结果列表
        List<String> results = result.getResult();
        if (results != null && !results.isEmpty()) {
            // 3. 遍历所有识别到的二维码文本
            for (String text : results) {
                // 4. 检查是否为课堂派签到链接
                if (text.contains("ketangpai.com/checkIn/checkinCodeResult")) {
                    // 5. 找到目标，返回结果并关闭页面
                    Intent data = new Intent();
                    data.putExtra("SCAN_RESULT", text);
                    setResult(RESULT_OK, data);
                    finish();
                    return; // 结束方法
                }
            }
            // 6. 如果遍历完都没找到目标链接
            runOnUiThread(() -> Toast.makeText(this, "未识别到有效的签到二维码", Toast.LENGTH_SHORT).show());
            // 2秒后恢复扫描，让用户可以扫其他码
            new android.os.Handler(getMainLooper()).postDelayed(() -> getCameraScan().setAnalyzeImage(true), 2000);
        } else {
            // 7. 未识别到任何内容，直接恢复扫描
            getCameraScan().setAnalyzeImage(true);
        }
    }
}