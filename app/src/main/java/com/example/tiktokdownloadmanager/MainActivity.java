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
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
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
import android.widget.Toast;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String SAVE_URL = "https://savetiktok.to/id";
    private static final int REQ_STORAGE = 501;
    private static final int MAX_INPUT_CHECKS = 20;
    private static final int MAX_MP4_HD_CHECKS = 30;

    private EditText input;
    private LinearLayout queue;
    private TextView progress, webStatus;
    private WebView webView;
    private final ArrayList<String> urls = new ArrayList<>();
    private int currentIndex = -1;
    private boolean processing = false;
    private boolean submitClicked = false;
    private boolean downloadStarted = false;
    private String currentCaption = "";
    private long currentDownloadId = -1L;
    private final LinkedHashSet<String> handledDownloadUrls = new LinkedHashSet<>();
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        requestStorageIfNeeded();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(22, 22, 22, 22);
        root.setBackgroundColor(Color.rgb(244, 245, 247));

        root.addView(text("TikTok Download Manager", 28));
        root.addView(text("WebView + antrean otomatis", 17));

        input = new EditText(this);
        input.setHint("Tempel link TikTok, satu per baris");
        input.setGravity(Gravity.TOP);
        input.setMinLines(5);
        root.addView(input, new LinearLayout.LayoutParams(-1, 250));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button load = button("Muat Antrean");
        Button clear = button("Bersihkan");
        row.addView(load, new LinearLayout.LayoutParams(0, 62, 1));
        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(0, 62, 1);
        clearParams.setMargins(10, 0, 0, 0);
        row.addView(clear, clearParams);
        root.addView(row);

        progress = text("Progress: 0 / 0", 18);
        root.addView(progress);
        webStatus = text("WebView: siap", 14);
        root.addView(webStatus);

        Button startAll = button("Mulai Semua");
        root.addView(startAll);
        root.addView(text("Aplikasi memproses URL melalui halaman SaveTikTok di WebView. CAPTCHA, login, rate limit, dan mekanisme keamanan tidak dilewati. Jika situs meminta tindakan manual, selesaikan secara manual di WebView.", 14));

        queue = new LinearLayout(this);
        queue.setOrientation(LinearLayout.VERTICAL);
        root.addView(queue);

        webView = new WebView(this);
        webView.setVisibility(View.GONE);
        root.addView(webView, new LinearLayout.LayoutParams(-1, 650));
        configureWebView();

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
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setSupportMultipleWindows(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setUserAgentString("Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 Mobile Safari/537.36");

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        webView.setWebChromeClient(new WebChromeClient());

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (!processing || currentIndex < 0) {
                    webStatus.setText("WebView: halaman siap");
                    return;
                }

                if (!isSaveTikTokPage(url)) {
                    webStatus.setText("WebView: membuka SaveTikTok...");
                    return;
                }

                if (!submitClicked) {
                    webStatus.setText("WebView: mencari kolom URL...");
                    handler.postDelayed(() -> waitForSaveTikTokInput(urls.get(currentIndex), 0), 1000);
                } else {
                    webStatus.setText("WebView: menunggu hasil...");
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    webStatus.setText("WebView: gagal memuat halaman");
                }
            }
        });

        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) ->
                handleWebDownload(url, userAgent, contentDisposition, mimeType, contentLength));
    }

    private boolean isSaveTikTokPage(String url) {
        try {
            String host = Uri.parse(url).getHost();
            return host != null && host.toLowerCase(Locale.US).contains("savetiktok.to");
        } catch (Exception e) {
            return false;
        }
    }

    private void loadQueue() {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String line : input.getText().toString().split("\\r?\\n")) {
            String url = line.trim();
            if (url.startsWith("http://") || url.startsWith("https://")) unique.add(url);
        }

        urls.clear();
        urls.addAll(unique);
        currentIndex = -1;
        processing = false;
        submitClicked = false;
        downloadStarted = false;
        currentCaption = "";
        currentDownloadId = -1L;
        handledDownloadUrls.clear();
        handler.removeCallbacksAndMessages(null);
        queue.removeAllViews();

        for (int i = 0; i < urls.size(); i++) addQueueItem(i, urls.get(i));

        updateProgress();
        webStatus.setText(urls.isEmpty() ? "WebView: tidak ada URL" : "WebView: antrean siap");
    }

    private void addQueueItem(int index, String url) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(14, 14, 14, 14);

        TextView status = text("#" + (index + 1) + "  Menunggu", 16);
        TextView link = text(url, 12);
        link.setMaxLines(3);

        Button process = button("Proses Link Ini");
        process.setOnClickListener(v -> startSingle(index));

        card.addView(status);
        card.addView(link);
        card.addView(process);
        card.setTag(status);
        queue.addView(card);
    }

    private void startSingle(int index) {
        if (index < 0 || index >= urls.size()) return;

        handler.removeCallbacksAndMessages(null);
        currentIndex = index;
        processing = true;
        submitClicked = false;
        downloadStarted = false;
        currentCaption = "";
        currentDownloadId = -1L;

        setStatus(index, "Memproses");
        progress.setText("Progress: " + (index + 1) + " / " + urls.size());
        webStatus.setText("WebView: membuka SaveTikTok...");
        webView.setVisibility(View.GONE);
        webView.loadUrl(SAVE_URL);
    }

    private void startAll() {
        if (urls.isEmpty()) {
            Toast.makeText(this, "Muat antrean dulu.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!processing) startSingle(0);
    }

    private void waitForSaveTikTokInput(String tiktokUrl, int attempt) {
        if (!processing || currentIndex < 0 || submitClicked) return;

        if (attempt >= MAX_INPUT_CHECKS) {
            webStatus.setText("WebView: kolom URL tidak ditemukan");
            setStatus(currentIndex, "Kolom URL SaveTikTok tidak ditemukan");
            return;
        }

        String js = "(function(){" +
                "var fields=Array.from(document.querySelectorAll('input,textarea'));" +
                "var field=fields.find(function(el){" +
                "var p=((el.placeholder||'')+' '+(el.getAttribute('aria-label')||'')+' '+(el.name||'')+' '+(el.type||'')).toLowerCase();" +
                "return p.includes('tautan')||p.includes('tiktok')||p.includes('link')||p.includes('url')||el.type==='url';" +
                "});" +
                "return field?'FOUND':'WAIT';" +
                "})()";

        webView.evaluateJavascript(js, result -> {
            if (result != null && result.contains("FOUND")) {
                webStatus.setText("WebView: kolom URL ditemukan...");
                handler.postDelayed(() -> injectTikTokUrl(tiktokUrl), 300);
            } else {
                handler.postDelayed(() -> waitForSaveTikTokInput(tiktokUrl, attempt + 1), 500);
            }
        });
    }

    private void injectTikTokUrl(String tiktokUrl) {
        if (!processing || currentIndex < 0 || submitClicked) return;

        String safeUrl = JSONObject.quote(tiktokUrl);

        String js = "(function(){" +
                "var fields=Array.from(document.querySelectorAll('input,textarea'));" +
                "var field=fields.find(function(el){" +
                "var p=((el.placeholder||'')+' '+(el.getAttribute('aria-label')||'')+' '+(el.name||'')+' '+(el.type||'')).toLowerCase();" +
                "return p.includes('tautan')||p.includes('tiktok')||p.includes('link')||p.includes('url')||el.type==='url';" +
                "});" +
                "if(!field)return 'NO_FIELD';" +
                "var proto=field instanceof HTMLTextAreaElement?HTMLTextAreaElement.prototype:HTMLInputElement.prototype;" +
                "var desc=Object.getOwnPropertyDescriptor(proto,'value');" +
                "if(desc&&desc.set){desc.set.call(field," + safeUrl + ");}else{field.value=" + safeUrl + ";}" +
                "field.dispatchEvent(new Event('input',{bubbles:true}));" +
                "field.dispatchEvent(new Event('change',{bubbles:true}));" +
                "field.focus();" +
                "if(field.value!==" + safeUrl + ")return 'VALUE_FAILED';" +
                "var buttons=Array.from(document.querySelectorAll('button,input[type=submit],input[type=button],a'));" +
                "var downloadButton=buttons.find(function(el){" +
                "var t=(el.innerText||el.textContent||el.value||el.getAttribute('aria-label')||'').replace(/\\s+/g,' ').trim().toLowerCase();" +
                "return t==='unduh'||t==='download';" +
                "});" +
                "if(!downloadButton)return 'NO_DOWNLOAD_BUTTON';" +
                "downloadButton.click();" +
                "return 'OK';" +
                "})()";

        webView.evaluateJavascript(js, result -> {
            if (result != null && result.contains("OK")) {
                submitClicked = true;
                webStatus.setText("WebView: URL berhasil dikirim, menunggu hasil...");
                setStatus(currentIndex, "Menunggu hasil SaveTikTok");
                handler.postDelayed(() -> findMp4HdButton(0), 2000);
            } else {
                submitClicked = false;
                webStatus.setText("WebView: URL belum berhasil dikirim, mencoba lagi...");
                handler.postDelayed(() -> waitForSaveTikTokInput(tiktokUrl, 0), 1000);
            }
        });
    }

    private void findMp4HdButton(int attempt) {
        if (!processing || currentIndex < 0 || downloadStarted) return;

        if (attempt >= MAX_MP4_HD_CHECKS) {
            webStatus.setText("WebView: tombol MP4 HD tidak ditemukan");
            setStatus(currentIndex, "MP4 HD tidak ditemukan");
            return;
        }

        /*
         * DEBUG DOM MODE
         *
         * Jangan klik MP4 HD dulu.
         * Kita mengambil struktur DOM nyata di sekitar tombol MP4 HD agar
         * selector caption final bisa dibuat berdasarkan HTML sebenarnya,
         * bukan berdasarkan tebakan.
         */
        String js =
                "(function(){" +
                "var buttons=Array.from(document.querySelectorAll('button,a,input[type=button],input[type=submit],[role=button]'));" +
                "var target=buttons.find(function(el){" +
                "var t=(el.innerText||el.textContent||el.value||el.getAttribute('aria-label')||'').replace(/\\s+/g,' ').trim().toLowerCase();" +
                "return t==='unduh mp4 hd'||t==='download mp4 hd'||t.includes('unduh mp4 hd')||t.includes('download mp4 hd');" +
                "});" +
                "if(!target)return JSON.stringify({state:'WAIT'});" +
                "var tr=target.getBoundingClientRect();" +
                "function clean(s,n){s=(s||'').replace(/\\s+/g,' ').trim();return s.length>n?s.substring(0,n)+'...':s;}" +
                "var ancestors=[];" +
                "var p=target;" +
                "for(var i=0;i<7&&p;i++,p=p.parentElement){" +
                "ancestors.push({level:i,tag:p.tagName,id:p.id||'',className:(typeof p.className==='string'?p.className:'')," +
                "text:clean(p.innerText||p.textContent||'',1800)," +
                "images:p.querySelectorAll('img').length," +
                "buttons:p.querySelectorAll('button,a,input[type=button],input[type=submit],[role=button]').length," +
                "html:clean(p.outerHTML||'',12000)});" +
                "}" +
                "var nearby=[];" +
                "var all=Array.from(document.querySelectorAll('h1,h2,h3,h4,p,span,div,section,article'));" +
                "all.forEach(function(el){" +
                "if(el===target||el.contains(target))return;" +
                "var r=el.getBoundingClientRect();" +
                "if(r.width<5||r.height<5)return;" +
                "if(r.bottom<0||r.top>window.innerHeight)return;" +
                "var t=clean(el.innerText||el.textContent||'',500);" +
                "if(!t||t.length<2)return;" +
                "var dist=Math.abs(r.bottom-tr.top);" +
                "if(dist>650)return;" +
                "nearby.push({tag:el.tagName,id:el.id||'',className:(typeof el.className==='string'?el.className:'')," +
                "top:Math.round(r.top),left:Math.round(r.left),width:Math.round(r.width),height:Math.round(r.height)," +
                "text:t,html:clean(el.outerHTML||'',4000),distance:Math.round(dist)});" +
                "});" +
                "nearby.sort(function(a,b){return a.distance-b.distance;});" +
                "nearby=nearby.slice(0,30);" +
                "var imgs=Array.from(document.querySelectorAll('img')).map(function(img){" +
                "var r=img.getBoundingClientRect();" +
                "return {src:clean(img.currentSrc||img.src||'',500),alt:clean(img.alt||'',300),className:(typeof img.className==='string'?img.className:'')," +
                "top:Math.round(r.top),left:Math.round(r.left),width:Math.round(r.width),height:Math.round(r.height)};" +
                "}).filter(function(x){return x.width>30&&x.height>30;}).slice(0,20);" +
                "return JSON.stringify({state:'FOUND',target:{tag:target.tagName,id:target.id||'',className:(typeof target.className==='string'?target.className:''),text:clean(target.innerText||target.textContent||target.value||'',500),html:clean(target.outerHTML||'',6000),top:Math.round(tr.top),left:Math.round(tr.left),width:Math.round(tr.width),height:Math.round(tr.height)},ancestors:ancestors,nearby:nearby,images:imgs});" +
                "})()";

        webView.evaluateJavascript(js, result -> {
            if (result != null && result.contains("\\\"state\\\":\\\"FOUND\\\"")) {
                saveDebugDomFile(result);
                webStatus.setText("WebView: DOM ditemukan → debug_dom.txt tersimpan");
                setStatus(currentIndex, "Debug DOM tersimpan - belum download");
                Toast.makeText(this, "Debug DOM tersimpan. Belum ada video yang didownload.", Toast.LENGTH_LONG).show();
                processing = false;
                submitClicked = false;
            } else {
                webStatus.setText("WebView: menunggu hasil video...");
                handler.postDelayed(() -> findMp4HdButton(attempt + 1), 500);
            }
        });
    }

    private void saveDebugDomFile(String rawJavascriptResult) {
        try {
            Object parsed = new org.json.JSONTokener(rawJavascriptResult).nextValue();
            String jsonText = parsed instanceof String ? (String) parsed : String.valueOf(parsed);
            JSONObject root = new JSONObject(jsonText);

            StringBuilder out = new StringBuilder();
            out.append("TikTokDownloadManager - DEBUG DOM\\n");
            out.append("Waktu: ").append(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new java.util.Date())).append("\\n");
            out.append("CATATAN: MP4 HD sengaja BELUM diklik. File ini hanya untuk mencari selector caption.\\n\\n");

            JSONObject target = root.optJSONObject("target");
            if (target != null) {
                out.append("========== TARGET MP4 HD ==========\\n");
                appendJsonField(out, "tag", target, "tag");
                appendJsonField(out, "id", target, "id");
                appendJsonField(out, "class", target, "className");
                appendJsonField(out, "text", target, "text");
                appendJsonField(out, "top", target, "top");
                appendJsonField(out, "left", target, "left");
                appendJsonField(out, "width", target, "width");
                appendJsonField(out, "height", target, "height");
                appendJsonField(out, "outerHTML", target, "html");
                out.append("\\n");
            }

            org.json.JSONArray ancestors = root.optJSONArray("ancestors");
            if (ancestors != null) {
                out.append("========== ANCESTORS MP4 HD ==========\\n");
                for (int i = 0; i < ancestors.length(); i++) {
                    JSONObject a = ancestors.optJSONObject(i);
                    if (a == null) continue;
                    out.append("--- LEVEL ").append(a.optInt("level", i)).append(" ---\\n");
                    out.append("tag: ").append(a.optString("tag", "")).append("\\n");
                    out.append("id: ").append(a.optString("id", "")).append("\\n");
                    out.append("class: ").append(a.optString("className", "")).append("\\n");
                    out.append("images: ").append(a.optInt("images", 0)).append("\\n");
                    out.append("buttons: ").append(a.optInt("buttons", 0)).append("\\n");
                    out.append("text: ").append(a.optString("text", "")).append("\\n");
                    out.append("outerHTML: ").append(a.optString("html", "")).append("\\n\\n");
                }
            }

            org.json.JSONArray nearby = root.optJSONArray("nearby");
            if (nearby != null) {
                out.append("========== NEARBY TEXT ELEMENTS ==========\\n");
                for (int i = 0; i < nearby.length(); i++) {
                    JSONObject n = nearby.optJSONObject(i);
                    if (n == null) continue;
                    out.append("--- ITEM ").append(i + 1).append(" / distance ").append(n.optInt("distance", -1)).append(" ---\\n");
                    out.append("tag: ").append(n.optString("tag", "")).append("\\n");
                    out.append("id: ").append(n.optString("id", "")).append("\\n");
                    out.append("class: ").append(n.optString("className", "")).append("\\n");
                    out.append("position: ").append(n.optInt("top", 0)).append(", ").append(n.optInt("left", 0)).append("\\n");
                    out.append("size: ").append(n.optInt("width", 0)).append("x").append(n.optInt("height", 0)).append("\\n");
                    out.append("text: ").append(n.optString("text", "")).append("\\n");
                    out.append("outerHTML: ").append(n.optString("html", "")).append("\\n\\n");
                }
            }

            org.json.JSONArray images = root.optJSONArray("images");
            if (images != null) {
                out.append("========== VISIBLE IMAGES ==========\\n");
                for (int i = 0; i < images.length(); i++) {
                    JSONObject im = images.optJSONObject(i);
                    if (im == null) continue;
                    out.append("--- IMAGE ").append(i + 1).append(" ---\\n");
                    out.append("src: ").append(im.optString("src", "")).append("\\n");
                    out.append("alt: ").append(im.optString("alt", "")).append("\\n");
                    out.append("class: ").append(im.optString("className", "")).append("\\n");
                    out.append("position: ").append(im.optInt("top", 0)).append(", ").append(im.optInt("left", 0)).append("\\n");
                    out.append("size: ").append(im.optInt("width", 0)).append("x").append(im.optInt("height", 0)).append("\\n\\n");
                }
            }

            writeTextToDownloads("debug_dom.txt", out.toString());
        } catch (Exception e) {
            writeTextToDownloads("debug_dom_error.txt", "Gagal membaca hasil DOM debug: " + e.getMessage() + "\\n\\nRAW:\\n" + rawJavascriptResult);
        }
    }

    private void appendJsonField(StringBuilder out, String label, JSONObject obj, String key) {
        out.append(label).append(": ").append(obj.optString(key, "")).append("\\n");
    }

    private void writeTextToDownloads(String filename, String content) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                android.content.ContentValues values = new android.content.ContentValues();
                values.put(android.provider.MediaStore.Downloads.DISPLAY_NAME, filename);
                values.put(android.provider.MediaStore.Downloads.MIME_TYPE, "text/plain");
                values.put(android.provider.MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/TikTokDownloadManager");
                values.put(android.provider.MediaStore.Downloads.IS_PENDING, 1);

                Uri uri = getContentResolver().insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);

                if (uri != null) {
                    try (java.io.OutputStream stream = getContentResolver().openOutputStream(uri)) {
                        if (stream != null) stream.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    }

                    values.clear();
                    values.put(android.provider.MediaStore.Downloads.IS_PENDING, 0);
                    getContentResolver().update(uri, values, null, null);
                }
            } else {
                java.io.File dir = new java.io.File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "TikTokDownloadManager");
                if (!dir.exists()) dir.mkdirs();

                java.io.File file = new java.io.File(dir, filename);

                try (java.io.FileOutputStream stream = new java.io.FileOutputStream(file)) {
                    stream.write(content.getBytes("UTF-8"));
                }
            }
        } catch (Exception e) {
            webStatus.setText("WebView: gagal menyimpan " + filename);
        }
    }

    private void clickMp4HdButton() {
        String js = "(function(){" +
                "var elements=Array.from(document.querySelectorAll('button,a,input[type=button],input[type=submit],[role=button]'));" +
                "var target=elements.find(function(el){" +
                "var t=(el.innerText||el.textContent||el.value||el.getAttribute('aria-label')||'').replace(/\\s+/g,' ').trim().toLowerCase();" +
                "return t.includes('unduh mp4 hd')||t.includes('download mp4 hd');" +
                "});" +
                "if(!target)return 'WAIT'; target.click(); return 'CLICKED';" +
                "})()";

        webView.evaluateJavascript(js, result -> {
            if (result != null && result.contains("CLICKED")) {
                webStatus.setText("WebView: MP4 HD dipilih, menunggu download...");
                setStatus(currentIndex, "MP4 HD dipilih");
            } else {
                webStatus.setText("WebView: tombol MP4 HD hilang, mencoba lagi...");
                handler.postDelayed(() -> findMp4HdButton(0), 500);
            }
        });
    }

    private void handleWebDownload(String url, String userAgent, String contentDisposition, String mimeType, long contentLength) {
        if (!processing || currentIndex < 0 || downloadStarted) return;
        if (url == null || url.trim().isEmpty()) return;

        String normalizedUrl = url.trim();
        if (handledDownloadUrls.contains(normalizedUrl)) return;

        String lowerMime = mimeType == null ? "" : mimeType.toLowerCase(Locale.US);
        String lowerUrl = normalizedUrl.toLowerCase(Locale.US);
        String lowerDisposition = contentDisposition == null ? "" : contentDisposition.toLowerCase(Locale.US);

        boolean looksLikeHtml = lowerMime.contains("text/html") || lowerMime.contains("application/xhtml");
        if (looksLikeHtml) {
            webStatus.setText("WebView: download ditolak karena bukan video");
            return;
        }

        boolean isBin = lowerMime.contains("application/octet-stream") ||
                lowerDisposition.contains(".bin") ||
                lowerUrl.matches(".*\\.bin(?:[?#].*)?$");

        boolean looksLikeVideo = lowerMime.startsWith("video/") ||
                lowerMime.contains("mp4") ||
                lowerDisposition.contains(".mp4") ||
                lowerUrl.contains(".mp4");

        /*
         * SaveTikTok pada sebagian download MP4 HD mengirim data video
         * sebagai application/octet-stream dengan nama/URL .bin.
         * Karena event ini datang setelah tombol MP4 HD kita klik, .bin
         * diperlakukan sebagai kandidat video dan disimpan paksa sebagai
         * video.mp4.
         */
        if (isBin && submitClicked) {
            looksLikeVideo = true;
        }

        if (!looksLikeVideo) {
            webStatus.setText("WebView: format download bukan MP4");
            return;
        }

        handledDownloadUrls.add(normalizedUrl);
        if (handledDownloadUrls.size() > 20) {
            String first = handledDownloadUrls.iterator().next();
            handledDownloadUrls.remove(first);
        }

        downloadStarted = true;
        webStatus.setText("WebView: file MP4 ditemukan, memulai download...");
        enqueueDownload(normalizedUrl, userAgent, mimeType, contentDisposition);
    }

    private void enqueueDownload(String url, String userAgent, String mimeType, String contentDisposition) {
        try {
            DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));

            request.setTitle("TikTok " + (currentIndex + 1));
            request.setDescription("TikTok Download Manager");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setMimeType("video/mp4");

            if (userAgent != null && !userAgent.isEmpty()) request.addRequestHeader("User-Agent", userAgent);

            String cookie = CookieManager.getInstance().getCookie(url);
            if (cookie != null && !cookie.isEmpty()) request.addRequestHeader("Cookie", cookie);

            request.addRequestHeader("Referer", SAVE_URL);

            // Selalu gunakan nama dan folder yang kita tentukan. Jangan gunakan nama .bin dari server.
            String folder = "TikTokDownloadManager/" + String.format(Locale.US, "%03d", currentIndex + 1);
            String filename = "video.mp4";

            request.setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    folder + "/" + filename
            );

            currentDownloadId = manager.enqueue(request);

            int completedIndex = currentIndex;
            setStatus(completedIndex, "Download dimulai");

            webStatus.setText(
                    "WebView: download dimulai → Download/TikTokDownloadManager/" +
                    String.format(Locale.US, "%03d", completedIndex + 1)
            );

            progress.setText("Progress: " + (completedIndex + 1) + " / " + urls.size());

            saveCaptionFile(completedIndex);

            Toast.makeText(
                    this,
                    "Download dimulai: " + folder + "/video.mp4",
                    Toast.LENGTH_SHORT
            ).show();

            waitForDownloadCompletion(manager, currentDownloadId, completedIndex, 0);

        } catch (Exception e) {
            downloadStarted = false;
            processing = false;
            setStatus(currentIndex, "Gagal memulai download");
            webStatus.setText("WebView: gagal download - " + e.getMessage());
        }
    }

    private void saveCaptionFile(int index) {
        // DownloadManager hanya menangani file video. Caption ditulis ke folder yang sama.
        // Pada Android modern, direktori Download publik dapat ditulis melalui MediaStore/SAF;
        // untuk menjaga kompatibilitas project lama, gunakan MediaStore pada Android 10+.
        try {
            String caption = currentCaption == null ? "" : currentCaption.trim();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                android.content.ContentValues values = new android.content.ContentValues();
                values.put(android.provider.MediaStore.Downloads.DISPLAY_NAME, "caption.txt");
                values.put(android.provider.MediaStore.Downloads.MIME_TYPE, "text/plain");
                values.put(
                        android.provider.MediaStore.Downloads.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS +
                                "/TikTokDownloadManager/" +
                                String.format(Locale.US, "%03d", index + 1)
                );
                values.put(android.provider.MediaStore.Downloads.IS_PENDING, 1);

                Uri uri = getContentResolver().insert(
                        android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        values
                );

                if (uri != null) {
                    try (java.io.OutputStream out = getContentResolver().openOutputStream(uri)) {
                        if (out != null) {
                            out.write(
                                    (caption.isEmpty() ? "Caption tidak ditemukan." : caption)
                                            .getBytes(java.nio.charset.StandardCharsets.UTF_8)
                            );
                        }
                    }

                    values.clear();
                    values.put(android.provider.MediaStore.Downloads.IS_PENDING, 0);
                    getContentResolver().update(uri, values, null, null);
                }

            } else {
                java.io.File dir = new java.io.File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        "TikTokDownloadManager/" +
                                String.format(Locale.US, "%03d", index + 1)
                );

                if (!dir.exists()) dir.mkdirs();

                java.io.File file = new java.io.File(dir, "caption.txt");

                try (java.io.FileOutputStream out = new java.io.FileOutputStream(file)) {
                    out.write(
                            (caption.isEmpty() ? "Caption tidak ditemukan." : caption)
                                    .getBytes("UTF-8")
                    );
                }
            }

        } catch (Exception e) {
            webStatus.setText("WebView: video diproses, caption gagal disimpan");
        }
    }

    private void waitForDownloadCompletion(
            DownloadManager manager,
            long downloadId,
            int completedIndex,
            int attempt
    ) {
        if (attempt >= 120) {
            setStatus(completedIndex, "Download berjalan");
            handler.postDelayed(() -> finishCurrentAndNext(completedIndex), 1000);
            return;
        }

        DownloadManager.Query query = new DownloadManager.Query();
        query.setFilterById(downloadId);

        try (android.database.Cursor cursor = manager.query(query)) {

            if (cursor != null && cursor.moveToFirst()) {
                int status = cursor.getInt(
                        cursor.getColumnIndexOrThrow(
                                DownloadManager.COLUMN_STATUS
                        )
                );

                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    setStatus(completedIndex, "Selesai");
                    webStatus.setText("WebView: download selesai");

                    handler.postDelayed(
                            () -> finishCurrentAndNext(completedIndex),
                            700
                    );

                    return;
                }

                if (status == DownloadManager.STATUS_FAILED) {
                    int reason = cursor.getInt(
                            cursor.getColumnIndexOrThrow(
                                    DownloadManager.COLUMN_REASON
                            )
                    );

                    downloadStarted = false;
                    processing = false;

                    setStatus(
                            completedIndex,
                            "Gagal download (" + reason + ")"
                    );

                    webStatus.setText("WebView: download gagal");
                    return;
                }

                int downloaded = cursor.getInt(
                        cursor.getColumnIndexOrThrow(
                                DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR
                        )
                );

                int total = cursor.getInt(
                        cursor.getColumnIndexOrThrow(
                                DownloadManager.COLUMN_TOTAL_SIZE_BYTES
                        )
                );

                if (total > 0) {
                    int percent = (int) Math.max(
                            0,
                            Math.min(
                                    100,
                                    (downloaded * 100L) / total
                            )
                    );

                    setStatus(
                            completedIndex,
                            "Download " + percent + "%"
                    );

                    webStatus.setText(
                            "WebView: download " + percent + "%"
                    );

                } else {
                    setStatus(
                            completedIndex,
                            "Download berjalan"
                    );
                }
            }

        } catch (Exception ignored) {
        }

        handler.postDelayed(
                () -> waitForDownloadCompletion(
                        manager,
                        downloadId,
                        completedIndex,
                        attempt + 1
                ),
                500
        );
    }

    private void finishCurrentAndNext(int completedIndex) {
        processing = false;
        submitClicked = false;
        downloadStarted = false;
        currentDownloadId = -1L;
        currentCaption = "";

        int next = completedIndex + 1;

        if (next < urls.size()) {
            startSingle(next);
        } else {
            currentIndex = -1;
            webStatus.setText("WebView: semua antrean selesai");
            progress.setText(
                    "Progress: " + urls.size() + " / " + urls.size()
            );

            Toast.makeText(
                    this,
                    "Semua antrean sudah diproses.",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void clearQueue() {
        handler.removeCallbacksAndMessages(null);

        urls.clear();
        currentIndex = -1;
        processing = false;
        submitClicked = false;
        downloadStarted = false;
        currentCaption = "";
        currentDownloadId = -1L;
        handledDownloadUrls.clear();

        queue.removeAllViews();
        input.setText("");

        progress.setText("Progress: 0 / 0");
        webStatus.setText("WebView: siap");

        webView.stopLoading();
        webView.setVisibility(View.GONE);
    }

    private void setStatus(int index, String value) {
        if (index < 0 || index >= queue.getChildCount()) return;

        View card = queue.getChildAt(index);
        Object tag = card.getTag();

        if (tag instanceof TextView) {
            ((TextView) tag).setText(
                    "#" + (index + 1) + "  " + value
            );
        }
    }

    private void updateProgress() {
        progress.setText("Progress: 0 / " + urls.size());
    }

    private TextView text(String value, float size) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(Color.rgb(17, 24, 39));
        view.setPadding(0, 9, 0, 9);
        return view;
    }

    private Button button(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        return button;
    }

    private void requestStorageIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT >= 23 &&
                android.os.Build.VERSION.SDK_INT <= 28 &&
                checkSelfPermission(
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED) {

            requestPermissions(
                    new String[]{
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                    },
                    REQ_STORAGE
            );
        }
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

    @Override
    public void onBackPressed() {
        if (webView != null &&
                webView.getVisibility() == View.VISIBLE &&
                webView.canGoBack()) {

            webView.goBack();

        } else {
            super.onBackPressed();
        }
    }
}
