package com.jsosowo.winemu;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Opens Microsoft's official Windows 11 download page. When the user taps the download button
 * there, the ISO is saved straight into the VM folder by {@link VmService}.
 */
public class IsoDownloadActivity extends Activity {
    public static final String EXTRA_ARM64 = "arm64";
    private WebView web;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        boolean arm64 = getIntent().getBooleanExtra(EXTRA_ARM64, false);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        TextView hint = new TextView(this);
        hint.setText((arm64 ? "Windows 11 ARM64" : "Windows 11 x64")
                + " — 아래 Microsoft 공식 페이지에서 '다운로드 선택' → 제품 언어(한국어) → '확인' → "
                + "'다운로드'를 누르면 앱이 자동으로 VM용 ISO로 저장합니다.");
        hint.setTextColor(Color.WHITE);
        hint.setBackgroundColor(0xFF0067C0);
        hint.setPadding(24, 20, 24, 20);
        hint.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(hint);

        web = new WebView(this);
        root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        if (Build.VERSION.SDK_INT >= 21) {
            android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        }

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                String u = req.getUrl().toString();
                if (looksLikeIso(u)) { startDownload(u); return true; }
                return false;
            }
        });
        web.setDownloadListener((url, ua, cd, mime, len) -> startDownload(url));
        web.loadUrl(IsoInfo.pageUrl(arm64));
    }

    private static boolean looksLikeIso(String u) {
        String p = android.net.Uri.parse(u).getPath();
        return p != null && p.toLowerCase(java.util.Locale.ROOT).endsWith(".iso");
    }

    private void startDownload(String url) {
        if (VmService.running) {
            Toast.makeText(this, "VM을 종료한 뒤 다운로드하세요.", Toast.LENGTH_LONG).show();
            return;
        }
        Intent i = new Intent(this, VmService.class)
                .setAction(VmService.ACTION_DOWNLOAD_ISO)
                .putExtra(VmService.EXTRA_URL, url);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
        Toast.makeText(this, "ISO 다운로드를 시작했습니다: " + IsoInfo.fileNameFromUrl(url),
                Toast.LENGTH_LONG).show();
        finish();
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack(); else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        web.destroy();
        super.onDestroy();
    }
}
