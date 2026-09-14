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
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;

public class MainActivity extends Activity {

    // ============================================================
    // SAVE TIKTOK
    // ============================================================

    private static final String SAVE_URL =
            "https://savetiktok.to/id";


    // ============================================================
    // PERMISSION
    // ============================================================

    private static final int REQ_STORAGE = 501;


    // ============================================================
    // ENGINE LIMIT
    // ============================================================

    private static final int MAX_INPUT_CHECKS = 20;

    private static final int MAX_MP4_HD_CHECKS = 30;

    private static final int MAX_DOWNLOAD_CHECKS = 1200;

    private static final long DOWNLOAD_CHECK_INTERVAL = 500L;


    // ============================================================
    // ENGINE VARIABLES
    // ============================================================

    private EditText input;

    private LinearLayout queue;

    private TextView progress;

    private TextView webStatus;

    private WebView webView;


    private final ArrayList<String> urls =
            new ArrayList<>();


    private int currentIndex = -1;


    private boolean processing = false;

    private boolean submitClicked = false;

    private boolean downloadStarted = false;


    private String currentCaption = "";


    private long currentDownloadId = -1L;


    private final LinkedHashSet<String>
            handledDownloadUrls =
            new LinkedHashSet<>();


    private final Handler handler =
            new Handler(
                    Looper.getMainLooper()
            );


    // ============================================================
    // UI VARIABLES
    // ============================================================

    private FrameLayout pageContainer;

    private View downloadPage;

    private View progressPage;

    private View finishedPage;


    private LinearLayout finishedList;


    private TextView emptyProgress;

    private TextView emptyFinished;


    private TextView navDownload;

    private TextView navProgress;

    private TextView navFinished;


    private int currentPage = 0;


    // ============================================================
    // ON CREATE
    // ============================================================

    @Override
    protected void onCreate(
            Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        buildUi();

        requestStorageIfNeeded();
    }


    // ============================================================
    // BUILD UI
    // ============================================================

    private void buildUi() {

        LinearLayout root =
                new LinearLayout(this);

        root.setOrientation(
                LinearLayout.VERTICAL
        );

        root.setBackgroundColor(
                Color.rgb(
                        247,
                        248,
                        250
                )
        );


        // ========================================================
        // PAGE CONTAINER
        // ========================================================

        pageContainer =
                new FrameLayout(this);


        root.addView(
                pageContainer,
                new LinearLayout.LayoutParams(
                        -1,
                        0,
                        1
                )
        );


        // ========================================================
        // PAGES
        // ========================================================

        downloadPage =
                buildDownloadPage();


        progressPage =
                buildProgressPage();


        finishedPage =
                buildFinishedPage();


        pageContainer.addView(
                downloadPage
        );


        // ========================================================
        // BOTTOM NAV
        // ========================================================

        LinearLayout bottomNav =
                new LinearLayout(this);

        bottomNav.setOrientation(
                LinearLayout.HORIZONTAL
        );

        bottomNav.setGravity(
                Gravity.CENTER
        );

        bottomNav.setBackgroundColor(
                Color.WHITE
        );


        navDownload =
                createNavItem(
                        "↓",
                        "Download"
                );


        navProgress =
                createNavItem(
                        "◷",
                        "Progress"
                );


        navFinished =
                createNavItem(
                        "✓",
                        "Finished"
                );


        bottomNav.addView(
                navDownload,
                new LinearLayout.LayoutParams(
                        0,
                        76,
                        1
                )
        );


        bottomNav.addView(
                navProgress,
                new LinearLayout.LayoutParams(
                        0,
                        76,
                        1
                )
        );


        bottomNav.addView(
                navFinished,
                new LinearLayout.LayoutParams(
                        0,
                        76,
                        1
                )
        );


        navDownload.setOnClickListener(
                v -> showPage(0)
        );


        navProgress.setOnClickListener(
                v -> showPage(1)
        );


        navFinished.setOnClickListener(
                v -> showPage(2)
        );


        root.addView(
                bottomNav,
                new LinearLayout.LayoutParams(
                        -1,
                        76
                )
        );


        // ========================================================
        // HIDDEN WEBVIEW
        // ========================================================

        webView =
                new WebView(this);

        webView.setVisibility(
                View.GONE
        );


        root.addView(
                webView,
                new LinearLayout.LayoutParams(
                        1,
                        1
                )
        );


        configureWebView();


        setContentView(root);


        updateNavigation();
    }


    // ============================================================
    // DOWNLOAD PAGE
    // ============================================================

    private View buildDownloadPage() {

        ScrollView scroll =
                new ScrollView(this);


        LinearLayout content =
                new LinearLayout(this);

        content.setOrientation(
                LinearLayout.VERTICAL
        );


        content.setPadding(
                20,
                28,
                20,
                30
        );


        TextView title =
                text(
                        "Download",
                        28
                );


        title.setTypeface(
                null,
                android.graphics.Typeface.BOLD
        );


        content.addView(
                title
        );


        TextView subtitle =
                text(
                        "Download video TikTok dengan mudah.",
                        15
                );


        subtitle.setTextColor(
                Color.GRAY
        );


        LinearLayout.LayoutParams
                subtitleParams =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );


        subtitleParams.setMargins(
                0,
                3,
                0,
                22
        );


        content.addView(
                subtitle,
                subtitleParams
        );


        // ========================================================
        // URL CARD
        // ========================================================

        LinearLayout card =
                createCard();


        TextView cardTitle =
                text(
                        "TikTok URL",
                        16
                );


        cardTitle.setTypeface(
                null,
                android.graphics.Typeface.BOLD
        );


        card.addView(
                cardTitle
        );


        input =
                new EditText(this);


        input.setHint(
                "Tempel link TikTok, satu per baris"
        );


        input.setGravity(
                Gravity.TOP
        );


        input.setTextSize(
                14
        );


        input.setPadding(
                16,
                15,
                16,
                15
        );


        input.setMinLines(
                5
        );


        input.setMaxLines(
                10
        );


        input.setBackgroundColor(
                Color.rgb(
                        246,
                        247,
                        249
                )
        );


        LinearLayout.LayoutParams
                inputParams =
                new LinearLayout.LayoutParams(
                        -1,
                        220
                );


        inputParams.setMargins(
                0,
                14,
                0,
                14
        );


        card.addView(
                input,
                inputParams
        );


        Button addButton =
                button(
                        "Tambah ke Antrian"
                );


        addButton.setOnClickListener(
                v -> loadQueue()
        );


        card.addView(
                addButton
        );


        content.addView(
                card
        );


        // ========================================================
        // INFO
        // ========================================================

        LinearLayout info =
                createCard();


        TextView infoTitle =
                text(
                        "Cara menggunakan",
                        16
                );


        infoTitle.setTypeface(
                null,
                android.graphics.Typeface.BOLD
        );


        info.addView(
                infoTitle
        );


        addStep(
                info,
                "1",
                "Paste satu atau beberapa link TikTok."
        );


        addStep(
                info,
                "2",
                "Tekan Tambah ke Antrian."
        );


        addStep(
                info,
                "3",
                "Buka Progress lalu tekan Mulai Semua."
        );


        addStep(
                info,
                "4",
                "Video dan caption disimpan otomatis."
        );


        addStep(
                info,
                "5",
                "Video selesai dapat diputar dari Finished."
        );


        content.addView(
                info
        );


        // ========================================================
        // STORAGE
        // ========================================================

        LinearLayout storage =
                createCard();


        TextView storageTitle =
                text(
                        "Penyimpanan",
                        16
                );


        storageTitle.setTypeface(
                null,
                android.graphics.Typeface.BOLD
        );


        storage.addView(
                storageTitle
        );


        TextView storageText =
                text(
                        "Download/TikTokDownloadManager/001/\n"
                                + "├── video.mp4\n"
                                + "└── caption.txt",
                        14
                );


        storageText.setTextColor(
                Color.GRAY
        );


        storage.addView(
                storageText
        );


        content.addView(
                storage
        );


        scroll.addView(
                content
        );


        return scroll;
    }


    // ============================================================
    // PROGRESS PAGE
    // ============================================================

    private View buildProgressPage() {

        ScrollView scroll =
                new ScrollView(this);


        LinearLayout content =
                new LinearLayout(this);

        content.setOrientation(
                LinearLayout.VERTICAL
        );


        content.setPadding(
                20,
                28,
                20,
                30
        );


        TextView title =
                text(
                        "Progress",
                        28
                );


        title.setTypeface(
                null,
                android.graphics.Typeface.BOLD
        );


        content.addView(
                title
        );


        TextView subtitle =
                text(
                        "Daftar video yang sedang diproses.",
                        15
                );


        subtitle.setTextColor(
                Color.GRAY
        );


        LinearLayout.LayoutParams
                subtitleParams =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );


        subtitleParams.setMargins(
                0,
                3,
                0,
                20
        );


        content.addView(
                subtitle,
                subtitleParams
        );


        Button startAll =
                button(
                        "Mulai Semua"
                );


        startAll.setOnClickListener(
                v -> startAll()
        );


        content.addView(
                startAll
        );


        progress =
                text(
                        "Progress: 0 / 0",
                        14
                );


        progress.setTextColor(
                Color.GRAY
        );


        progress.setPadding(
                0,
                14,
                0,
                8
        );


        content.addView(
                progress
        );


        webStatus =
                text(
                        "WebView: siap",
                        13
                );


        webStatus.setTextColor(
                Color.GRAY
        );


        content.addView(
                webStatus
        );


        queue =
                new LinearLayout(this);


        queue.setOrientation(
                LinearLayout.VERTICAL
        );


        LinearLayout.LayoutParams
                queueParams =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );


        queueParams.setMargins(
                0,
                14,
                0,
                0
        );


        content.addView(
                queue,
                queueParams
        );


        emptyProgress =
                text(
                        "Belum ada video dalam antrean.",
                        14
                );


        emptyProgress.setGravity(
                Gravity.CENTER
        );


        emptyProgress.setTextColor(
                Color.GRAY
        );


        emptyProgress.setPadding(
                0,
                50,
                0,
                50
        );


        content.addView(
                emptyProgress
        );


        scroll.addView(
                content
        );


        return scroll;
    }


    // ============================================================
    // FINISHED PAGE
    // ============================================================

    private View buildFinishedPage() {

        ScrollView scroll =
                new ScrollView(this);


        LinearLayout content =
                new LinearLayout(this);

        content.setOrientation(
                LinearLayout.VERTICAL
        );


        content.setPadding(
                20,
                28,
                20,
                30
        );


        TextView title =
                text(
                        "Finished",
                        28
                );


        title.setTypeface(
                null,
                android.graphics.Typeface.BOLD
        );


        content.addView(
                title
        );


        TextView subtitle =
                text(
                        "Video yang sudah selesai diunduh.",
                        15
                );


        subtitle.setTextColor(
                Color.GRAY
        );


        LinearLayout.LayoutParams
                subtitleParams =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );


        subtitleParams.setMargins(
                0,
                3,
                0,
                20
        );


        content.addView(
                subtitle,
                subtitleParams
        );


        finishedList =
                new LinearLayout(this);


        finishedList.setOrientation(
                LinearLayout.VERTICAL
        );


        content.addView(
                finishedList
        );


        emptyFinished =
                text(
                        "Belum ada download selesai.",
                        14
                );


        emptyFinished.setGravity(
                Gravity.CENTER
        );


        emptyFinished.setTextColor(
                Color.GRAY
        );


        emptyFinished.setPadding(
                0,
                50,
                0,
                50
        );


        content.addView(
                emptyFinished
        );


        scroll.addView(
                content
        );


        return scroll;
    }


    // ============================================================
    // NAV ITEM
    // ============================================================

    private TextView createNavItem(
            String icon,
            String name) {

        TextView item =
                new TextView(this);


        item.setText(
                icon +
                        "\n" +
                        name
        );


        item.setTextSize(
                12
        );


        item.setGravity(
                Gravity.CENTER
        );


        item.setTextColor(
                Color.GRAY
        );


        return item;
    }


    // ============================================================
    // SHOW PAGE
    // ============================================================

    private void showPage(
            int page) {

        currentPage = page;


        pageContainer.removeAllViews();


        if (page == 0) {

            pageContainer.addView(
                    downloadPage
            );

        } else if (page == 1) {

            pageContainer.addView(
                    progressPage
            );

        } else {

            pageContainer.addView(
                    finishedPage
            );
        }


        updateNavigation();
    }


    // ============================================================
    // NAV COLOR
    // ============================================================

    private void updateNavigation() {

        if (navDownload == null) {
            return;
        }


        navDownload.setTextColor(
                currentPage == 0
                        ? Color.BLACK
                        : Color.GRAY
        );


        navProgress.setTextColor(
                currentPage == 1
                        ? Color.BLACK
                        : Color.GRAY
        );


        navFinished.setTextColor(
                currentPage == 2
                        ? Color.BLACK
                        : Color.GRAY
        );
    }


    // ============================================================
    // CARD
    // ============================================================

    private LinearLayout createCard() {

        LinearLayout card =
                new LinearLayout(this);


        card.setOrientation(
                LinearLayout.VERTICAL
        );


        card.setPadding(
                18,
                18,
                18,
                18
        );


        card.setBackgroundColor(
                Color.WHITE
        );


        LinearLayout.LayoutParams
                params =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );


        params.setMargins(
                0,
                0,
                0,
                16
        );


        card.setLayoutParams(
                params
        );


        return card;
    }


    // ============================================================
    // STEP
    // ============================================================

    private void addStep(
            LinearLayout parent,
            String number,
            String description) {

        TextView step =
                text(
                        number +
                                "    " +
                                description,
                        14
                );


        step.setTextColor(
                Color.DKGRAY
        );


        parent.addView(
                step
        );
    }


    // ============================================================
    // TEXT
    // ============================================================

    private TextView text(
            String value,
            float size) {

        TextView view =
                new TextView(this);


        view.setText(
                value
        );


        view.setTextSize(
                size
        );


        view.setTextColor(
                Color.rgb(
                        25,
                        25,
                        25
                )
        );


        view.setPadding(
                0,
                7,
                0,
                7
        );


        return view;
    }


    // ============================================================
    // BUTTON
    // ============================================================

    private Button button(
            String value) {

        Button button =
                new Button(this);


        button.setText(
                value
        );


        button.setAllCaps(
                false
        );


        return button;
    }


    // ============================================================
    // WEBVIEW CONFIG
    // ============================================================

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView() {

        WebSettings settings =
                webView.getSettings();


        settings.setJavaScriptEnabled(
                true
        );


        settings.setDomStorageEnabled(
                true
        );


        settings.setDatabaseEnabled(
                true
        );


        settings.setAllowFileAccess(
                false
        );


        settings.setAllowContentAccess(
                true
        );


        settings.setSupportMultipleWindows(
                false
        );


        settings.setJavaScriptCanOpenWindowsAutomatically(
                false
        );


        settings.setUserAgentString(
                "Mozilla/5.0 (Linux; Android 12) " +
                        "AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) " +
                        "Chrome/140.0 Mobile Safari/537.36"
        );


        CookieManager
                .getInstance()
                .setAcceptCookie(
                        true
                );


        CookieManager
                .getInstance()
                .setAcceptThirdPartyCookies(
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
                            WebResourceRequest request) {

                        return false;
                    }


                    @Override
                    public void onPageFinished(
                            WebView view,
                            String url) {

                        super.onPageFinished(
                                view,
                                url
                        );


                        if (!processing ||
                                currentIndex < 0) {

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
                                    () ->
                                            waitForSaveTikTokInput(
                                                    urls.get(
                                                            currentIndex
                                                    ),
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
                            WebResourceError error) {

                        if (request.isForMainFrame()) {

                            webStatus.setText(
                                    "WebView: gagal memuat halaman"
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
                ) ->

                        handleWebDownload(
                                url,
                                userAgent,
                                contentDisposition,
                                mimeType,
                                contentLength
                        )
        );
    }


    // ============================================================
    // SAVE TIKTOK PAGE CHECK
    // ============================================================

    private boolean isSaveTikTokPage(
            String url) {

        try {

            String host =
                    Uri.parse(
                            url
                    ).getHost();


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


    // ============================================================
    // LOAD QUEUE
    // ============================================================

    private void loadQueue() {

        LinkedHashSet<String> unique =
                new LinkedHashSet<>();


        for (String line :
                input.getText()
                        .toString()
                        .split("\\r?\\n")) {


            String url =
                    line.trim();


            if (url.startsWith(
                    "http://"
            )
                    ||
                    url.startsWith(
                            "https://"
                    )) {

                unique.add(
                        url
                );
            }
        }


        urls.clear();

        urls.addAll(
                unique
        );


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


        if (finishedList != null) {

            finishedList.removeAllViews();
        }


        for (int i = 0;
             i < urls.size();
             i++) {

            addQueueItem(
                    i,
                    urls.get(i)
            );
        }


        updateProgress();

        updateEmptyStates();


        if (urls.isEmpty()) {

            webStatus.setText(
                    "WebView: tidak ada URL"
            );


            Toast.makeText(
                    this,
                    "Belum ada URL TikTok.",
                    Toast.LENGTH_SHORT
            ).show();


            return;
        }


        webStatus.setText(
                "WebView: antrean siap"
        );


        showPage(1);
    }


    // ============================================================
    // QUEUE ITEM
    // ============================================================

    private void addQueueItem(
            int index,
            String url) {

        LinearLayout card =
                createCard();


        TextView status =
                text(
                        "#" +
                                String.format(
                                        Locale.US,
                                        "%03d",
                                        index + 1
                                ) +
                                "  Menunggu",
                        16
                );


        status.setTypeface(
                null,
                android.graphics.Typeface.BOLD
        );


        TextView link =
                text(
                        url,
                        13
                );


        link.setTextColor(
                Color.DKGRAY
        );


        link.setMaxLines(
                3
        );


        card.addView(
                status
        );


        card.addView(
                link
        );


        card.setTag(
                status
        );


        queue.addView(
                card
        );


        updateEmptyStates();
    }


    // ============================================================
    // START SINGLE
    // ============================================================

    private void startSingle(
            int index) {

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
                "WebView: membuka SaveTikTok..."
        );


        webView.setVisibility(
                View.GONE
        );


        webView.loadUrl(
                SAVE_URL
        );


        showPage(1);
    }


    // ============================================================
    // START ALL
    // ============================================================

    private void startAll() {

        if (urls.isEmpty()) {

            Toast.makeText(
                    this,
                    "Muat antrean dulu.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }


        if (processing) {

            Toast.makeText(
                    this,
                    "Proses masih berjalan.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }


        startSingle(0);
    }


    // ============================================================
    // WAIT INPUT
    // ============================================================

    private void waitForSaveTikTokInput(
            String tiktokUrl,
            int attempt) {

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
                    "Kolom URL tidak ditemukan"
            );


            processing = false;

            return;
        }


        String js =
                "(function(){" +

                        "var fields=" +
                        "Array.from(document.querySelectorAll('input,textarea'));" +

                        "var field=fields.find(function(el){" +

                        "var p=(" +
                        "(el.placeholder||'')+' '+" +
                        "(el.getAttribute('aria-label')||'')+' '+" +
                        "(el.name||'')+' '+" +
                        "(el.type||'')" +
                        ").toLowerCase();" +

                        "return p.includes('tautan')||" +
                        "p.includes('tiktok')||" +
                        "p.includes('link')||" +
                        "p.includes('url')||" +
                        "el.type==='url';" +

                        "});" +

                        "return field?'FOUND':'WAIT';" +

                        "})()";


        webView.evaluateJavascript(
                js,
                result -> {

                    if (result != null &&
                            result.contains(
                                    "FOUND"
                            )) {


                        webStatus.setText(
                                "WebView: kolom URL ditemukan..."
                        );


                        handler.postDelayed(
                                () ->
                                        injectTikTokUrl(
                                                tiktokUrl
                                        ),
                                300
                        );


                    } else {


                        handler.postDelayed(
                                () ->
                                        waitForSaveTikTokInput(
                                                tiktokUrl,
                                                attempt + 1
                                        ),
                                500
                        );
                    }
                }
        );
    }


    // ============================================================
    // INJECT URL
    // ============================================================

    private void injectTikTokUrl(
            String tiktokUrl) {

        if (!processing ||
                currentIndex < 0 ||
                submitClicked) {

            return;
        }


        String safeUrl =
                JSONObject.quote(
                        tiktokUrl
                );


        String js =
                "(function(){" +

                        "var fields=" +
                        "Array.from(document.querySelectorAll('input,textarea'));" +

                        "var field=fields.find(function(el){" +

                        "var p=(" +
                        "(el.placeholder||'')+' '+" +
                        "(el.getAttribute('aria-label')||'')+' '+" +
                        "(el.name||'')+' '+" +
                        "(el.type||'')" +
                        ").toLowerCase();" +

                        "return p.includes('tautan')||" +
                        "p.includes('tiktok')||" +
                        "p.includes('link')||" +
                        "p.includes('url')||" +
                        "el.type==='url';" +

                        "});" +

                        "if(!field)return 'NO_FIELD';" +

                        "var proto=" +
                        "field instanceof HTMLTextAreaElement?" +
                        "HTMLTextAreaElement.prototype:" +
                        "HTMLInputElement.prototype;" +

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

                        "if(field.value!==" +
                        safeUrl +
                        ")return 'VALUE_FAILED';" +

                        "var buttons=" +
                        "Array.from(document.querySelectorAll(" +
                        "'button,input[type=submit]," +
                        "input[type=button],a'));" +

                        "var downloadButton=" +
                        "buttons.find(function(el){" +

                        "var t=(" +
                        "el.innerText||" +
                        "el.textContent||" +
                        "el.value||" +
                        "el.getAttribute('aria-label')||''" +
                        ").replace(/\\s+/g,' ')" +
                        ".trim().toLowerCase();" +

                        "return t==='unduh'||" +
                        "t==='download';" +

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
                            result.contains(
                                    "OK"
                            )) {


                        submitClicked = true;


                        webStatus.setText(
                                "WebView: URL berhasil dikirim..."
                        );


                        setStatus(
                                currentIndex,
                                "Menunggu hasil SaveTikTok"
                        );


                        handler.postDelayed(
                                () ->
                                        findMp4HdButton(
                                                0
                                        ),
                                2000
                        );


                    } else {


                        submitClicked = false;


                        webStatus.setText(
                                "WebView: mencoba mengirim URL lagi..."
                        );


                        handler.postDelayed(
                                () ->
                                        waitForSaveTikTokInput(
                                                tiktokUrl,
                                                0
                                        ),
                                1000
                        );
                    }
                }
        );
    }


    // ============================================================
    // FIND MP4 HD
    // ============================================================

    private void findMp4HdButton(
            int attempt) {

        if (!processing ||
                currentIndex < 0 ||
                downloadStarted) {

            return;
        }


        if (attempt >= MAX_MP4_HD_CHECKS) {

            webStatus.setText(
                    "WebView: tombol MP4 HD tidak ditemukan"
            );


            setStatus(
                    currentIndex,
                    "MP4 HD tidak ditemukan"
            );


            processing = false;

            return;
        }


        String js =
                "(function(){" +

                        "var root=" +
                        "document.querySelector('#download-result');" +

                        "if(!root)" +
                        "return JSON.stringify({state:'WAIT'});" +

                        "var card=" +
                        "root.querySelector(" +
                        "'.video-data .tik-video'" +
                        ");" +

                        "if(!card)" +
                        "return JSON.stringify({state:'WAIT'});" +

                        "var captionEl=" +
                        "card.querySelector(" +
                        "'.tik-left .thumbnail .content .clearfix h3'" +
                        ");" +

                        "var buttons=" +
                        "Array.from(" +
                        "card.querySelectorAll(" +
                        "'.dl-action a.tik-button-dl'" +
                        ")" +
                        ");" +

                        "var hd=buttons.find(function(el){" +

                        "var t=(" +
                        "el.innerText||" +
                        "el.textContent||''" +
                        ").replace(/\\s+/g,' ')" +
                        ".trim().toLowerCase();" +

                        "return t==='unduh mp4 hd'||" +
                        "t==='download mp4 hd';" +

                        "});" +

                        "if(!hd)" +
                        "return JSON.stringify({state:'WAIT'});" +

                        "var caption=captionEl?" +
                        "(captionEl.innerText||" +
                        "captionEl.textContent||'').trim():" +
                        "'';" +

                        "return JSON.stringify({" +
                        "state:'FOUND'," +
                        "caption:caption," +
                        "hasCaption:!!captionEl" +
                        "});" +

                        "})()";


        webView.evaluateJavascript(
                js,
                result -> {

                    if (result == null ||
                            !result.contains(
                                    "\\\"state\\\":\\\"FOUND\\\""
                            )) {


                        webStatus.setText(
                                "WebView: menunggu caption dan MP4 HD..."
                        );


                        handler.postDelayed(
                                () ->
                                        findMp4HdButton(
                                                attempt + 1
                                        ),
                                500
                        );


                        return;
                    }


                    try {

                        Object parsed =
                                new org.json.JSONTokener(
                                        result
                                ).nextValue();


                        String jsonText =
                                parsed instanceof String
                                        ? (String) parsed
                                        : String.valueOf(
                                                parsed
                                        );


                        JSONObject obj =
                                new JSONObject(
                                        jsonText
                                );


                        currentCaption =
                                obj.optString(
                                        "caption",
                                        ""
                                ).trim();


                        if (currentCaption.isEmpty()) {

                            webStatus.setText(
                                    "WebView: caption kosong"
                            );


                            setStatus(
                                    currentIndex,
                                    "Caption kosong"
                            );


                            processing = false;

                            return;
                        }


                        webStatus.setText(
                                "WebView: caption ditemukan"
                        );


                        setStatus(
                                currentIndex,
                                "Caption ditemukan"
                        );


                        // =================================================
                        // CLICK MP4 HD
                        // =================================================

                        String clickJs =
                                "(function(){" +

                                        "var card=" +
                                        "document.querySelector(" +
                                        "'#download-result .video-data .tik-video'" +
                                        ");" +

                                        "if(!card)return 'WAIT';" +

                                        "var buttons=" +
                                        "Array.from(" +
                                        "card.querySelectorAll(" +
                                        "'.dl-action a.tik-button-dl'" +
                                        ")" +
                                        ");" +

                                        "var hd=buttons.find(function(el){" +

                                        "var t=(" +
                                        "el.innerText||" +
                                        "el.textContent||''" +
                                        ").replace(/\\s+/g,' ')" +
                                        ".trim().toLowerCase();" +

                                        "return t==='unduh mp4 hd'||" +
                                        "t==='download mp4 hd';" +

                                        "});" +

                                        "if(!hd)return 'WAIT';" +

                                        "hd.click();" +

                                        "return 'CLICKED';" +

                                        "})()";


                        webView.evaluateJavascript(
                                clickJs,
                                clickResult -> {

                                    if (clickResult != null &&
                                            clickResult.contains(
                                                    "CLICKED"
                                            )) {


                                        submitClicked = true;


                                        webStatus.setText(
                                                "WebView: MP4 HD dipilih..."
                                        );


                                        setStatus(
                                                currentIndex,
                                                "MP4 HD dipilih"
                                        );


                                    } else {


                                        webStatus.setText(
                                                "WebView: gagal klik MP4 HD"
                                        );


                                        setStatus(
                                                currentIndex,
                                                "Gagal klik MP4 HD"
                                        );


                                        processing = false;
                                    }
                                }
                        );


                    } catch (Exception e) {

                        webStatus.setText(
                                "WebView: gagal membaca caption"
                        );


                        setStatus(
                                currentIndex,
                                "Gagal membaca caption"
                        );


                        processing = false;
                    }
                }
        );
    }


    // ============================================================
    // DOWNLOAD LISTENER
    // ============================================================

    private void handleWebDownload(
            String url,
            String userAgent,
            String contentDisposition,
            String mimeType,
            long contentLength) {

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
                normalizedUrl
        )) {

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
                lowerMime.contains(
                        "text/html"
                ) ||
                        lowerMime.contains(
                                "application/xhtml"
                        );


        if (looksLikeHtml) {

            return;
        }


        boolean isBin =
                lowerMime.contains(
                        "application/octet-stream"
                ) ||
                        lowerDisposition.contains(
                                ".bin"
                        ) ||
                        lowerUrl.matches(
                                ".*\\.bin(?:[?#].*)?$"
                        );


        boolean looksLikeVideo =
                lowerMime.startsWith(
                        "video/"
                ) ||
                        lowerMime.contains(
                                "mp4"
                        ) ||
                        lowerDisposition.contains(
                                ".mp4"
                        ) ||
                        lowerUrl.contains(
                                ".mp4"
                        );


        if (isBin &&
                submitClicked) {

            looksLikeVideo = true;
        }


        if (!looksLikeVideo) {

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


            handledDownloadUrls.remove(
                    first
            );
        }


        downloadStarted = true;


        webStatus.setText(
                "WebView: file MP4 ditemukan..."
        );


        setStatus(
                currentIndex,
                "Memulai download"
        );


        enqueueDownload(
                normalizedUrl,
                userAgent,
                mimeType,
                contentDisposition
        );
    }


    // ============================================================
    // ENQUEUE DOWNLOAD
    // ============================================================

    private void enqueueDownload(
            String url,
            String userAgent,
            String mimeType,
            String contentDisposition) {

        try {

            DownloadManager manager =
                    (DownloadManager)
                            getSystemService(
                                    Context.DOWNLOAD_SERVICE
                            );


            DownloadManager.Request request =
                    new DownloadManager.Request(
                            Uri.parse(
                                    url
                            )
                    );


            int folderNumber =
                    currentIndex + 1;


            String folder =
                    "TikTokDownloadManager/" +
                            String.format(
                                    Locale.US,
                                    "%03d",
                                    folderNumber
                            );


            request.setTitle(
                    "TikTok " +
                            String.format(
                                    Locale.US,
                                    "%03d",
                                    folderNumber
                            )
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
                    CookieManager
                            .getInstance()
                            .getCookie(
                                    url
                            );


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


            request.setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    folder +
                            "/video.mp4"
            );


            currentDownloadId =
                    manager.enqueue(
                            request
                    );


            if (currentDownloadId < 0) {

                throw new Exception(
                        "DownloadManager menolak request"
                );
            }


            setStatus(
                    currentIndex,
                    "Download dimulai"
            );


            webStatus.setText(
                    "Download: " +
                            folder +
                            "/video.mp4"
            );


            progress.setText(
                    "Progress: " +
                            folderNumber +
                            " / " +
                            urls.size()
            );


            saveCaptionFile(
                    currentIndex
            );


            Toast.makeText(
                    this,
                    "Download dimulai #" +
                            String.format(
                                    Locale.US,
                                    "%03d",
                                    folderNumber
                            ),
                    Toast.LENGTH_SHORT
            ).show();


            waitForDownloadCompletion(
                    manager,
                    currentDownloadId,
                    currentIndex,
                    0
            );


        } catch (Exception e) {

            downloadStarted = false;

            processing = false;

            currentDownloadId = -1L;


            setStatus(
                    currentIndex,
                    "Gagal memulai download"
            );


            webStatus.setText(
                    "Download gagal"
            );
        }
    }


    // ============================================================
    // SAVE CAPTION
    // ============================================================

    private void saveCaptionFile(
            int index) {

        try {

            String caption =
                    currentCaption == null
                            ? ""
                            : currentCaption.trim();


            String content =
                    caption.isEmpty()
                            ? "Caption tidak ditemukan."
                            : caption;


            String folder =
                    "TikTokDownloadManager/" +
                            String.format(
                                    Locale.US,
                                    "%03d",
                                    index + 1
                            );


            if (Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.Q) {


                android.content.ContentValues values =
                        new android.content.ContentValues();


                values.put(
                        android.provider.MediaStore.Downloads.DISPLAY_NAME,
                        "caption.txt"
                );


                values.put(
                        android.provider.MediaStore.Downloads.MIME_TYPE,
                        "text/plain"
                );


                values.put(
                        android.provider.MediaStore.Downloads.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS +
                                "/" +
                                folder
                );


                values.put(
                        android.provider.MediaStore.Downloads.IS_PENDING,
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
                                            .openOutputStream(
                                                    uri
                                            )
                    ) {

                        if (out != null) {

                            out.write(
                                    content.getBytes(
                                            java.nio.charset.StandardCharsets.UTF_8
                                    )
                            );
                        }
                    }


                    values.clear();


                    values.put(
                            android.provider.MediaStore.Downloads.IS_PENDING,
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
                                                Environment.DIRECTORY_DOWNLOADS
                                        ),
                                folder
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
                            content.getBytes(
                                    "UTF-8"
                            )
                    );
                }
            }


        } catch (Exception e) {

            webStatus.setText(
                    "Video diproses, caption gagal disimpan"
            );
        }
    }


    // ============================================================
    // DOWNLOAD MONITOR
    // ============================================================

    private void waitForDownloadCompletion(
            DownloadManager manager,
            long downloadId,
            int completedIndex,
            int attempt) {


        if (!processing ||
                currentIndex != completedIndex ||
                currentDownloadId != downloadId) {

            return;
        }


        // ========================================================
        // TIMEOUT
        // ========================================================

        if (attempt >=
                MAX_DOWNLOAD_CHECKS) {


            setStatus(
                    completedIndex,
                    "Download belum selesai"
            );


            webStatus.setText(
                    "Download belum selesai setelah 10 menit. Antrean dihentikan."
            );


            Toast.makeText(
                    this,
                    "Download belum selesai. Antrean dihentikan.",
                    Toast.LENGTH_LONG
            ).show();


            processing = false;

            return;
        }


        DownloadManager.Query query =
                new DownloadManager.Query();


        query.setFilterById(
                downloadId
        );


        try (
                android.database.Cursor cursor =
                        manager.query(
                                query
                        )
        ) {


            if (cursor == null ||
                    !cursor.moveToFirst()) {


                handler.postDelayed(
                        () ->
                                waitForDownloadCompletion(
                                        manager,
                                        downloadId,
                                        completedIndex,
                                        attempt + 1
                                ),
                        DOWNLOAD_CHECK_INTERVAL
                );


                return;
            }


            int statusColumn =
                    cursor.getColumnIndex(
                            DownloadManager.COLUMN_STATUS
                    );


            if (statusColumn < 0) {


                handler.postDelayed(
                        () ->
                                waitForDownloadCompletion(
                                        manager,
                                        downloadId,
                                        completedIndex,
                                        attempt + 1
                                ),
                        DOWNLOAD_CHECK_INTERVAL
                );


                return;
            }


            int status =
                    cursor.getInt(
                            statusColumn
                    );


            // ====================================================
            // SUCCESS
            // ====================================================

            if (status ==
                    DownloadManager.STATUS_SUCCESSFUL) {


                setStatus(
                        completedIndex,
                        "Selesai ✓"
                );


                webStatus.setText(
                        "Download selesai ✓"
                );


                /*
                 * Simpan ID sebelum currentDownloadId
                 * di-reset.
                 */

                long finishedDownloadId =
                        downloadId;


                /*
                 * Caption juga disalin sebelum
                 * state dibersihkan.
                 */

                String finishedCaption =
                        currentCaption == null
                                ? ""
                                : currentCaption;


                downloadStarted = false;

                currentDownloadId = -1L;


                // =================================================
                // TAMBAHKAN PREVIEW VIDEO
                // =================================================

                addFinishedItem(
                        completedIndex,
                        finishedCaption,
                        finishedDownloadId
                );


                handler.postDelayed(
                        () ->
                                finishCurrentAndNext(
                                        completedIndex
                                ),
                        700
                );


                return;
            }


            // ====================================================
            // FAILED
            // ====================================================

            if (status ==
                    DownloadManager.STATUS_FAILED) {


                int reasonColumn =
                        cursor.getColumnIndex(
                                DownloadManager.COLUMN_REASON
                        );


                int reason =
                        reasonColumn >= 0
                                ? cursor.getInt(
                                        reasonColumn
                                )
                                : -1;


                downloadStarted = false;

                currentDownloadId = -1L;

                processing = false;


                setStatus(
                        completedIndex,
                        "Gagal download"
                );


                webStatus.setText(
                        "Download gagal"
                );


                Toast.makeText(
                        this,
                        "Download #" +
                                String.format(
                                        Locale.US,
                                        "%03d",
                                        completedIndex + 1
                                ) +
                                " gagal. Kode: " +
                                reason,
                        Toast.LENGTH_LONG
                ).show();


                return;
            }


            // ====================================================
            // RUNNING
            // ====================================================

            int downloadedColumn =
                    cursor.getColumnIndex(
                            DownloadManager
                                    .COLUMN_BYTES_DOWNLOADED_SO_FAR
                    );


            int totalColumn =
                    cursor.getColumnIndex(
                            DownloadManager
                                    .COLUMN_TOTAL_SIZE_BYTES
                    );


            long downloaded =
                    downloadedColumn >= 0
                            ? cursor.getLong(
                                    downloadedColumn
                            )
                            : -1L;


            long total =
                    totalColumn >= 0
                            ? cursor.getLong(
                                    totalColumn
                            )
                            : -1L;


            if (total > 0 &&
                    downloaded >= 0) {


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
                        "Download " +
                                percent +
                                "%"
                );


            } else {


                setStatus(
                        completedIndex,
                        "Download berjalan"
                );


                webStatus.setText(
                        "Download berjalan..."
                );
            }


        } catch (Exception e) {

            /*
             * Error query sementara tidak dianggap
             * selesai.
             */
        }


        handler.postDelayed(
                () ->
                        waitForDownloadCompletion(
                                manager,
                                downloadId,
                                completedIndex,
                                attempt + 1
                        ),
                DOWNLOAD_CHECK_INTERVAL
        );
    }


    // ============================================================
    // FINISHED ITEM DENGAN VIDEO PREVIEW
    // ============================================================

    private void addFinishedItem(
            int index,
            String caption,
            long downloadId) {

        if (finishedList == null) {
            return;
        }


        LinearLayout card =
                createCard();


        // ========================================================
        // NOMOR
        // ========================================================

        TextView number =
                text(
                        "#" +
                                String.format(
                                        Locale.US,
                                        "%03d",
                                        index + 1
                                ),
                        15
                );


        number.setTypeface(
                null,
                android.graphics.Typeface.BOLD
        );


        card.addView(
                number
        );


        // ========================================================
        // VIDEO PREVIEW
        // ========================================================

        FrameLayout videoContainer =
                new FrameLayout(this);


        videoContainer.setBackgroundColor(
                Color.BLACK
        );


        VideoView videoView =
                new VideoView(this);


        FrameLayout.LayoutParams
                videoParams =
                new FrameLayout.LayoutParams(
                        -1,
                        420
                );


        videoParams.gravity =
                Gravity.CENTER;


        videoContainer.addView(
                videoView,
                videoParams
        );


        /*
         * Ambil URI hasil download dari
         * DownloadManager.
         */

        try {

            DownloadManager manager =
                    (DownloadManager)
                            getSystemService(
                                    Context.DOWNLOAD_SERVICE
                            );


            Uri videoUri =
                    manager.getUriForDownloadedFile(
                            downloadId
                    );


            if (videoUri != null) {


                videoView.setVideoURI(
                        videoUri
                );


                MediaController controller =
                        new MediaController(
                                this
                        );


                controller.setAnchorView(
                        videoView
                );


                videoView.setMediaController(
                        controller
                );


                videoView.setOnPreparedListener(
                        mp -> {

                            /*
                             * Jangan autoplay.
                             * User menekan Play sendiri.
                             */

                            mp.setLooping(
                                    false
                            );
                        }
                );


                videoView.setOnErrorListener(
                        (mp,
                         what,
                         extra) -> {

                            Toast.makeText(
                                    this,
                                    "Video preview tidak dapat diputar.",
                                    Toast.LENGTH_SHORT
                            ).show();


                            return true;
                        }
                );


            } else {

                TextView unavailable =
                        text(
                                "Preview tidak tersedia",
                                14
                        );


                unavailable.setTextColor(
                        Color.WHITE
                );


                unavailable.setGravity(
                        Gravity.CENTER
                );


                videoContainer.addView(
                        unavailable,
                        new FrameLayout.LayoutParams(
                                -1,
                                -1
                        )
                );
            }


        } catch (Exception e) {

            TextView unavailable =
                    text(
                            "Preview tidak tersedia",
                            14
                    );


            unavailable.setTextColor(
                    Color.WHITE
            );


            unavailable.setGravity(
                    Gravity.CENTER
            );


            videoContainer.addView(
                    unavailable,
                    new FrameLayout.LayoutParams(
                            -1,
                            -1
                    )
            );
        }


        LinearLayout.LayoutParams
                videoContainerParams =
                new LinearLayout.LayoutParams(
                        -1,
                        420
                );


        videoContainerParams.setMargins(
                0,
                12,
                0,
                14
        );


        card.addView(
                videoContainer,
                videoContainerParams
        );


        // ========================================================
        // STATUS
        // ========================================================

        TextView status =
                text(
                        "Selesai ✓",
                        14
                );


        status.setTextColor(
                Color.rgb(
                        30,
                        130,
                        70
                )
        );


        card.addView(
                status
        );


        // ========================================================
        // CAPTION
        // ========================================================

        String captionValue =
                caption == null
                        ? ""
                        : caption.trim();


        if (captionValue.isEmpty()) {

            captionValue =
                    "Caption tidak ditemukan.";
        }


        TextView captionText =
                text(
                        captionValue,
                        14
                );


        captionText.setTextColor(
                Color.DKGRAY
        );


        captionText.setMaxLines(
                6
        );


        card.addView(
                captionText
        );


        // ========================================================
        // LOCATION
        // ========================================================

        TextView location =
                text(
                        "Download/TikTokDownloadManager/" +
                                String.format(
                                        Locale.US,
                                        "%03d",
                                        index + 1
                                ),
                        12
                );


        location.setTextColor(
                Color.GRAY
        );


        card.addView(
                location
        );


        // ========================================================
        // ADD TO TOP
        // ========================================================

        finishedList.addView(
                card,
                0
        );


        updateEmptyStates();
    }


    // ============================================================
    // FINISH CURRENT AND NEXT
    // ============================================================

    private void finishCurrentAndNext(
            int completedIndex) {


        processing = false;

        submitClicked = false;

        downloadStarted = false;

        currentDownloadId = -1L;

        currentCaption = "";


        int next =
                completedIndex + 1;


        // ========================================================
        // NEXT
        // ========================================================

        if (next < urls.size()) {


            startSingle(
                    next
            );


            return;
        }


        // ========================================================
        // ALL FINISHED
        // ========================================================

        currentIndex = -1;


        webStatus.setText(
                "Semua antrean selesai ✓"
        );


        progress.setText(
                "Progress: " +
                        urls.size() +
                        " / " +
                        urls.size()
        );


        updateEmptyStates();


        Toast.makeText(
                this,
                "Semua antrean sudah selesai.",
                Toast.LENGTH_LONG
        ).show();


        showPage(2);
    }


    // ============================================================
    // SET STATUS
    // ============================================================

    private void setStatus(
            int index,
            String value) {

        if (queue == null) {
            return;
        }


        if (index < 0 ||
                index >= queue.getChildCount()) {

            return;
        }


        View card =
                queue.getChildAt(
                        index
                );


        Object tag =
                card.getTag();


        if (tag instanceof TextView) {


            ((TextView) tag).setText(
                    "#" +
                            String.format(
                                    Locale.US,
                                    "%03d",
                                    index + 1
                            ) +
                            "  " +
                            value
            );
        }
    }


    // ============================================================
    // UPDATE PROGRESS
    // ============================================================

    private void updateProgress() {

        if (progress == null) {
            return;
        }


        progress.setText(
                "Progress: 0 / " +
                        urls.size()
        );
    }


    // ============================================================
    // EMPTY STATE
    // ============================================================

    private void updateEmptyStates() {

        if (emptyProgress != null) {

            emptyProgress.setVisibility(
                    urls.isEmpty()
                            ? View.VISIBLE
                            : View.GONE
            );
        }


        if (emptyFinished != null &&
                finishedList != null) {

            emptyFinished.setVisibility(
                    finishedList.getChildCount() == 0
                            ? View.VISIBLE
                            : View.GONE
            );
        }
    }


    // ============================================================
    // CLEAR
    // ============================================================

    private void clearQueue() {

        if (processing) {

            Toast.makeText(
                    this,
                    "Tidak bisa membersihkan saat proses berjalan.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }


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


        if (finishedList != null) {

            finishedList.removeAllViews();
        }


        progress.setText(
                "Progress: 0 / 0"
        );


        webStatus.setText(
                "WebView: siap"
        );


        webView.stopLoading();


        updateEmptyStates();


        showPage(0);
    }


    // ============================================================
    // STORAGE PERMISSION
    // ============================================================

    private void requestStorageIfNeeded() {

        if (Build.VERSION.SDK_INT >= 23 &&
                Build.VERSION.SDK_INT <= 28 &&
                checkSelfPermission(
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
                        != PackageManager.PERMISSION_GRANTED) {


            requestPermissions(
                    new String[]{
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                    },
                    REQ_STORAGE
            );
        }
    }


    // ============================================================
    // BACK
    // ============================================================

    @Override
    public void onBackPressed() {

        if (currentPage != 0) {

            showPage(0);

            return;
        }


        super.onBackPressed();
    }


    // ============================================================
    // DESTROY
    // ============================================================

    @Override
    protected void onDestroy() {

        handler.removeCallbacksAndMessages(
                null
        );


        if (webView != null) {

            webView.stopLoading();

            webView.destroy();

            webView = null;
        }


        super.onDestroy();
    }
}
