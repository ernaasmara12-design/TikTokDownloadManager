package com.example.tiktokdownloadmanager;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final String SAVE_URL =
            "https://savetiktok.to/id";

    private static final int REQ_STORAGE = 501;

    private static final int MAX_INPUT_CHECKS = 20;
    private static final int MAX_RESULT_CHECKS = 40;

    private EditText input;
    private LinearLayout queue;
    private TextView progress;
    private TextView webStatus;
    private WebView webView;

    private final ArrayList<String> urls =
            new ArrayList<>();

    private final LinkedHashSet<String>
            handledDownloadUrls =
            new LinkedHashSet<>();

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private int currentIndex = -1;

    private boolean processing = false;
    private boolean submitClicked = false;
    private boolean downloadStarted = false;

    private String currentCaption = "";

    private long currentDownloadId = -1L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        buildUi();
        requestStorageIfNeeded();
    }

    private void buildUi() {

        ScrollView scroll =
                new ScrollView(this);

        LinearLayout root =
                new LinearLayout(this);

        root.setOrientation(
                LinearLayout.VERTICAL
        );

        root.setPadding(
                22,
                22,
                22,
                22
        );

        root.setBackgroundColor(
                Color.rgb(244, 245, 247)
        );

        root.addView(
                text(
                        "TikTok Download Manager",
                        28
                )
        );

        root.addView(
                text(
                        "WebView + antrean otomatis",
                        17
                )
        );

        input = new EditText(this);

        input.setHint(
                "Tempel link TikTok, satu per baris"
        );

        input.setGravity(
                Gravity.TOP
        );

        input.setMinLines(5);

        root.addView(
                input,
                new LinearLayout.LayoutParams(
                        -1,
                        250
                )
        );

        LinearLayout row =
                new LinearLayout(this);

        row.setOrientation(
                LinearLayout.HORIZONTAL
        );

        Button load =
                button("Muat Antrean");

        Button clear =
                button("Bersihkan");

        row.addView(
                load,
                new LinearLayout.LayoutParams(
                        0,
                        62,
                        1
                )
        );

        LinearLayout.LayoutParams
                clearParams =
                new LinearLayout.LayoutParams(
                        0,
                        62,
                        1
                );

        clearParams.setMargins(
                10,
                0,
                0,
                0
        );

        row.addView(
                clear,
                clearParams
        );

        root.addView(row);

        progress =
                text(
                        "Progress: 0 / 0",
                        18
                );

        root.addView(progress);

        webStatus =
                text(
                        "Status: siap",
                        14
                );

        root.addView(webStatus);

        Button startAll =
                button("Mulai Semua");

        root.addView(startAll);

        root.addView(
                text(
                        "Hasil download disimpan otomatis di " +
                        "Download/TikTokDownloadManager. " +
                        "Setiap video mempunyai folder sendiri.",
                        14
                )
        );

        queue =
                new LinearLayout(this);

        queue.setOrientation(
                LinearLayout.VERTICAL
        );

        root.addView(queue);

        /*
         * WebView tetap ada sebagai mesin otomatis,
         * tetapi TIDAK ditampilkan di dashboard.
         */
        webView =
                new WebView(this);

        webView.setVisibility(
                View.GONE
        );

        configureWebView();

        /*
         * Jangan masukkan WebView ke dashboard.
         * WebView tetap bisa melakukan proses
         * background dan menerima DownloadListener.
         */

        load.setOnClickListener(
                v -> loadQueue()
        );

        clear.setOnClickListener(
                v -> clearQueue()
        );

        startAll.setOnClickListener(
                v -> startAll()
        );

        scroll.addView(root);

        setContentView(scroll);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView() {

        WebSettings settings =
                webView.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);

        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);

        settings.setSupportMultipleWindows(false);

        settings.setJavaScriptCanOpenWindowsAutomatically(
                false
        );

        settings.setUserAgentString(
                "Mozilla/5.0 (Linux; Android 12) " +
                "AppleWebKit/537.36 " +
                "(KHTML, like Gecko) " +
                "Chrome/140.0 Mobile Safari/537.36"
        );

        CookieManager cookies =
                CookieManager.getInstance();

        cookies.setAcceptCookie(true);

        cookies.setAcceptThirdPartyCookies(
                webView,
                true
        );

        webView.setWebChromeClient(
                new WebChromeClient()
        );

        webView.setWebViewClient(
                new WebViewClient() {

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

                        super.onPageFinished(
                                view,
                                url
                        );

                        if (!processing ||
                                currentIndex < 0) {
                            return;
                        }

                        if (!isSaveTikTokPage(url)) {
                            return;
                        }

                        if (!submitClicked) {

                            webStatus.setText(
                                    "Status: menyiapkan URL..."
                            );

                            handler.postDelayed(
                                    () -> waitForInput(
                                            urls.get(currentIndex),
                                            0
                                    ),
                                    800
                            );

                        } else {

                            webStatus.setText(
                                    "Status: menunggu hasil..."
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
                                    "Status: gagal memuat SaveTikTok"
                            );
                        }
                    }
                }
        );

        webView.setDownloadListener(
                (
                        url,
                        userAgent,
                        contentDisposition,
                        mimeType,
                        contentLength
                ) -> handleWebDownload(
                        url,
                        userAgent,
                        contentDisposition,
                        mimeType,
                        contentLength
                )
        );
    }

    private boolean isSaveTikTokPage(
            String url
    ) {

        try {

            String host =
                    Uri.parse(url).getHost();

            return host != null &&
                    host.toLowerCase(
                            Locale.US
                    ).contains(
                            "savetiktok.to"
                    );

        } catch (Exception e) {

            return false;
        }
    }

    private void loadQueue() {

        LinkedHashSet<String> unique =
                new LinkedHashSet<>();

        String raw =
                input.getText()
                        .toString();

        String[] lines =
                raw.split("\\r?\\n");

        for (String line : lines) {

            String url =
                    line.trim();

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

        handler.removeCallbacksAndMessages(
                null
        );

        queue.removeAllViews();

        for (int i = 0;
             i < urls.size();
             i++) {

            addQueueItem(
                    i,
                    urls.get(i)
            );
        }

        updateProgress();

        webStatus.setText(
                urls.isEmpty()
                        ? "Status: tidak ada URL"
                        : "Status: antrean siap"
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
                        "#" +
                        (index + 1) +
                        "  Menunggu",
                        16
                );

        TextView link =
                text(
                        url,
                        12
                );

        link.setMaxLines(3);

        Button process =
                button(
                        "Proses Link Ini"
                );

        process.setOnClickListener(
                v -> startSingle(index)
        );

        card.addView(status);
        card.addView(link);
        card.addView(process);

        card.setTag(status);

        queue.addView(card);
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

    private void startSingle(
            int index
    ) {

        if (index < 0 ||
                index >= urls.size()) {
            return;
        }

        handler.removeCallbacksAndMessages(
                null
        );

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
                "Status: membuka SaveTikTok..."
        );

        /*
         * WebView TIDAK ditampilkan.
         * Hanya digunakan sebagai mesin otomatis.
         */
        webView.loadUrl(
                SAVE_URL
        );
    }

    private void waitForInput(
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
                    "Status: kolom URL tidak ditemukan"
            );

            setStatus(
                    currentIndex,
                    "Kolom URL tidak ditemukan"
            );

            return;
        }

        String js =
                "(function(){" +

                "var f=Array.from(" +
                "document.querySelectorAll(" +
                "'input,textarea'));" +

                "var x=f.find(function(e){" +

                "var p=(" +
                "(e.placeholder||'')+' '+" +
                "(e.getAttribute('aria-label')||'')+' '+" +
                "(e.name||'')+' '+" +
                "(e.type||'')" +
                ").toLowerCase();" +

                "return p.includes('tautan')" +
                "||p.includes('tiktok')" +
                "||p.includes('link')" +
                "||p.includes('url')" +
                "||e.type==='url';" +

                "});" +

                "return x?'FOUND':'WAIT';" +

                "})()";

        webView.evaluateJavascript(
                js,
                result -> {

                    if (result != null &&
                            result.contains("FOUND")) {

                        handler.postDelayed(
                                () -> injectUrl(
                                        tiktokUrl
                                ),
                                300
                        );

                    } else {

                        handler.postDelayed(
                                () -> waitForInput(
                                        tiktokUrl,
                                        attempt + 1
                                ),
                                500
                        );
                    }
                }
        );
    }

    private void injectUrl(
            String tiktokUrl
    ) {

        if (!processing ||
                currentIndex < 0 ||
                submitClicked) {
            return;
        }

        String safe =
                JSONObject.quote(
                        tiktokUrl
                );

        String js =
                "(function(){" +

                "var f=Array.from(" +
                "document.querySelectorAll(" +
                "'input,textarea'));" +

                "var x=f.find(function(e){" +

                "var p=(" +
                "(e.placeholder||'')+' '+" +
                "(e.getAttribute('aria-label')||'')+' '+" +
                "(e.name||'')+' '+" +
                "(e.type||'')" +
                ").toLowerCase();" +

                "return p.includes('tautan')" +
                "||p.includes('tiktok')" +
                "||p.includes('link')" +
                "||p.includes('url')" +
                "||e.type==='url';" +

                "});" +

                "if(!x)return 'NO_FIELD';" +

                "var proto=x instanceof " +
                "HTMLTextAreaElement" +
                "?HTMLTextAreaElement.prototype" +
                ":HTMLInputElement.prototype;" +

                "var d=Object.getOwnPropertyDescriptor(" +
                "proto,'value');" +

                "if(d&&d.set)d.set.call(x," +
                safe +
                ");" +

                "else x.value=" +
                safe +
                ";" +

                "x.dispatchEvent(new Event(" +
                "'input',{bubbles:true}));" +

                "x.dispatchEvent(new Event(" +
                "'change',{bubbles:true}));" +

                "var bs=Array.from(" +
                "document.querySelectorAll(" +
                "'button,input[type=submit]," +
                "input[type=button],a'" +
                "));" +

                "var b=bs.find(function(e){" +

                "var t=(" +
                "e.innerText||" +
                "e.textContent||" +
                "e.value||" +
                "e.getAttribute('aria-label')||''" +
                ").replace(/\\s+/g,' ')" +
                ".trim().toLowerCase();" +

                "return t==='unduh'" +
                "||t==='download';" +

                "});" +

                "if(!b)return 'NO_BUTTON';" +

                "b.click();" +

                "return x.value===" +
                safe +
                "?'OK':'FAILED';" +

                "})()";

        webView.evaluateJavascript(
                js,
                result -> {

                    if (result != null &&
                            result.contains("OK")) {

                        submitClicked = true;

                        webStatus.setText(
                                "Status: URL dikirim..."
                        );

                        setStatus(
                                currentIndex,
                                "Menunggu hasil"
                        );

                        handler.postDelayed(
                                () -> findMp4Hd(
                                        0
                                ),
                                1800
                        );

                    } else {

                        handler.postDelayed(
                                () -> waitForInput(
                                        tiktokUrl,
                                        0
                                ),
                                800
                        );
                    }
                }
        );
    }

    private void findMp4Hd(
            int attempt
    ) {

        if (!processing ||
                currentIndex < 0 ||
                downloadStarted) {
            return;
        }

        if (attempt >= MAX_RESULT_CHECKS) {

            webStatus.setText(
                    "Status: MP4 HD tidak ditemukan"
            );

            setStatus(
                    currentIndex,
                    "MP4 HD tidak ditemukan"
            );

            return;
        }

        String js =
                "(function(){" +

                "var es=Array.from(" +
                "document.querySelectorAll(" +
                "'button,a,input[type=button]," +
                "input[type=submit],[role=button]'" +
                "));" +

                "var target=es.find(function(e){" +

                "var t=(" +
                "e.innerText||" +
                "e.textContent||" +
                "e.value||" +
                "e.getAttribute('aria-label')||''" +
                ").replace(/\\s+/g,' ')" +
                ".trim().toLowerCase();" +

                "return t.includes('unduh mp4 hd')" +
                "||t.includes('download mp4 hd');" +

                "});" +

                "var caption='';" +

                /*
                 * Ambil kandidat teks yang lebih dekat
                 * dengan hasil video.
                 */
                "if(target){" +

                "var parent=target.parentElement;" +

                "for(var level=0;" +
                "level<5&&parent;" +
                "level++," +
                "parent=parent.parentElement){" +

                "var candidates=Array.from(" +
                "parent.querySelectorAll(" +
                "'h1,h2,h3,h4,p,span,div'" +
                "));" +

                "for(var i=0;" +
                "i<candidates.length;i++){" +

                "var s=(" +
                "candidates[i].innerText||" +
                "candidates[i].textContent||''" +
                ").replace(/\\s+/g,' ')" +
                ".trim();" +

                "if(s.length>=3&&" +
                "s.length<=500&&" +

                "!/^(" +
                "download|unduh|" +
                "download mp4|unduh mp4|" +
                "download mp4 hd|unduh mp4 hd|" +
                "download mp3|unduh mp3|" +
                "share|bagikan|copy|salin" +
                ")$/i.test(s)&&" +

                "!/savetiktok|tiktok download manager/i" +
                ".test(s)&&" +

                "(/#/.test(s)||s.length>20)){" +

                "caption=s;" +
                "break;" +

                "}" +
                "}" +

                "if(caption)break;" +
                "}" +

                "}" +

                "return JSON.stringify({" +
                "found:!!target," +
                "caption:caption" +
                "});" +

                "})()";

        webView.evaluateJavascript(
                js,
                result -> {

                    if (result == null) {

                        retryFindMp4Hd(
                                attempt
                        );

                        return;
                    }

                    try {

                        String clean =
                                result;

                        if (clean.startsWith("\"")) {

                            clean =
                                    new org.json.JSONTokener(
                                            clean
                                    ).nextValue()
                                            .toString();
                        }

                        JSONObject obj =
                                new JSONObject(
                                        clean
                                );

                        boolean found =
                                obj.optBoolean(
                                        "found",
                                        false
                                );

                        currentCaption =
                                obj.optString(
                                        "caption",
                                        ""
                                ).trim();

                        if (found) {

                            clickMp4Hd();

                        } else {

                            retryFindMp4Hd(
                                    attempt
                            );
                        }

                    } catch (Exception e) {

                        retryFindMp4Hd(
                                attempt
                        );
                    }
                }
        );
    }

    private void retryFindMp4Hd(
            int attempt
    ) {

        handler.postDelayed(
                () -> findMp4Hd(
                        attempt + 1
                ),
                500
        );
    }

    private void clickMp4Hd() {

        String js =
                "(function(){" +

                "var es=Array.from(" +
                "document.querySelectorAll(" +
                "'button,a,input[type=button]," +
                "input[type=submit],[role=button]'" +
                "));" +

                "var b=es.find(function(e){" +

                "var t=(" +
                "e.innerText||" +
                "e.textContent||" +
                "e.value||" +
                "e.getAttribute('aria-label')||''" +
                ").replace(/\\s+/g,' ')" +
                ".trim().toLowerCase();" +

                "return t.includes('unduh mp4 hd')" +
                "||t.includes('download mp4 hd');" +

                "});" +

                "if(!b)return 'WAIT';" +

                "b.click();" +

                "return 'CLICKED';" +

                "})()";

        webView.evaluateJavascript(
                js,
                result -> {

                    if (result != null &&
                            result.contains("CLICKED")) {

                        webStatus.setText(
                                "Status: MP4 HD dipilih..."
                        );

                        setStatus(
                                currentIndex,
                                "MP4 HD dipilih"
                        );

                    } else {

                        handler.postDelayed(
                                () -> findMp4Hd(0),
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

        String normalized =
                url.trim();

        if (handledDownloadUrls.contains(
                normalized
        )) {
            return;
        }

        String mime =
                mimeType == null
                        ? ""
                        : mimeType.toLowerCase(
                                Locale.US
                        );

        String disposition =
                contentDisposition == null
                        ? ""
                        : contentDisposition.toLowerCase(
                                Locale.US
                        );

        String lowerUrl =
                normalized.toLowerCase(
                        Locale.US
                );

        /*
         * SaveTikTok dapat mengirim MP4 HD
         * sebagai application/octet-stream
         * atau URL yang mengandung .bin.
         *
         * Jangan menolak .bin.
         */
        boolean html =
                mime.contains("text/html") ||
                mime.contains("application/xhtml");

        if (html) {
            return;
        }

        boolean explicitNonVideo =
                mime.contains("audio/") ||
                mime.contains("image/") ||
                mime.contains("text/");

        if (explicitNonVideo) {
            return;
        }

        boolean video =
                mime.startsWith("video/") ||
                mime.contains("mp4") ||
                disposition.contains(".mp4") ||
                lowerUrl.contains(".mp4") ||
                mime.contains(
                        "application/octet-stream"
                ) ||
                disposition.contains(".bin") ||
                lowerUrl.contains(".bin");

        if (!video) {
            return;
        }

        handledDownloadUrls.add(
                normalized
        );

        if (handledDownloadUrls.size() > 30) {

            String first =
                    handledDownloadUrls
                            .iterator()
                            .next();

            handledDownloadUrls.remove(
                    first
            );
        }

        /*
         * Satu URL download hanya boleh
         * diproses satu kali.
         */
        if (downloadStarted) {
            return;
        }

        downloadStarted = true;

        webStatus.setText(
                "Status: video ditemukan, download..."
        );

        enqueueDownload(
                normalized,
                userAgent
        );
    }

    private void enqueueDownload(
            String url,
            String userAgent
    ) {

        try {

            DownloadManager manager =
                    (DownloadManager)
                            getSystemService(
                                    Context.DOWNLOAD_SERVICE
                            );

            if (manager == null) {

                throw new Exception(
                        "DownloadManager tidak tersedia"
                );
            }

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

            request.setMimeType(
                    "video/mp4"
            );

            request.setNotificationVisibility(
                    DownloadManager.Request
                            .VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            );

            if (userAgent != null &&
                    !userAgent.isEmpty()) {

                request.addRequestHeader(
                        "User-Agent",
                        userAgent
                );
            }

            String cookie =
                    CookieManager
                            .getInstance()
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
             * Struktur:
             *
             * Download/
             *   TikTokDownloadManager/
             *      001/
             *         video.mp4
             */
            String number =
                    String.format(
                            Locale.US,
                            "%03d",
                            currentIndex + 1
                    );

            String folder =
                    "TikTokDownloadManager/" +
                    number;

            /*
             * Selalu pakai video.mp4.
             * Nama .bin dari server tidak digunakan.
             */
            request.setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    folder + "/video.mp4"
            );

            currentDownloadId =
                    manager.enqueue(
                            request
                    );

            int index =
                    currentIndex;

            setStatus(
                    index,
                    "Download dimulai"
            );

            webStatus.setText(
                    "Status: download dimulai"
            );

            waitForDownloadCompletion(
                    manager,
                    currentDownloadId,
                    index,
                    0
            );

        } catch (Exception e) {

            downloadStarted = false;
            processing = false;

            setStatus(
                    currentIndex,
                    "Gagal download"
            );

            webStatus.setText(
                    "Status: gagal download - " +
                    e.getMessage()
            );
        }
    }

    private void waitForDownloadCompletion(
            DownloadManager manager,
            long id,
            int index,
            int attempt
    ) {

        if (attempt >= 240) {

            setStatus(
                    index,
                    "Download masih berjalan"
            );

            processing = false;

            webStatus.setText(
                    "Status: download masih berjalan"
            );

            return;
        }

        DownloadManager.Query query =
                new DownloadManager.Query();

        query.setFilterById(id);

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
                            index,
                            "Selesai"
                    );

                    webStatus.setText(
                            "Status: download selesai"
                    );

                    saveCaptionFile(
                            index
                    );

                    handler.postDelayed(
                            () -> finishCurrentAndNext(
                                    index
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

                    setStatus(
                            index,
                            "Gagal (" +
                            reason +
                            ")"
                    );

                    webStatus.setText(
                            "Status: download gagal"
                    );

                    processing = false;
                    downloadStarted = false;

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
                                            downloaded *
                                            100L /
                                            total
                                    )
                            );

                    setStatus(
                            index,
                            "Download " +
                            percent +
                            "%"
                    );

                    webStatus.setText(
                            "Status: download " +
                            percent +
                            "%"
                    );

                } else {

                    setStatus(
                            index,
                            "Download berjalan"
                    );
                }
            }

        } catch (Exception ignored) {
        }

        handler.postDelayed(
                () -> waitForDownloadCompletion(
                        manager,
                        id,
                        index,
                        attempt + 1
                ),
                500
        );
    }

    private void saveCaptionFile(
            int index
    ) {

        String caption =
                currentCaption == null
                        ? ""
                        : currentCaption.trim();

        if (caption.isEmpty()) {

            caption =
                    "Caption tidak ditemukan.";
        }

        try {

            String number =
                    String.format(
                            Locale.US,
                            "%03d",
                            index + 1
                    );

            String relativePath =
                    Environment.DIRECTORY_DOWNLOADS +
                    "/TikTokDownloadManager/" +
                    number;

            /*
             * Android 10+
             */
            if (Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.Q) {

                android.content.ContentValues
                        values =
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
                        relativePath
                );

                values.put(
                        android.provider.MediaStore.Downloads
                                .IS_PENDING,
                        1
                );

                Uri uri =
                        getContentResolver().insert(
                                android.provider.MediaStore
                                        .Downloads
                                        .EXTERNAL_CONTENT_URI,
                                values
                        );

                if (uri != null) {

                    try (
                            OutputStream output =
                                    getContentResolver()
                                            .openOutputStream(
                                                    uri
                                            )
                    ) {

                        if (output != null) {

                            output.write(
                                    caption.getBytes(
                                            StandardCharsets.UTF_8
                                    )
                            );
                        }
                    }

                    android.content.ContentValues
                            done =
                            new android.content.ContentValues();

                    done.put(
                            android.provider.MediaStore.Downloads
                                    .IS_PENDING,
                            0
                    );

                    getContentResolver().update(
                            uri,
                            done,
                            null,
                            null
                    );
                }

            } else {

                File base =
                        Environment
                                .getExternalStoragePublicDirectory(
                                        Environment
                                                .DIRECTORY_DOWNLOADS
                                );

                File folder =
                        new File(
                                base,
                                "TikTokDownloadManager/" +
                                number
                        );

                if (!folder.exists()) {
                    folder.mkdirs();
                }

                File captionFile =
                        new File(
                                folder,
                                "caption.txt"
                        );

                try (
                        FileOutputStream output =
                                new FileOutputStream(
                                        captionFile
                                )
                ) {

                    output.write(
                            caption.getBytes(
                                    StandardCharsets.UTF_8
                            )
                    );
                }
            }

        } catch (Exception e) {

            webStatus.setText(
                    "Status: video selesai, " +
                    "caption gagal disimpan"
            );
        }
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

            progress.setText(
                    "Progress: " +
                    urls.size() +
                    " / " +
                    urls.size()
            );

            webStatus.setText(
                    "Status: semua antrean selesai"
            );

            Toast.makeText(
                    this,
                    "Semua download selesai.",
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
                "Status: siap"
        );

        webView.stopLoading();
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

        if (Build.VERSION.SDK_INT >= 23 &&
                Build.VERSION.SDK_INT <= 28 &&
                checkSelfPermission(
                        Manifest.permission
                                .WRITE_EXTERNAL_STORAGE
                ) !=
                PackageManager.PERMISSION_GRANTED) {

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

        /*
         * Karena WebView sekarang disembunyikan,
         * tombol Back langsung mengikuti Activity.
         */
        super.onBackPressed();
    }
}
