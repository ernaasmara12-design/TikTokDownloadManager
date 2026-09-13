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

    private final LinkedHashSet<String> handledDownloadUrls =
            new LinkedHashSet<>();

    private final Handler handler =
            new Handler(Looper.getMainLooper());

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

        root.addView(
                text("TikTok Download Manager", 28)
        );

        root.addView(
                text("WebView + antrean otomatis", 17)
        );

        input = new EditText(this);
        input.setHint("Tempel link TikTok, satu per baris");
        input.setGravity(Gravity.TOP);
        input.setMinLines(5);

        root.addView(
                input,
                new LinearLayout.LayoutParams(-1, 250)
        );

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        Button load = button("Muat Antrean");
        Button clear = button("Bersihkan");

        row.addView(
                load,
                new LinearLayout.LayoutParams(0, 62, 1)
        );

        LinearLayout.LayoutParams clearParams =
                new LinearLayout.LayoutParams(0, 62, 1);

        clearParams.setMargins(10, 0, 0, 0);

        row.addView(clear, clearParams);

        root.addView(row);

        progress = text("Progress: 0 / 0", 18);
        root.addView(progress);

        webStatus = text("WebView: siap", 14);
        root.addView(webStatus);

        Button startAll = button("Mulai Semua");
        root.addView(startAll);

        root.addView(
                text(
                        "Aplikasi memproses URL melalui halaman SaveTikTok di WebView. " +
                        "CAPTCHA, login, rate limit, dan mekanisme keamanan tidak dilewati. " +
                        "Jika situs meminta tindakan manual, selesaikan secara manual di WebView.",
                        14
                )
        );

        queue = new LinearLayout(this);
        queue.setOrientation(LinearLayout.VERTICAL);

        root.addView(queue);

        webView = new WebView(this);
        webView.setVisibility(View.GONE);

        root.addView(
                webView,
                new LinearLayout.LayoutParams(-1, 650)
        );

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

        settings.setUserAgentString(
                "Mozilla/5.0 (Linux; Android 12) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/140.0 Mobile Safari/537.36"
        );

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance()
                .setAcceptThirdPartyCookies(webView, true);

        webView.setWebChromeClient(
                new WebChromeClient()
        );

        webView.setWebViewClient(new WebViewClient() {

            @Override
            public boolean shouldOverrideUrlLoading(
                    WebView view,
                    WebResourceRequest request
            ) {
                return false;
            }

            @Override
            public void onPageFinished(
                    WebView view,
                    String url
            ) {

                super.onPageFinished(view, url);

                if (!processing || currentIndex < 0) {
                    webStatus.setText(
                            "WebView: halaman siap"
                    );
                    return;
                }

                if (!isSaveTikTokPage(url)) {
                    webStatus.setText(
                            "WebView: membuka SaveTikTok..."
                    );
                    return;
                }

                if (!submitClicked) {

                    webStatus.setText(
                            "WebView: mencari kolom URL..."
                    );

                    handler.postDelayed(
                            () -> waitForSaveTikTokInput(
                                    urls.get(currentIndex),
                                    0
                            ),
                            1000
                    );

                } else {

                    webStatus.setText(
                            "WebView: menunggu hasil..."
                    );
                }
            }

            @Override
            public void onReceivedError(
                    WebView view,
                    WebResourceRequest request,
                    WebResourceError error
            ) {

                if (request.isForMainFrame()) {

                    webStatus.setText(
                            "WebView: gagal memuat halaman"
                    );
                }
            }
        });

        webView.setDownloadListener(
                (url,
                 userAgent,
                 contentDisposition,
                 mimeType,
                 contentLength) ->

                        handleWebDownload(
                                url,
                                userAgent,
                                contentDisposition,
                                mimeType,
                                contentLength
                        )
        );
    }

    private boolean isSaveTikTokPage(String url) {

        try {

            String host =
                    Uri.parse(url).getHost();

            return host != null &&
                    host.toLowerCase(Locale.US)
                            .contains("savetiktok.to");

        } catch (Exception e) {

            return false;
        }
    }

    private void loadQueue() {

        LinkedHashSet<String> unique =
                new LinkedHashSet<>();

        String[] lines =
                input.getText()
                        .toString()
                        .split("\\r?\\n");

        for (String line : lines) {

            String url = line.trim();

            if (url.startsWith("http://") ||
                    url.startsWith("https://")) {

                unique.add(url);
            }
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

        for (int i = 0; i < urls.size(); i++) {

            addQueueItem(
                    i,
                    urls.get(i)
            );
        }

        updateProgress();

        webStatus.setText(
                urls.isEmpty()
                        ? "WebView: tidak ada URL"
                        : "WebView: antrean siap"
        );
    }

    private void addQueueItem(
            int index,
            String url
    ) {

        LinearLayout card =
                new LinearLayout(this);

        card.setOrientation(
                LinearLayout.VERTICAL
        );

        card.setPadding(
                14,
                14,
                14,
                14
        );

        TextView status =
                text(
                        "#" + (index + 1) +
                        "  Menunggu",
                        16
                );

        TextView link =
                text(url, 12);

        link.setMaxLines(3);

        Button process =
                button("Proses Link Ini");

        process.setOnClickListener(
                v -> startSingle(index)
        );

        card.addView(status);
        card.addView(link);
        card.addView(process);

        card.setTag(status);

        queue.addView(card);
    }

    private void startSingle(int index) {

        if (index < 0 ||
                index >= urls.size()) {
            return;
        }

        handler.removeCallbacksAndMessages(null);

        currentIndex = index;

        processing = true;
        submitClicked = false;
        downloadStarted = false;

        currentCaption = "";
        currentDownloadId = -1L;

        setStatus(
                index,
                "Memproses"
        );

        progress.setText(
                "Progress: " +
                (index + 1) +
                " / " +
                urls.size()
        );

        webStatus.setText(
                "WebView: membuka SaveTikTok..."
        );

        webView.setVisibility(
                View.VISIBLE
        );

        webView.loadUrl(SAVE_URL);
    }

    private void startAll() {

        if (urls.isEmpty()) {

            Toast.makeText(
                    this,
                    "Muat antrean dulu.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        if (!processing) {

            startSingle(0);
        }
    }

    private void waitForSaveTikTokInput(
            String tiktokUrl,
            int attempt
    ) {

        if (!processing ||
                currentIndex < 0 ||
                submitClicked) {
            return;
        }

        if (attempt >= MAX_INPUT_CHECKS) {

            webStatus.setText(
                    "WebView: kolom URL tidak ditemukan"
            );

            setStatus(
                    currentIndex,
                    "Kolom URL SaveTikTok tidak ditemukan"
            );

            return;
        }

        String js =
                "(function(){" +

                "var fields=Array.from(" +
                "document.querySelectorAll('input,textarea')" +
                ");" +

                "var field=fields.find(function(el){" +

                "var p=(" +
                "(el.placeholder||'')+' '+" +
                "(el.getAttribute('aria-label')||'')+' '+" +
                "(el.name||'')+' '+" +
                "(el.type||'')" +
                ").toLowerCase();" +

                "return p.includes('tautan')" +
                "||p.includes('tiktok')" +
                "||p.includes('link')" +
                "||p.includes('url')" +
                "||el.type==='url';" +

                "});" +

                "return field?'FOUND':'WAIT';" +

                "})()";

        webView.evaluateJavascript(
                js,
                result -> {

                    if (result != null &&
                            result.contains("FOUND")) {

                        webStatus.setText(
                                "WebView: kolom URL ditemukan..."
                        );

                        handler.postDelayed(
                                () -> injectTikTokUrl(tiktokUrl),
                                300
                        );

                    } else {

                        handler.postDelayed(
                                () -> waitForSaveTikTokInput(
                                        tiktokUrl,
                                        attempt + 1
                                ),
                                500
                        );
                    }
                }
        );
    }

    private void injectTikTokUrl(
            String tiktokUrl
    ) {

        if (!processing ||
                currentIndex < 0 ||
                submitClicked) {
            return;
        }

        String safeUrl =
                JSONObject.quote(tiktokUrl);

        String js =
                "(function(){" +

                "var fields=Array.from(" +
                "document.querySelectorAll('input,textarea')" +
                ");" +

                "var field=fields.find(function(el){" +

                "var p=(" +
                "(el.placeholder||'')+' '+" +
                "(el.getAttribute('aria-label')||'')+' '+" +
                "(el.name||'')+' '+" +
                "(el.type||'')" +
                ").toLowerCase();" +

                "return p.includes('tautan')" +
                "||p.includes('tiktok')" +
                "||p.includes('link')" +
                "||p.includes('url')" +
                "||el.type==='url';" +

                "});" +

                "if(!field)return 'NO_FIELD';" +

                "var proto=" +
                "field instanceof HTMLTextAreaElement" +
                "?HTMLTextAreaElement.prototype" +
                ":HTMLInputElement.prototype;" +

                "var desc=" +
                "Object.getOwnPropertyDescriptor(" +
                "proto,'value');" +

                "if(desc&&desc.set){" +
                "desc.set.call(field," +
                safeUrl +
                ");" +
                "}else{" +
                "field.value=" +
                safeUrl +
                ";" +
                "}" +

                "field.dispatchEvent(" +
                "new Event('input',{bubbles:true})" +
                ");" +

                "field.dispatchEvent(" +
                "new Event('change',{bubbles:true})" +
                ");" +

                "field.focus();" +

                "if(field.value!=" +
                safeUrl +
                ")return 'VALUE_FAILED';" +

                "var buttons=Array.from(" +
                "document.querySelectorAll(" +
                "'button,input[type=submit]," +
                "input[type=button],a'" +
                ")" +
                ");" +

                "var downloadButton=" +
                "buttons.find(function(el){" +

                "var t=(" +
                "el.innerText||" +
                "el.textContent||" +
                "el.value||" +
                "el.getAttribute('aria-label')||''" +
                ").replace(/\\s+/g,' ')" +
                ".trim().toLowerCase();" +

                "return t==='unduh'||t==='download';" +

                "});" +

                "if(!downloadButton)" +
                "return 'NO_DOWNLOAD_BUTTON';" +

                "downloadButton.click();" +

                "return 'OK';" +

                "})()";

        webView.evaluateJavascript(
                js,
                result -> {

                    if (result != null &&
                            result.contains("OK")) {

                        submitClicked = true;

                        webStatus.setText(
                                "WebView: URL berhasil dikirim, menunggu hasil..."
                        );

                        setStatus(
                                currentIndex,
                                "Menunggu hasil SaveTikTok"
                        );

                        handler.postDelayed(
                                () -> findMp4HdButton(0),
                                2000
                        );

                    } else {

                        submitClicked = false;

                        webStatus.setText(
                                "WebView: URL belum berhasil dikirim, mencoba lagi..."
                        );

                        handler.postDelayed(
                                () -> waitForSaveTikTokInput(
                                        tiktokUrl,
                                        0
                                ),
                                1000
                        );
                    }
                }
        );
    }

    private void findMp4HdButton(
            int attempt
    ) {

        if (!processing ||
                currentIndex < 0 ||
                downloadStarted) {
            return;
        }

        if (attempt >= MAX_MP4_HD_CHECKS) {

            webStatus.setText(
                    "WebView: tombol Unduh MP4 HD tidak ditemukan"
            );

            setStatus(
                    currentIndex,
                    "MP4 HD belum ditemukan / perlu tindakan manual"
            );

            return;
        }

        String js =
                "(function(){" +

                "var elements=Array.from(" +
                "document.querySelectorAll(" +
                "'button,a,input[type=button]," +
                "input[type=submit],[role=button]'" +
                ")" +
                ");" +

                "var target=elements.find(function(el){" +

                "var t=(" +
                "el.innerText||" +
                "el.textContent||" +
                "el.value||" +
                "el.getAttribute('aria-label')||''" +
                ").replace(/\\s+/g,' ')" +
                ".trim().toLowerCase();" +

                "return t.includes('unduh mp4 hd')" +
                "||t.includes('download mp4 hd');" +

                "});" +

                "if(!target)" +
                "return JSON.stringify({" +
                "state:'WAIT',caption:''});" +

                "var caption='';" +

                "var bad=/^(unduh|download|" +
                "unduh mp4|download mp4|" +
                "unduh mp4 hd|download mp4 hd|" +
                "unduh mp3|download mp3|" +
                "unduh lebih banyak video|" +
                "share|bagikan|copy|salin)$/i;" +

                "var candidates=Array.from(" +
                "document.querySelectorAll(" +
                "'h1,h2,h3,h4,p,span,div'" +
                ")" +
                ");" +

                "for(var i=0;i<candidates.length;i++){" +

                "var el=candidates[i];" +

                "var t=(" +
                "el.innerText||" +
                "el.textContent||''" +
                ").replace(/\\s+/g,' ')" +
                ".trim();" +

                "if(!t||t.length<3||t.length>500" +
                "||bad.test(t)" +
                "||/https?:\\/\\//i.test(t))" +
                "continue;" +

                "if(/(^|\\s)#?[a-zA-Z0-9_]*" +
                "[a-zA-Z0-9][a-zA-Z0-9_-]*(#|\\s|$)/" +
                ".test(t)" +

                "&& !/savetiktok|" +
                "tiktok download manager|" +
                "internet|video|foto/i.test(t)){" +

                "caption=t;" +

                "}" +

                "}" +

                "if(!caption){" +

                "var prev=target.parentElement;" +

                "for(var k=0;k<4&&prev;k++," +
                "prev=prev.parentElement){" +

                "var tx=(" +
                "prev.innerText||''" +
                ").replace(/\\s+/g,' ')" +
                ".trim();" +

                "var lines=tx.split(/\\n/)" +
                ".map(function(x){return x.trim();})" +
                ".filter(Boolean);" +

                "for(var j=0;j<lines.length;j++){" +

                "if(lines[j].length>=3" +
                "&&lines[j].length<=500" +
                "&&!bad.test(lines[j])" +
                "&&!/^(unduh|download|share|bagikan)/i.test(lines[j])){" +

                "caption=lines[j];" +

                "break;" +

                "}" +

                "}" +

                "if(caption)break;" +

                "}" +

                "}" +

                "return JSON.stringify({" +
                "state:'FOUND',caption:caption});" +

                "})()";

        webView.evaluateJavascript(
                js,
                result -> {

                    if (result != null &&
                            result.contains(
                                    "\\\"state\\\":\\\"FOUND\\\""
                            )) {

                        try {

                            Object parsed =
                                    new org.json.JSONTokener(
                                            result
                                    ).nextValue();

                            JSONObject obj =
                                    parsed instanceof JSONObject
                                            ? (JSONObject) parsed
                                            : new JSONObject(
                                                    String.valueOf(parsed)
                                            );

                            currentCaption =
                                    obj.optString(
                                            "caption",
                                            ""
                                    ).trim();

                        } catch (Exception ignored) {

                            currentCaption = "";
                        }

                        clickMp4HdButton();

                    } else {

                        webStatus.setText(
                                "WebView: menunggu tombol MP4 HD..."
                        );

                        handler.postDelayed(
                                () -> findMp4HdButton(
                                        attempt + 1
                                ),
                                500
                        );
                    }
                }
        );
    }

    private void clickMp4HdButton() {

        String js =
                "(function(){" +

                "var elements=Array.from(" +
                "document.querySelectorAll(" +
                "'button,a,input[type=button]," +
                "input[type=submit],[role=button]'" +
                ")" +
                ");" +

                "var target=elements.find(function(el){" +

                "var t=(" +
                "el.innerText||" +
                "el.textContent||" +
                "el.value||" +
                "el.getAttribute('aria-label')||''" +
                ").replace(/\\s+/g,' ')" +
                ".trim().toLowerCase();" +

                "return t.includes('unduh mp4 hd')" +
                "||t.includes('download mp4 hd');" +

                "});" +

                "if(!target)return 'WAIT';" +

                "target.click();" +

                "return 'CLICKED';" +

                "})()";

        webView.evaluateJavascript(
                js,
                result -> {

                    if (result != null &&
                            result.contains("CLICKED")) {

                        webStatus.setText(
                                "WebView: MP4 HD dipilih, menunggu download..."
                        );

                        setStatus(
                                currentIndex,
                                "MP4 HD dipilih"
                        );

                    } else {

                        webStatus.setText(
                                "WebView: tombol MP4 HD hilang, mencoba lagi..."
                        );

                        handler.postDelayed(
                                () -> findMp4HdButton(0),
                                500
                        );
                    }
                }
        );
    }

    private void handleWebDownload(
            String url,
            String userAgent,
            String contentDisposition,
            String mimeType,
            long contentLength
    ) {

        if (!processing ||
                currentIndex < 0 ||
                downloadStarted) {
            return;
        }

        if (url == null ||
                url.trim().isEmpty()) {
            return;
        }

        String normalizedUrl =
                url.trim();

        if (handledDownloadUrls.contains(
                normalizedUrl)) {
            return;
        }

        String lowerMime =
                mimeType == null
                        ? ""
                        : mimeType.toLowerCase(
                                Locale.US
                        );

        String lowerUrl =
                normalizedUrl.toLowerCase(
                        Locale.US
                );

        String lowerDisposition =
                contentDisposition == null
                        ? ""
                        : contentDisposition.toLowerCase(
                                Locale.US
                        );

        boolean looksLikeHtml =
                lowerMime.contains("text/html") ||
                lowerMime.contains("application/xhtml");

        if (looksLikeHtml) {

            webStatus.setText(
                    "WebView: download ditolak karena bukan video"
            );

            return;
        }

        boolean isBin =
                lowerMime.contains(
                        "application/octet-stream"
                ) ||

                lowerDisposition.contains(".bin") ||

                lowerUrl.matches(
                        ".*\\.bin(?:[?#].*)?$"
                );

        boolean looksLikeVideo =
                lowerMime.startsWith("video/") ||

                lowerMime.contains("mp4") ||

                lowerDisposition.contains(".mp4") ||

                lowerUrl.contains(".mp4") ||

                (contentLength > 0 && !isBin);

        /*
         * Response BIN tidak pernah dimasukkan
         * sebagai download aplikasi.
         */
        if (isBin &&
                !lowerMime.startsWith("video/")) {

            webStatus.setText(
                    "WebView: file BIN diabaikan"
            );

            return;
        }

        if (!looksLikeVideo) {

            webStatus.setText(
                    "WebView: format download bukan MP4"
            );

            return;
        }

        handledDownloadUrls.add(
                normalizedUrl
        );

        if (handledDownloadUrls.size() > 20) {

            String first =
                    handledDownloadUrls
                            .iterator()
                            .next();

            handledDownloadUrls.remove(first);
        }

        downloadStarted = true;

        webStatus.setText(
                "WebView: file MP4 ditemukan, memulai download..."
        );

        enqueueDownload(
                normalizedUrl,
                userAgent,
                mimeType,
                contentDisposition
        );
    }

    private void enqueueDownload(
            String url,
            String userAgent,
            String mimeType,
            String contentDisposition
    ) {

        try {

            DownloadManager manager =
                    (DownloadManager)
                            getSystemService(
                                    Context.DOWNLOAD_SERVICE
                            );

            DownloadManager.Request request =
                    new DownloadManager.Request(
                            Uri.parse(url)
                    );

            request.setTitle(
                    "TikTok " +
                    (currentIndex + 1)
            );

            request.setDescription(
                    "TikTok Download Manager"
            );

            request.setNotificationVisibility(
                    DownloadManager.Request
                            .VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            );

            request.setMimeType(
                    "video/mp4"
            );

            if (userAgent != null &&
                    !userAgent.isEmpty()) {

                request.addRequestHeader(
                        "User-Agent",
                        userAgent
                );
            }

            String cookie =
                    CookieManager.getInstance()
                            .getCookie(url);

            if (cookie != null &&
                    !cookie.isEmpty()) {

                request.addRequestHeader(
                        "Cookie",
                        cookie
                );
            }

            request.addRequestHeader(
                    "Referer",
                    SAVE_URL
            );

            /*
             * Folder khusus:
             *
             * Download/
             * └── TikTokDownloadManager/
             *     └── 001/
             *         └── video.mp4
             */
            String folder =
                    "TikTokDownloadManager/" +
                    String.format(
                            Locale.US,
                            "%03d",
                            currentIndex + 1
                    );

            String filename =
                    "video.mp4";

            /*
             * Jangan gunakan nama file dari server.
             * Selalu gunakan video.mp4.
             */
            request.setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    folder + "/" + filename
            );

            currentDownloadId =
                    manager.enqueue(request);

            int completedIndex =
                    currentIndex;

            setStatus(
                    completedIndex,
                    "Download dimulai"
            );

            webStatus.setText(
                    "WebView: download dimulai → " +
                    "Download/TikTokDownloadManager/" +
                    String.format(
                            Locale.US,
                            "%03d",
                            completedIndex + 1
                    )
            );

            progress.setText(
                    "Progress: " +
                    (completedIndex + 1) +
                    " / " +
                    urls.size()
            );

            /*
             * Simpan caption di folder yang sama.
             */
            saveCaptionFile(
                    completedIndex
            );

            Toast.makeText(
                    this,
                    "Download dimulai: " +
                    folder +
                    "/video.mp4",
                    Toast.LENGTH_SHORT
            ).show();

            waitForDownloadCompletion(
                    manager,
                    currentDownloadId,
                    completedIndex,
                    0
            );

        } catch (Exception e) {

            downloadStarted = false;
            processing = false;

            setStatus(
                    currentIndex,
                    "Gagal memulai download"
            );

            webStatus.setText(
                    "WebView: gagal download - " +
                    e.getMessage()
            );
        }
    }

    private void saveCaptionFile(
            int index
    ) {

        try {

            String caption =
                    currentCaption == null
                            ? ""
                            : currentCaption.trim();

            String folder =
                    Environment.DIRECTORY_DOWNLOADS +
                    "/TikTokDownloadManager/" +
                    String.format(
                            Locale.US,
                            "%03d",
                            index + 1
                    );

            String textContent =
                    caption.isEmpty()
                            ? "Caption tidak ditemukan."
                            : caption;

            if (Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.Q) {

                android.content.ContentValues values =
                        new android.content.ContentValues();

                values.put(
                        android.provider.MediaStore.Downloads
                                .DISPLAY_NAME,
                        "caption.txt"
                );

                values.put(
                        android.provider.MediaStore.Downloads
                                .MIME_TYPE,
                        "text/plain"
                );

                values.put(
                        android.provider.MediaStore.Downloads
                                .RELATIVE_PATH,
                        folder
                );

                values.put(
                        android.provider.MediaStore.Downloads
                                .IS_PENDING,
                        1
                );

                Uri uri =
                        getContentResolver().insert(
                                android.provider.MediaStore.Downloads
                                        .EXTERNAL_CONTENT_URI,
                                values
                        );

                if (uri != null) {

                    try (
                            java.io.OutputStream out =
                                    getContentResolver()
                                            .openOutputStream(uri)
                    ) {

                        if (out != null) {

                            out.write(
                                    textContent.getBytes(
                                            java.nio.charset.StandardCharsets
                                                    .UTF_8
                                    )
                            );
                        }
                    }

                    values.clear();

                    values.put(
                            android.provider.MediaStore.Downloads
                                    .IS_PENDING,
                            0
                    );

                    getContentResolver().update(
                            uri,
                            values,
                            null,
                            null
                    );
                }

            } else {

                java.io.File dir =
                        new java.io.File(
                                Environment
                                        .getExternalStoragePublicDirectory(
                                                Environment
                                                        .DIRECTORY_DOWNLOADS
                                        ),
                                "TikTokDownloadManager/" +
                                String.format(
                                        Locale.US,
                                        "%03d",
                                        index + 1
                                )
                        );

                if (!dir.exists()) {
                    dir.mkdirs();
                }

                java.io.File file =
                        new java.io.File(
                                dir,
                                "caption.txt"
                        );

                try (
                        java.io.FileOutputStream out =
                                new java.io.FileOutputStream(
                                        file
                                )
                ) {

                    out.write(
                            textContent.getBytes(
                                    "UTF-8"
                            )
                    );
                }
            }

        } catch (Exception e) {

            webStatus.setText(
                    "WebView: video diproses, caption gagal disimpan"
            );
        }
    }

    private void waitForDownloadCompletion(
            DownloadManager manager,
            long downloadId,
            int completedIndex,
            int attempt
    ) {

        if (attempt >= 120) {

            setStatus(
                    completedIndex,
                    "Download berjalan"
            );

            handler.postDelayed(
                    () -> finishCurrentAndNext(
                            completedIndex
                    ),
                    1000
            );

            return;
        }

        DownloadManager.Query query =
                new DownloadManager.Query();

        query.setFilterById(
                downloadId
        );

        try (
                android.database.Cursor cursor =
                        manager.query(query)
        ) {

            if (cursor != null &&
                    cursor.moveToFirst()) {

                int status =
                        cursor.getInt(
                                cursor.getColumnIndexOrThrow(
                                        DownloadManager
                                                .COLUMN_STATUS
                                )
                        );

                if (status ==
                        DownloadManager.STATUS_SUCCESSFUL) {

                    setStatus(
                            completedIndex,
                            "Selesai"
                    );

                    webStatus.setText(
                            "WebView: download selesai"
                    );

                    handler.postDelayed(
                            () -> finishCurrentAndNext(
                                    completedIndex
                            ),
                            700
                    );

                    return;
                }

                if (status ==
                        DownloadManager.STATUS_FAILED) {

                    int reason =
                            cursor.getInt(
                                    cursor.getColumnIndexOrThrow(
                                            DownloadManager
                                                    .COLUMN_REASON
                                    )
                            );

                    downloadStarted = false;
                    processing = false;

                    setStatus(
                            completedIndex,
                            "Gagal download (" +
                            reason +
                            ")"
                    );

                    webStatus.setText(
                            "WebView: download gagal"
                    );

                    return;
                }

                int downloaded =
                        cursor.getInt(
                                cursor.getColumnIndexOrThrow(
                                        DownloadManager
                                                .COLUMN_BYTES_DOWNLOADED_SO_FAR
                                )
                        );

                int total =
                        cursor.getInt(
                                cursor.getColumnIndexOrThrow(
                                        DownloadManager
                                                .COLUMN_TOTAL_SIZE_BYTES
                                )
                        );

                if (total > 0) {

                    int percent =
                            (int) Math.max(
                                    0,
                                    Math.min(
                                            100,
                                            (
                                                    downloaded *
                                                    100L
                                            ) / total
                                    )
                            );

                    setStatus(
                            completedIndex,
                            "Download " +
                            percent +
                            "%"
                    );

                    webStatus.setText(
                            "WebView: download " +
                            percent +
                            "%"
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

    private void finishCurrentAndNext(
            int completedIndex
    ) {

        processing = false;
        submitClicked = false;
        downloadStarted = false;

        currentDownloadId = -1L;
        currentCaption = "";

        int next =
                completedIndex + 1;

        if (next < urls.size()) {

            startSingle(next);

        } else {

            currentIndex = -1;

            webStatus.setText(
                    "WebView: semua antrean selesai"
            );

            progress.setText(
                    "Progress: " +
                    urls.size() +
                    " / " +
                    urls.size()
            );

            Toast.makeText(
                    this,
                    "Semua antrean sudah diproses.",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void clearQueue() {

        handler.removeCallbacksAndMessages(
                null
        );

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

        progress.setText(
                "Progress: 0 / 0"
        );

        webStatus.setText(
                "WebView: siap"
        );

        webView.stopLoading();

        webView.setVisibility(
                View.GONE
        );
    }

    private void setStatus(
            int index,
            String value
    ) {

        if (index < 0 ||
                index >= queue.getChildCount()) {
            return;
        }

        View card =
                queue.getChildAt(index);

        Object tag =
                card.getTag();

        if (tag instanceof TextView) {

            ((TextView) tag).setText(
                    "#" +
                    (index + 1) +
                    "  " +
                    value
            );
        }
    }

    private void updateProgress() {

        progress.setText(
                "Progress: 0 / " +
                urls.size()
        );
    }

    private TextView text(
            String value,
            float size
    ) {

        TextView view =
                new TextView(this);

        view.setText(value);

        view.setTextSize(size);

        view.setTextColor(
                Color.rgb(
                        17,
                        24,
                        39
                )
        );

        view.setPadding(
                0,
                9,
                0,
                9
        );

        return view;
    }

    private Button button(
            String value
    ) {

        Button button =
                new Button(this);

        button.setText(value);

        button.setAllCaps(false);

        return button;
    }

    private void requestStorageIfNeeded() {

        if (android.os.Build.VERSION.SDK_INT >= 23 &&
                android.os.Build.VERSION.SDK_INT <= 28 &&
                checkSelfPermission(
                        Manifest.permission
                                .WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED) {

            requestPermissions(
                    new String[]{
                            Manifest.permission
                                    .WRITE_EXTERNAL_STORAGE
                    },
                    REQ_STORAGE
            );
        }
    }

    @Override
    protected void onDestroy() {

        handler.removeCallbacksAndMessages(
                null
        );

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
