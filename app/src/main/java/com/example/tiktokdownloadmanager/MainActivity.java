package com.example.tiktokdownloadmanager;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.view.Gravity;
import android.view.View;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.LinkedHashSet;

public class MainActivity extends Activity {

    private static final String SAVE_URL = "https://savetiktok.to/id";
    private static final int REQUEST_STORAGE = 501;

    private EditText input;
    private LinearLayout queue;
    private TextView progress;
    private TextView webStatus;
    private WebView webView;

    private final ArrayList<String> urls = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private int currentIndex = -1;
    private boolean processing = false;
    private boolean downloadStarted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        configureWebView();
        requestStoragePermissionIfNeeded();
    }

    private TextView makeText(String value, float size) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(Color.rgb(17, 24, 39));
        t.setPadding(0, 8, 0, 8);
        return t;
    }

    private Button makeButton(String value) {
        Button b = new Button(this);
        b.setText(value);
        b.setAllCaps(false);
        return b;
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(22, 22, 22, 22);
        root.setBackgroundColor(Color.rgb(244, 245, 247));

        root.addView(makeText("TikTok Download Manager", 27));
        root.addView(makeText("WebView + antrean otomatis", 16));

        input = new EditText(this);
        input.setHint("Tempel link TikTok, satu per baris");
        input.setGravity(Gravity.TOP);
        input.setMinLines(5);
        input.setInputType(
                android.text.InputType.TYPE_CLASS_TEXT
                        | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                        | android.text.InputType.TYPE_TEXT_VARIATION_URI
        );
        root.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 250
        ));

        LinearLayout controls = new LinearLayout(this);
        Button load = makeButton("Muat Antrean");
        Button clear = makeButton("Bersihkan");

        controls.addView(load, new LinearLayout.LayoutParams(0, 62, 1));

        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(0, 62, 1);
        clearParams.setMargins(10, 0, 0, 0);
        controls.addView(clear, clearParams);
        root.addView(controls);

        progress = makeText("Progress: 0 / 0", 18);
        root.addView(progress);

        webStatus = makeText("WebView: siap", 14);
        root.addView(webStatus);

        Button startAll = makeButton("Mulai Semua");
        root.addView(startAll);

        root.addView(makeText(
                "Aplikasi memakai halaman SaveTikTok secara normal. " +
                "CAPTCHA, login, rate limit, dan mekanisme keamanan tidak dilewati. " +
                "Jika situs meminta tindakan manual, selesaikan di WebView.",
                13
        ));

        queue = new LinearLayout(this);
        queue.setOrientation(LinearLayout.VERTICAL);
        root.addView(queue);

        webView = new WebView(this);
        webView.setVisibility(View.GONE);
        root.addView(webView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 700
        ));

        load.setOnClickListener(v -> loadQueue());
        clear.setOnClickListener(v -> clearQueue());
        startAll.setOnClickListener(v -> startAll());

        scroll.addView(root);
        setContentView(scroll);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccess(false);
        settings.setSupportMultipleWindows(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");

        webView.setWebChromeClient(new WebChromeClient());

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(
                    WebView view, WebResourceRequest request) {
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                webStatus.setText("WebView: halaman siap");

                if (processing && currentIndex >= 0) {
                    final String target = urls.get(currentIndex);
                    handler.postDelayed(() -> fillUrl(target), 1000);
                }
            }

            @Override
            public void onReceivedError(
                    WebView view,
                    WebResourceRequest request,
                    WebResourceError error) {
                if (request.isForMainFrame()) {
                    webStatus.setText("WebView: gagal memuat halaman");
                }
            }
        });

        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(
                    String url,
                    String userAgent,
                    String contentDisposition,
                    String mimeType,
                    long contentLength) {

                if (!processing || currentIndex < 0) {
                    return;
                }

                downloadStarted = true;
                startAndroidDownload(
                        url, userAgent, contentDisposition, mimeType
                );
            }
        });
    }

    private void loadQueue() {
        LinkedHashSet<String> unique = new LinkedHashSet<>();

        String[] lines = input.getText().toString().split("\\r?\\n");

        for (String line : lines) {
            String url = line.trim();

            if ((url.startsWith("https://") || url.startsWith("http://"))
                    && url.toLowerCase().contains("tiktok")) {
                unique.add(url);
            }
        }

        urls.clear();
        urls.addAll(unique);

        currentIndex = -1;
        processing = false;
        downloadStarted = false;

        queue.removeAllViews();

        for (int i = 0; i < urls.size(); i++) {
            addQueueItem(i, urls.get(i));
        }

        updateProgress();
    }

    private void addQueueItem(int index, String url) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(12, 12, 12, 12);

        TextView status = makeText(
                "#" + (index + 1) + "  Menunggu", 16
        );

        TextView link = makeText(url, 12);
        link.setMaxLines(4);

        Button process = makeButton("Proses Link Ini");
        process.setOnClickListener(v -> processSingle(index));

        card.addView(status);
        card.addView(link);
        card.addView(process);

        card.setTag(status);
        queue.addView(card);
    }

    private void processSingle(int index) {
        if (index < 0 || index >= urls.size()) {
            return;
        }

        if (processing) {
            Toast.makeText(
                    this,
                    "Sedang memproses link lain.",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        currentIndex = index;
        processing = true;
        downloadStarted = false;

        setStatus(index, "Memproses");
        webView.setVisibility(View.VISIBLE);
        webStatus.setText("WebView: membuka SaveTikTok...");

        webView.loadUrl(SAVE_URL);
    }

    private void startAll() {
        if (urls.isEmpty()) {
            Toast.makeText(
                    this,
                    "Masukkan link TikTok lalu tekan Muat Antrean.",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        if (processing) {
            Toast.makeText(
                    this,
                    "Antrean sedang diproses.",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        processSingle(0);
    }

    private void fillUrl(String targetUrl) {
        if (!processing || currentIndex < 0) {
            return;
        }

        String jsUrl = toJavaScriptString(targetUrl);

        String javascript =
                "(function() {" +
                "var target = " + jsUrl + ";" +

                "var fields = Array.prototype.slice.call(" +
                "document.querySelectorAll('input, textarea')" +
                ");" +

                "var field = null;" +

                "for (var i = 0; i < fields.length; i++) {" +
                "  var f = fields[i];" +
                "  var hint = (" +
                "    (f.placeholder || '') + ' ' +" +
                "    (f.name || '') + ' ' +" +
                "    (f.id || '') + ' ' +" +
                "    (f.getAttribute('aria-label') || '')" +
                "  ).toLowerCase();" +

                "  if (f.type === 'url' ||" +
                "      hint.indexOf('url') >= 0 ||" +
                "      hint.indexOf('link') >= 0 ||" +
                "      hint.indexOf('tautan') >= 0 ||" +
                "      hint.indexOf('tiktok') >= 0) {" +
                "    field = f;" +
                "    break;" +
                "  }" +
                "}" +

                "if (!field && fields.length > 0) field = fields[0];" +

                "if (!field) {" +
                "  AndroidBridge.status('Kolom URL tidak ditemukan');" +
                "  return 'NO_FIELD';" +
                "}" +

                "try {" +
                "  var proto = field.tagName.toLowerCase() === 'textarea' " +
                "    ? HTMLTextAreaElement.prototype " +
                "    : HTMLInputElement.prototype;" +
                "  var desc = Object.getOwnPropertyDescriptor(proto, 'value');" +
                "  if (desc && desc.set) desc.set.call(field, target);" +
                "  else field.value = target;" +
                "} catch (e) {" +
                "  field.value = target;" +
                "}" +

                "field.dispatchEvent(new Event('input', {bubbles:true}));" +
                "field.dispatchEvent(new Event('change', {bubbles:true}));" +
                "field.focus();" +

                "AndroidBridge.status('URL sudah dimasukkan');" +

                "setTimeout(function() {" +
                "  var controls = Array.prototype.slice.call(" +
                "    document.querySelectorAll(" +
                "      'button, input[type=submit], input[type=button]'" +
                "    )" +
                "  );" +

                "  var submit = null;" +

                "  for (var j = 0; j < controls.length; j++) {" +
                "    var c = controls[j];" +
                "    var txt = (" +
                "      (c.innerText || '') + ' ' +" +
                "      (c.value || '') + ' ' +" +
                "      (c.getAttribute('aria-label') || '')" +
                "    ).toLowerCase();" +

                "    if (" +
                "      txt.indexOf('download') >= 0 ||" +
                "      txt.indexOf('unduh') >= 0 ||" +
                "      txt.indexOf('submit') >= 0 ||" +
                "      txt.indexOf('search') >= 0 ||" +
                "      txt.indexOf('cari') >= 0" +
                "    ) {" +
                "      submit = c;" +
                "      break;" +
                "    }" +
                "  }" +

                "  if (submit) {" +
                "    submit.click();" +
                "    AndroidBridge.status('Tombol proses ditemukan dan ditekan');" +
                "  } else {" +
                "    AndroidBridge.status(" +
                "      'URL dimasukkan; tekan tombol Download/Unduh jika belum diproses'" +
                "    );" +
                "  }" +
                "}, 600);" +

                "return 'OK';" +
                "})()";

        webView.evaluateJavascript(javascript, null);

        handler.postDelayed(
                this::lookForDirectDownload,
                5000
        );
    }

    private String toJavaScriptString(String value) {
        String escaped = value
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\r", "\\r")
                .replace("\n", "\\n");

        return "'" + escaped + "'";
    }

    private void lookForDirectDownload() {
        if (!processing || currentIndex < 0 || downloadStarted) {
            return;
        }

        String javascript =
                "(function() {" +
                "var links = Array.prototype.slice.call(" +
                "document.querySelectorAll('a[href]')" +
                ");" +

                "for (var i = 0; i < links.length; i++) {" +
                "  var a = links[i];" +
                "  var href = a.href || '';" +
                "  var text = (" +
                "    (a.innerText || '') + ' ' +" +
                "    (a.textContent || '') + ' ' +" +
                "    (a.getAttribute('aria-label') || '')" +
                "  ).toLowerCase();" +

                "  var media = /\\.mp4(\\?|$)|\\.mp3(\\?|$)/i.test(href);" +
                "  var downloadText =" +
                "    text.indexOf('download') >= 0 ||" +
                "    text.indexOf('unduh') >= 0;" +

                "  if (media && downloadText) {" +
                "    return href;" +
                "  }" +
                "}" +

                "return '';" +
                "})()";

        webView.evaluateJavascript(javascript, value -> {
            if (value == null || value.length() <= 2) {
                if (processing && !downloadStarted) {
                    handler.postDelayed(
                            this::lookForDirectDownload,
                            2500
                    );
                }
                return;
            }

            String url = value;

            if (url.startsWith("\"") && url.endsWith("\"")) {
                url = url.substring(1, url.length() - 1)
                        .replace("\\/", "/")
                        .replace("\\\"", "\"");
            }

            if (url.startsWith("http")) {
                webStatus.setText("WebView: tautan video ditemukan");
                webView.loadUrl(url);
            } else if (processing && !downloadStarted) {
                handler.postDelayed(
                        this::lookForDirectDownload,
                        2500
                );
            }
        });
    }

    private void startAndroidDownload(
            String url,
            String userAgent,
            String contentDisposition,
            String mimeType) {

        try {
            DownloadManager manager =
                    (DownloadManager) getSystemService(
                            Context.DOWNLOAD_SERVICE
                    );

            DownloadManager.Request request =
                    new DownloadManager.Request(Uri.parse(url));

            String fileName =
                    "tiktok_" + System.currentTimeMillis() + ".mp4";

            request.setTitle(fileName);
            request.setDescription("TikTok Download Manager");
            request.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            );

            if (mimeType != null && !mimeType.isEmpty()) {
                request.setMimeType(mimeType);
            }

            if (userAgent != null && !userAgent.isEmpty()) {
                request.addRequestHeader("User-Agent", userAgent);
            }

            String cookie = CookieManager
                    .getInstance()
                    .getCookie(url);

            if (cookie != null && !cookie.isEmpty()) {
                request.addRequestHeader("Cookie", cookie);
            }

            request.setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    fileName
            );

            manager.enqueue(request);

            int completedIndex = currentIndex;

            setStatus(
                    completedIndex,
                    "Download dimulai"
            );

            progress.setText(
                    "Progress: " +
                    (completedIndex + 1) +
                    " / " +
                    urls.size()
            );

            Toast.makeText(
                    this,
                    "Download dimulai: " + fileName,
                    Toast.LENGTH_SHORT
            ).show();

            handler.postDelayed(() -> {
                processing = false;

                int next = completedIndex + 1;

                if (next < urls.size()) {
                    processSingle(next);
                } else {
                    currentIndex = -1;
                    webStatus.setText(
                            "WebView: semua antrean selesai"
                    );

                    Toast.makeText(
                            MainActivity.this,
                            "Semua link sudah diproses.",
                            Toast.LENGTH_LONG
                    ).show();
                }
            }, 1500);

        } catch (Exception e) {
            processing = false;

            setStatus(
                    currentIndex,
                    "Gagal memulai download"
            );

            webStatus.setText(
                    "WebView: " + e.getMessage()
            );
        }
    }

    private void setStatus(int index, String value) {
        if (index < 0 || index >= queue.getChildCount()) {
            return;
        }

        View card = queue.getChildAt(index);
        Object tag = card.getTag();

        if (tag instanceof TextView) {
            ((TextView) tag).setText(
                    "#" + (index + 1) + "  " + value
            );
        }
    }

    private void updateProgress() {
        progress.setText(
                "Progress: 0 / " + urls.size()
        );
    }

    private void clearQueue() {
        urls.clear();
        currentIndex = -1;
        processing = false;
        downloadStarted = false;

        queue.removeAllViews();

        input.setText("");

        progress.setText("Progress: 0 / 0");
        webStatus.setText("WebView: siap");
        webView.setVisibility(View.GONE);
    }

    private void requestStoragePermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 23 &&
                Build.VERSION.SDK_INT <= 28 &&
                checkSelfPermission(
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED) {

            requestPermissions(
                    new String[]{
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                    },
                    REQUEST_STORAGE
            );
        }
    }

    private class AndroidBridge {
        @JavascriptInterface
        public void status(final String message) {
            runOnUiThread(() ->
                    webStatus.setText(
                            "WebView: " + message
                    )
            );
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null &&
                webView.getVisibility() == View.VISIBLE &&
                webView.canGoBack()) {

            webView.goBack();
            return;
        }

        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);

        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }

        super.onDestroy();
    }
}
