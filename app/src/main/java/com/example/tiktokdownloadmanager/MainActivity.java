package com.example.tiktokdownloadmanager;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
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
    // LIMIT / TIMING
    // ============================================================

    private static final int REQ_STORAGE = 501;

    private static final int MAX_INPUT_CHECKS = 20;

    private static final int MAX_MP4_HD_CHECKS = 30;

    /*
     * 1200 x 500ms = sekitar 10 menit.
     */
    private static final int MAX_DOWNLOAD_CHECKS = 1200;

    private static final long DOWNLOAD_CHECK_INTERVAL = 500L;


    // ============================================================
    // UI
    // ============================================================

    private FrameLayout pageContainer;

    private LinearLayout downloadPage;

    private LinearLayout progressPage;

    private LinearLayout finishedPage;

    private LinearLayout queue;

    private LinearLayout finishedList;

    private EditText input;

    private TextView progress;

    private TextView webStatus;

    private Button downloadNav;

    private Button progressNav;

    private Button finishedNav;


    // ============================================================
    // WEBVIEW
    // ============================================================

    private WebView webView;


    // ============================================================
    // DATA
    // ============================================================

    private final ArrayList<String> urls =
            new ArrayList<>();

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private final LinkedHashSet<String>
            handledDownloadUrls =
            new LinkedHashSet<>();


    // ============================================================
    // CURRENT PROCESS
    // ============================================================

    private int currentIndex = -1;

    private boolean processing = false;

    private boolean submitClicked = false;

    private boolean downloadStarted = false;

    private String currentCaption = "";

    private long currentDownloadId = -1L;


    // ============================================================
    // ACTIVITY
    // ============================================================

    @Override
    protected void onCreate(
            Bundle savedInstanceState
    ) {

        super.onCreate(savedInstanceState);

        buildUi();

        requestStorageIfNeeded();
    }


    // ============================================================
    // BUILD UI
    // ============================================================

    private void buildUi() {

        FrameLayout root =
                new FrameLayout(this);

        root.setBackgroundColor(
                Color.rgb(246, 247, 249)
        );


        // --------------------------------------------------------
        // STATUS BAR SPACE
        // --------------------------------------------------------

        int statusBarHeight =
                getStatusBarHeight();

        root.setPadding(
                0,
                statusBarHeight,
                0,
                0
        );


        // --------------------------------------------------------
        // PAGE CONTAINER
        // --------------------------------------------------------

        pageContainer =
                new FrameLayout(this);


        FrameLayout.LayoutParams
                pageParams =
                new FrameLayout.LayoutParams(
                        -1,
                        -1
                );

        pageParams.bottomMargin = 74;


        root.addView(
                pageContainer,
                pageParams
        );


        // --------------------------------------------------------
        // PAGES
        // --------------------------------------------------------

        downloadPage =
                buildDownloadPage();

        progressPage =
                buildProgressPage();

        finishedPage =
                buildFinishedPage();


        pageContainer.addView(
                downloadPage,
                matchParams()
        );

        pageContainer.addView(
                progressPage,
                matchParams()
        );

        pageContainer.addView(
                finishedPage,
                matchParams()
        );


        // --------------------------------------------------------
        // BOTTOM NAV
        // --------------------------------------------------------

        LinearLayout bottom =
                new LinearLayout(this);

        bottom.setOrientation(
                LinearLayout.HORIZONTAL
        );

        bottom.setGravity(
                Gravity.CENTER
        );

        bottom.setPadding(
                8,
                4,
                8,
                4
        );

        bottom.setBackgroundColor(
                Color.WHITE
        );


        downloadNav =
                navButton(
                        "⌂",
                        "Download"
                );


        progressNav =
                navButton(
                        "↓",
                        "Progress"
                );


        finishedNav =
                navButton(
                        "✓",
                        "Finished"
                );


        bottom.addView(
                downloadNav,
                new LinearLayout.LayoutParams(
                        0,
                        -1,
                        1
                )
        );


        bottom.addView(
                progressNav,
                new LinearLayout.LayoutParams(
                        0,
                        -1,
                        1
                )
        );


        bottom.addView(
                finishedNav,
                new LinearLayout.LayoutParams(
                        0,
                        -1,
                        1
                )
        );


        FrameLayout.LayoutParams
                bottomParams =
                new FrameLayout.LayoutParams(
                        -1,
                        74,
                        Gravity.BOTTOM
                );


        root.addView(
                bottom,
                bottomParams
        );


        // --------------------------------------------------------
        // NAV CLICK
        // --------------------------------------------------------

        downloadNav.setOnClickListener(
                v -> showPage(0)
        );

        progressNav.setOnClickListener(
                v -> showPage(1)
        );

        finishedNav.setOnClickListener(
                v -> showPage(2)
        );


        // --------------------------------------------------------
        // CONTENT
        // --------------------------------------------------------

        setContentView(root);


        // --------------------------------------------------------
        // HIDDEN WEBVIEW
        // --------------------------------------------------------

        webView =
                new WebView(this);

        webView.setVisibility(
                View.GONE
        );


        root.addView(
                webView,
                new FrameLayout.LayoutParams(
                        1,
                        1
                )
        );


        configureWebView();


        // --------------------------------------------------------
        // DEFAULT PAGE
        // --------------------------------------------------------

        showPage(0);
    }


    // ============================================================
    // DOWNLOAD PAGE
    // ============================================================

    private LinearLayout buildDownloadPage() {

        LinearLayout page =
                new LinearLayout(this);

        page.setOrientation(
                LinearLayout.VERTICAL
        );


        ScrollView scroll =
                new ScrollView(this);

        scroll.setFillViewport(
                true
        );


        LinearLayout content =
                new LinearLayout(this);

        content.setOrientation(
                LinearLayout.VERTICAL
        );

        content.setPadding(
                20,
                12,
                20,
                24
        );


        // --------------------------------------------------------
        // HEADER
        // --------------------------------------------------------

        LinearLayout header =
                new LinearLayout(this);

        header.setGravity(
                Gravity.CENTER_VERTICAL
        );


        TextView title =
                text(
                        "Video Downloader",
                        24
                );

        title.setTypeface(
                null,
                Typeface.BOLD
        );


        header.addView(
                title,
                new LinearLayout.LayoutParams(
                        0,
                        64,
                        1
                )
        );


        TextView settings =
                text(
                        "⚙",
                        24
                );

        settings.setGravity(
                Gravity.CENTER
        );


        header.addView(
                settings,
                new LinearLayout.LayoutParams(
                        52,
                        64
                )
        );


        content.addView(
                header
        );


        // --------------------------------------------------------
        // URL INPUT CARD
        // --------------------------------------------------------

        LinearLayout search =
                roundedBox(
                        Color.WHITE,
                        Color.rgb(
                                225,
                                226,
                                230
                        ),
                        16,
                        1
                );


        search.setGravity(
                Gravity.CENTER_VERTICAL
        );


        TextView searchIcon =
                text(
                        "⌕",
                        25
                );

        searchIcon.setTextColor(
                Color.GRAY
        );


        search.addView(
                searchIcon,
                new LinearLayout.LayoutParams(
                        38,
                        58
                )
        );


        input =
                new EditText(this);

        input.setHint(
                "Tempel link TikTok di sini"
        );

        input.setTextSize(
                15
        );

        input.setSingleLine(
                false
        );

        input.setMinLines(
                1
        );

        input.setMaxLines(
                5
        );

        input.setPadding(
                4,
                4,
                4,
                4
        );

        input.setBackgroundColor(
                Color.TRANSPARENT
        );


        search.addView(
                input,
                new LinearLayout.LayoutParams(
                        0,
                        64,
                        1
                )
        );


        content.addView(
                search,
                marginParams(
                        0,
                        6,
                        0,
                        12
                )
        );


        // --------------------------------------------------------
        // LOAD QUEUE BUTTON
        // --------------------------------------------------------

        Button load =
                button(
                        "Tambahkan ke Antrean"
                );


        load.setTextSize(
                14
        );


        content.addView(
                load,
                new LinearLayout.LayoutParams(
                        -1,
                        54
                )
        );


        load.setOnClickListener(
                v -> {

                    loadQueue();

                    if (!urls.isEmpty()) {

                        showPage(1);

                    }

                }
        );


        // --------------------------------------------------------
        // CLEAR BUTTON
        // --------------------------------------------------------

        Button clear =
                button(
                        "Bersihkan"
                );


        clear.setTextSize(
                13
        );


        content.addView(
                clear,
                marginParams(
                        0,
                        8,
                        0,
                        18
                )
        );


        clear.setOnClickListener(
                v -> clearQueue()
        );


        // --------------------------------------------------------
        // INFO CARD
        // --------------------------------------------------------

        LinearLayout info =
                roundedBox(
                        Color.WHITE,
                        Color.rgb(
                                230,
                                231,
                                234
                        ),
                        16,
                        1
                );


        info.setPadding(
                14,
                10,
                14,
                10
        );


        TextView infoTitle =
                text(
                        "Cara kerja",
                        15
                );


        infoTitle.setTypeface(
                null,
                Typeface.BOLD
        );


        info.addView(
                infoTitle
        );


        TextView infoText =
                text(
                        "Masukkan link TikTok → masuk antrean → "
                                + "aplikasi mengambil MP4 HD → "
                                + "video dan caption disimpan otomatis.",
                        12
                );


        infoText.setTextColor(
                Color.GRAY
        );


        infoText.setPadding(
                0,
                4,
                0,
                4
        );


        info.addView(
                infoText
        );


        content.addView(
                info,
                marginParams(
                        0,
                        0,
                        0,
                        18
                )
        );


        // --------------------------------------------------------
        // PLATFORM
        // --------------------------------------------------------

        TextView supported =
                text(
                        "Supported",
                        17
                );


        supported.setTypeface(
                null,
                Typeface.BOLD
        );


        content.addView(
                supported,
                marginParams(
                        0,
                        0,
                        0,
                        6
                )
        );


        LinearLayout grid =
                new LinearLayout(this);

        grid.setOrientation(
                LinearLayout.VERTICAL
        );


        String[][] platforms = {

                {"●", "WhatsApp"},
                {"V", "Vimeo"},
                {"P", "Pinterest"},
                {"F", "Facebook"},

                {"▶", "YouTube"},
                {"◎", "Instagram"},
                {"D", "Dailymotion"},
                {"♪", "TikTok"}

        };


        for (
                int r = 0;
                r < 2;
                r++
        ) {

            LinearLayout row =
                    new LinearLayout(this);

            row.setGravity(
                    Gravity.CENTER
            );


            for (
                    int c = 0;
                    c < 4;
                    c++
            ) {

                int idx =
                        r * 4 + c;


                LinearLayout item =
                        new LinearLayout(this);

                item.setOrientation(
                        LinearLayout.VERTICAL
                );

                item.setGravity(
                        Gravity.CENTER
                );


                TextView circle =
                        text(
                                platforms[idx][0],
                                20
                        );

                circle.setGravity(
                        Gravity.CENTER
                );

                circle.setTextColor(
                        Color.WHITE
                );

                circle.setBackground(
                        round(
                                Color.rgb(
                                        70 + idx * 7,
                                        120 + idx * 4,
                                        190
                                ),
                                50
                        )
                );


                item.addView(
                        circle,
                        new LinearLayout.LayoutParams(
                                46,
                                46
                        )
                );


                TextView label =
                        text(
                                platforms[idx][1],
                                11
                        );

                label.setGravity(
                        Gravity.CENTER
                );

                label.setMaxLines(
                        1
                );


                item.addView(
                        label,
                        new LinearLayout.LayoutParams(
                                -1,
                                32
                        )
                );


                row.addView(
                        item,
                        new LinearLayout.LayoutParams(
                                0,
                                82,
                                1
                        )
                );
            }


            grid.addView(
                    row,
                    new LinearLayout.LayoutParams(
                            -1,
                            82
                    )
            );
        }


        content.addView(
                grid,
                marginParams(
                        0,
                        0,
                        0,
                        18
                )
        );


        // --------------------------------------------------------
        // HOW TO DOWNLOAD
        // --------------------------------------------------------

        Button how =
                button(
                        "How to Download?   ›"
                );

        how.setTextSize(
                14
        );


        content.addView(
                how,
                new LinearLayout.LayoutParams(
                        -1,
                        52
                )
        );


        TextView feedback =
                text(
                        "▢  Feedback or Suggestion",
                        13
                );

        feedback.setGravity(
                Gravity.CENTER
        );

        feedback.setTextColor(
                Color.GRAY
        );


        content.addView(
                feedback,
                new LinearLayout.LayoutParams(
                        -1,
                        46
                )
        );


        // --------------------------------------------------------
        // SCROLL
        // --------------------------------------------------------

        scroll.addView(
                content,
                new ScrollView.LayoutParams(
                        -1,
                        -2
                )
        );


        page.addView(
                scroll,
                new LinearLayout.LayoutParams(
                        -1,
                        0,
                        1
                )
        );


        return page;
    }


    // ============================================================
    // PROGRESS PAGE
    // ============================================================

    private LinearLayout buildProgressPage() {

        LinearLayout page =
                new LinearLayout(this);

        page.setOrientation(
                LinearLayout.VERTICAL
        );

        page.setPadding(
                20,
                12,
                20,
                16
        );


        // --------------------------------------------------------
        // HEADER
        // --------------------------------------------------------

        TextView title =
                text(
                        "Progress",
                        24
                );

        title.setTypeface(
                null,
                Typeface.BOLD
        );


        page.addView(
                title,
                new LinearLayout.LayoutParams(
                        -1,
                        64
                )
        );


        // --------------------------------------------------------
        // START BUTTON
        // --------------------------------------------------------

        Button start =
                button(
                        "Mulai Semua"
                );


        start.setTextSize(
                14
        );


        page.addView(
                start,
                marginParams(
                        0,
                        0,
                        0,
                        10
                )
        );


        start.setOnClickListener(
                v -> startAll()
        );


        // --------------------------------------------------------
        // QUEUE
        // --------------------------------------------------------

        ScrollView scroll =
                new ScrollView(this);

        scroll.setFillViewport(
                true
        );


        queue =
                new LinearLayout(this);

        queue.setOrientation(
                LinearLayout.VERTICAL
        );


        scroll.addView(
                queue,
                new ScrollView.LayoutParams(
                        -1,
                        -2
                )
        );


        page.addView(
                scroll,
                new LinearLayout.LayoutParams(
                        -1,
                        0,
                        1
                )
        );


        // --------------------------------------------------------
        // HIDDEN STATUS
        // --------------------------------------------------------

        webStatus =
                text(
                        "WebView: siap",
                        12
                );

        webStatus.setVisibility(
                View.GONE
        );


        page.addView(
                webStatus,
                new LinearLayout.LayoutParams(
                        1,
                        1
                )
        );


        progress =
                text(
                        "Progress: 0 / 0",
                        12
                );

        progress.setVisibility(
                View.GONE
        );


        page.addView(
                progress,
                new LinearLayout.LayoutParams(
                        1,
                        1
                )
        );


        return page;
    }


    // ============================================================
    // FINISHED PAGE
    // ============================================================

    private LinearLayout buildFinishedPage() {

        LinearLayout page =
                new LinearLayout(this);

        page.setOrientation(
                LinearLayout.VERTICAL
        );

        page.setPadding(
                20,
                12,
                20,
                16
        );


        TextView title =
                text(
                        "Finished",
                        24
                );

        title.setTypeface(
                null,
                Typeface.BOLD
        );


        page.addView(
                title,
                new LinearLayout.LayoutParams(
                        -1,
                        64
                )
        );


        ScrollView scroll =
                new ScrollView(this);

        scroll.setFillViewport(
                true
        );


        finishedList =
                new LinearLayout(this);

        finishedList.setOrientation(
                LinearLayout.VERTICAL
        );


        scroll.addView(
                finishedList,
                new ScrollView.LayoutParams(
                        -1,
                        -2
                )
        );


        page.addView(
                scroll,
                new LinearLayout.LayoutParams(
                        -1,
                        0,
                        1
                )
        );


        return page;
    }


    // ============================================================
    // ADD FINISHED VIDEO
    // ============================================================

    private void addFinishedItem(
            int index,
            String caption,
            long downloadId
    ) {

        if (finishedList == null) {
            return;
        }


        // --------------------------------------------------------
        // CARD
        // --------------------------------------------------------

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

        card.setBackground(
                round(
                        Color.WHITE,
                        18,
                        Color.rgb(
                                230,
                                230,
                                232
                        ),
                        1
                )
        );


        LinearLayout.LayoutParams
                cardParams =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );

        cardParams.setMargins(
                0,
                0,
                0,
                12
        );


        finishedList.addView(
                card,
                cardParams
        );


        // --------------------------------------------------------
        // HEADER
        // --------------------------------------------------------

        TextView head =
                text(
                        "✓  Video "
                                + String.format(
                                Locale.US,
                                "%03d",
                                index + 1
                        ),
                        16
                );


        head.setTypeface(
                null,
                Typeface.BOLD
        );


        card.addView(
                head
        );


        // --------------------------------------------------------
        // VIDEO PREVIEW
        // --------------------------------------------------------

        VideoView videoView =
                new VideoView(this);


        LinearLayout.LayoutParams
                videoParams =
                new LinearLayout.LayoutParams(
                        -1,
                        230
                );


        videoParams.setMargins(
                0,
                8,
                0,
                8
        );


        card.addView(
                videoView,
                videoParams
        );


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
                        new MediaController(this);


                controller.setAnchorView(
                        videoView
                );


                videoView.setMediaController(
                        controller
                );


                videoView.setOnPreparedListener(
                        mp -> {

                            mp.setLooping(
                                    true
                            );

                        }
                );

            } else {

                TextView error =
                        text(
                                "Preview video belum tersedia.",
                                12
                        );

                error.setTextColor(
                        Color.GRAY
                );

                card.addView(
                        error
                );
            }


        } catch (Exception e) {

            TextView error =
                    text(
                            "Preview tidak dapat dibuka.",
                            12
                    );

            error.setTextColor(
                    Color.GRAY
            );

            card.addView(
                    error
            );
        }


        // --------------------------------------------------------
        // CAPTION
        // --------------------------------------------------------

        TextView cap =
                text(
                        caption == null
                                || caption.isEmpty()
                                ? "Tanpa caption"
                                : caption,
                        13
                );


        cap.setMaxLines(
                4
        );


        card.addView(
                cap
        );


        // --------------------------------------------------------
        // FILE INFO
        // --------------------------------------------------------

        TextView files =
                text(
                        "video.mp4  •  caption.txt",
                        12
                );


        files.setTextColor(
                Color.GRAY
        );


        card.addView(
                files
        );


        // --------------------------------------------------------
        // LOCATION
        // --------------------------------------------------------

        TextView location =
                text(
                        "Download/TikTokDownloadManager/"
                                + String.format(
                                Locale.US,
                                "%03d",
                                index + 1
                        ),
                        11
                );


        location.setTextColor(
                Color.GRAY
        );


        card.addView(
                location
        );
    }


    // ============================================================
    // PAGE SWITCH
    // ============================================================

    private void showPage(
            int page
    ) {

        if (pageContainer == null) {
            return;
        }


        downloadPage.setVisibility(
                page == 0
                        ? View.VISIBLE
                        : View.GONE
        );


        progressPage.setVisibility(
                page == 1
                        ? View.VISIBLE
                        : View.GONE
        );


        finishedPage.setVisibility(
                page == 2
                        ? View.VISIBLE
                        : View.GONE
        );


        if (downloadNav != null) {

            downloadNav.setTextColor(
                    page == 0
                            ? Color.BLACK
                            : Color.LTGRAY
            );
        }


        if (progressNav != null) {

            progressNav.setTextColor(
                    page == 1
                            ? Color.BLACK
                            : Color.LTGRAY
            );
        }


        if (finishedNav != null) {

            finishedNav.setTextColor(
                    page == 2
                            ? Color.BLACK
                            : Color.LTGRAY
            );
        }
    }


    // ============================================================
    // WEBVIEW
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
                "Mozilla/5.0 (Linux; Android 12) "
                        + "AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) "
                        + "Chrome/140.0 Mobile Safari/537.36"
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


                        if (!processing
                                || currentIndex < 0) {

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


    // ============================================================
    // CHECK SAVETIKTOK
    // ============================================================

    private boolean isSaveTikTokPage(
            String url
    ) {

        try {

            String host =
                    Uri.parse(url).getHost();


            return host != null
                    && host.toLowerCase(
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


        String raw =
                input.getText()
                        .toString();


        for (
                String line :
                raw.split("\\r?\\n")
        ) {

            String url =
                    line.trim();


            if (
                    url.startsWith(
                            "http://"
                    )
                            || url.startsWith(
                            "https://"
                    )
            ) {

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


        finishedList.removeAllViews();


        for (
                int i = 0;
                i < urls.size();
                i++
        ) {

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


        if (urls.isEmpty()) {

            Toast.makeText(
                    this,
                    "Tidak ada link valid.",
                    Toast.LENGTH_SHORT
            ).show();

        } else {

            Toast.makeText(
                    this,
                    urls.size()
                            + " link masuk antrean.",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }


    // ============================================================
    // QUEUE ITEM
    // ============================================================

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


        card.setBackground(
                round(
                        Color.WHITE,
                        16,
                        Color.rgb(
                                230,
                                230,
                                232
                        ),
                        1
                )
        );


        LinearLayout.LayoutParams
                cardParams =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );


        cardParams.setMargins(
                0,
                0,
                0,
                10
        );


        queue.addView(
                card,
                cardParams
        );


        TextView status =
                text(
                        "#"
                                + (index + 1)
                                + "  Menunggu",
                        16
                );


        status.setTypeface(
                null,
                Typeface.BOLD
        );


        TextView link =
                text(
                        url,
                        12
                );


        link.setMaxLines(
                3
        );


        Button process =
                button(
                        "Proses Link Ini"
                );


        process.setOnClickListener(
                v -> startSingle(index)
        );


        card.addView(
                status
        );


        card.addView(
                link
        );


        card.addView(
                process
        );


        card.setTag(
                status
        );
    }


    // ============================================================
    // START SINGLE
    // ============================================================

    private void startSingle(
            int index
    ) {

        if (
                index < 0
                        || index >= urls.size()
        ) {

            return;
        }


        handler.removeCallbacksAndMessages(
                null
        );


        currentIndex =
                index;


        processing =
                true;


        submitClicked =
                false;


        downloadStarted =
                false;


        currentCaption =
                "";


        currentDownloadId =
                -1L;


        setStatus(
                index,
                "Memproses"
        );


        progress.setText(
                "Progress: "
                        + (index + 1)
                        + " / "
                        + urls.size()
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

            showPage(0);

            return;
        }


        if (!processing) {

            startSingle(0);
        }
    }


    // ============================================================
    // WAIT INPUT
    // ============================================================

    private void waitForSaveTikTokInput(
            String tiktokUrl,
            int attempt
    ) {

        if (
                !processing
                        || currentIndex < 0
                        || submitClicked
        ) {

            return;
        }


        if (
                attempt >= MAX_INPUT_CHECKS
        ) {

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
                "(function(){"
                        + "var fields=Array.from(document.querySelectorAll('input,textarea'));"
                        + "var field=fields.find(function(el){"
                        + "var p=((el.placeholder||'')+' '+"
                        + "(el.getAttribute('aria-label')||'')+' '+"
                        + "(el.name||'')+' '+"
                        + "(el.type||'')).toLowerCase();"
                        + "return p.includes('tautan')||"
                        + "p.includes('tiktok')||"
                        + "p.includes('link')||"
                        + "p.includes('url')||"
                        + "el.type==='url';"
                        + "});"
                        + "return field?'FOUND':'WAIT';"
                        + "})()";


        webView.evaluateJavascript(
                js,
                result -> {

                    if (
                            result != null
                                    && result.contains(
                                    "FOUND"
                            )
                    ) {

                        webStatus.setText(
                                "WebView: kolom URL ditemukan..."
                        );


                        handler.postDelayed(
                                () -> injectTikTokUrl(
                                        tiktokUrl
                                ),
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


    // ============================================================
    // INJECT URL
    // ============================================================

    private void injectTikTokUrl(
            String tiktokUrl
    ) {

        if (
                !processing
                        || currentIndex < 0
                        || submitClicked
        ) {

            return;
        }


        String safeUrl =
                JSONObject.quote(
                        tiktokUrl
                );


        String js =
                "(function(){"

                        + "var fields=Array.from("
                        + "document.querySelectorAll("
                        + "'input,textarea'));"

                        + "var field=fields.find(function(el){"

                        + "var p=((el.placeholder||'')+' '+"
                        + "(el.getAttribute('aria-label')||'')+' '+"
                        + "(el.name||'')+' '+"
                        + "(el.type||'')).toLowerCase();"

                        + "return p.includes('tautan')||"
                        + "p.includes('tiktok')||"
                        + "p.includes('link')||"
                        + "p.includes('url')||"
                        + "el.type==='url';"

                        + "});"

                        + "if(!field)return 'NO_FIELD';"

                        + "var proto=field instanceof HTMLTextAreaElement"
                        + "?HTMLTextAreaElement.prototype"
                        + ":HTMLInputElement.prototype;"

                        + "var desc="
                        + "Object.getOwnPropertyDescriptor("
                        + "proto,'value');"

                        + "if(desc&&desc.set){"
                        + "desc.set.call(field,"
                        + safeUrl
                        + ");"
                        + "}else{"
                        + "field.value="
                        + safeUrl
                        + ";"
                        + "}"

                        + "field.dispatchEvent("
                        + "new Event('input',{bubbles:true})"
                        + ");"

                        + "field.dispatchEvent("
                        + "new Event('change',{bubbles:true})"
                        + ");"

                        + "field.focus();"

                        + "if(field.value!=="
                        + safeUrl
                        + ")return 'VALUE_FAILED';"

                        + "var buttons="
                        + "Array.from(document.querySelectorAll("
                        + "'button,input[type=submit],"
                        + "input[type=button],a'));"

                        + "var downloadButton="
                        + "buttons.find(function(el){"

                        + "var t=(el.innerText||"
                        + "el.textContent||"
                        + "el.value||"
                        + "el.getAttribute('aria-label')||'')"
                        + ".replace(/\\s+/g,' ')"
                        + ".trim()"
                        + ".toLowerCase();"

                        + "return t==='unduh'||t==='download';"

                        + "});"

                        + "if(!downloadButton)"
                        + "return 'NO_DOWNLOAD_BUTTON';"

                        + "downloadButton.click();"

                        + "return 'OK';"

                        + "})()";


        webView.evaluateJavascript(
                js,
                result -> {

                    if (
                            result != null
                                    && result.contains("OK")
                    ) {

                        submitClicked =
                                true;


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

                        submitClicked =
                                false;


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


    // ============================================================
    // FIND MP4 HD
    // ============================================================

    private void findMp4HdButton(
            int attempt
    ) {

        if (
                !processing
                        || currentIndex < 0
                        || downloadStarted
        ) {

            return;
        }


        if (
                attempt >= MAX_MP4_HD_CHECKS
        ) {

            webStatus.setText(
                    "WebView: tombol MP4 HD tidak ditemukan"
            );


            setStatus(
                    currentIndex,
                    "MP4 HD tidak ditemukan"
            );


            processing =
                    false;


            return;
        }


        /*
         * Selector berdasarkan struktur DOM SaveTikTok
         * yang sudah ditemukan sebelumnya.
         *
         * #download-result
         *   .video-data
         *     .tik-video
         *
         * Caption:
         * .tik-left .thumbnail .content .clearfix h3
         *
         * MP4 HD:
         * .dl-action a.tik-button-dl
         */

        String js =
                "(function(){"

                        + "var root="
                        + "document.querySelector("
                        + "'#download-result');"

                        + "if(!root)"
                        + "return JSON.stringify({state:'WAIT'});"

                        + "var card="
                        + "root.querySelector("
                        + "'.video-data .tik-video');"

                        + "if(!card)"
                        + "return JSON.stringify({state:'WAIT'});"

                        + "var captionEl="
                        + "card.querySelector("
                        + "'.tik-left .thumbnail "
                        + ".content .clearfix h3');"

                        + "var buttons="
                        + "Array.from("
                        + "card.querySelectorAll("
                        + "'.dl-action a.tik-button-dl'"
                        + "));"

                        + "var hd="
                        + "buttons.find(function(el){"

                        + "var t=(el.innerText||"
                        + "el.textContent||'')"
                        + ".replace(/\\s+/g,' ')"
                        + ".trim()"
                        + ".toLowerCase();"

                        + "return t==='unduh mp4 hd'"
                        + "||t==='download mp4 hd';"

                        + "});"

                        + "if(!hd)"
                        + "return JSON.stringify({state:'WAIT'});"

                        + "var caption="
                        + "captionEl?"
                        + "(captionEl.innerText||"
                        + "captionEl.textContent||'')"
                        + ".trim():'';"

                        + "return JSON.stringify({"
                        + "state:'FOUND',"
                        + "caption:caption,"
                        + "hasCaption:!!captionEl"
                        + "});"

                        + "})()";


        webView.evaluateJavascript(
                js,
                result -> {

                    if (
                            result == null
                                    || !result.contains(
                                    "\\\"state\\\":\\\"FOUND\\\""
                            )
                    ) {

                        webStatus.setText(
                                "WebView: menunggu caption dan MP4 HD..."
                        );


                        handler.postDelayed(
                                () -> findMp4HdButton(
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


                        // ------------------------------------------------
                        // CAPTION HARUS ADA
                        // ------------------------------------------------

                        if (
                                currentCaption.isEmpty()
                        ) {

                            webStatus.setText(
                                    "WebView: caption kosong, MP4 HD tidak diproses"
                            );


                            setStatus(
                                    currentIndex,
                                    "Caption kosong"
                            );


                            processing =
                                    false;


                            return;
                        }


                        webStatus.setText(
                                "WebView: caption ditemukan"
                        );


                        setStatus(
                                currentIndex,
                                "Caption ditemukan"
                        );


                        // ------------------------------------------------
                        // CLICK MP4 HD
                        // ------------------------------------------------

                        String clickJs =
                                "(function(){"

                                        + "var card="
                                        + "document.querySelector("
                                        + "'#download-result "
                                        + ".video-data "
                                        + ".tik-video');"

                                        + "if(!card)return 'WAIT';"

                                        + "var buttons="
                                        + "Array.from("
                                        + "card.querySelectorAll("
                                        + "'.dl-action "
                                        + "a.tik-button-dl'"
                                        + "));"

                                        + "var hd="
                                        + "buttons.find(function(el){"

                                        + "var t=(el.innerText||"
                                        + "el.textContent||'')"
                                        + ".replace(/\\s+/g,' ')"
                                        + ".trim()"
                                        + ".toLowerCase();"

                                        + "return t==="
                                        + "'unduh mp4 hd'"
                                        + "||t==="
                                        + "'download mp4 hd';"

                                        + "});"

                                        + "if(!hd)"
                                        + "return 'WAIT';"

                                        + "hd.click();"

                                        + "return 'CLICKED';"

                                        + "})()";


                        webView.evaluateJavascript(
                                clickJs,
                                clickResult -> {

                                    if (
                                            clickResult != null
                                                    && clickResult.contains(
                                                    "CLICKED"
                                            )
                                    ) {

                                        submitClicked =
                                                true;


                                        webStatus.setText(
                                                "WebView: MP4 HD dipilih, menunggu download..."
                                        );


                                        setStatus(
                                                currentIndex,
                                                "MP4 HD dipilih"
                                        );

                                    } else {

                                        webStatus.setText(
                                                "WebView: tombol MP4 HD gagal diklik"
                                        );


                                        setStatus(
                                                currentIndex,
                                                "Gagal klik MP4 HD"
                                        );


                                        processing =
                                                false;
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


                        processing =
                                false;
                    }
                }
        );
    }


    // ============================================================
    // WEB DOWNLOAD EVENT
    // ============================================================

    private void handleWebDownload(
            String url,
            String userAgent,
            String contentDisposition,
            String mimeType,
            long contentLength
    ) {

        if (
                !processing
                        || currentIndex < 0
                        || downloadStarted
        ) {

            return;
        }


        if (
                url == null
                        || url.trim().isEmpty()
        ) {

            return;
        }


        String normalizedUrl =
                url.trim();


        if (
                handledDownloadUrls.contains(
                        normalizedUrl
                )
        ) {

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


        // --------------------------------------------------------
        // HTML BUKAN VIDEO
        // --------------------------------------------------------

        boolean looksLikeHtml =
                lowerMime.contains(
                        "text/html"
                )
                        || lowerMime.contains(
                        "application/xhtml"
                );


        if (looksLikeHtml) {

            webStatus.setText(
                    "WebView: download ditolak karena bukan video"
            );


            return;
        }


        // --------------------------------------------------------
        // .BIN / OCTET STREAM
        // --------------------------------------------------------

        boolean isBin =
                lowerMime.contains(
                        "application/octet-stream"
                )
                        || lowerDisposition.contains(
                        ".bin"
                )
                        || lowerUrl.matches(
                        ".*\\.bin(?:[?#].*)?$"
                );


        // --------------------------------------------------------
        // VIDEO
        // --------------------------------------------------------

        boolean looksLikeVideo =
                lowerMime.startsWith(
                        "video/"
                )
                        || lowerMime.contains(
                        "mp4"
                )
                        || lowerDisposition.contains(
                        ".mp4"
                )
                        || lowerUrl.contains(
                        ".mp4"
                );


        /*
         * SaveTikTok pada beberapa MP4 HD dapat
         * mengirim video sebagai application/octet-stream
         * atau .bin.
         *
         * Karena event ini terjadi setelah MP4 HD
         * diklik, kandidat tersebut diterima.
         */

        if (
                isBin
                        && submitClicked
        ) {

            looksLikeVideo =
                    true;
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


        if (
                handledDownloadUrls.size() > 20
        ) {

            String first =
                    handledDownloadUrls
                            .iterator()
                            .next();


            handledDownloadUrls.remove(
                    first
            );
        }


        downloadStarted =
                true;


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


    // ============================================================
    // DOWNLOAD MANAGER
    // ============================================================

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
                    "TikTok "
                            + (currentIndex + 1)
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


            if (
                    userAgent != null
                            && !userAgent.isEmpty()
            ) {

                request.addRequestHeader(
                        "User-Agent",
                        userAgent
                );
            }


            String cookie =
                    CookieManager
                            .getInstance()
                            .getCookie(url);


            if (
                    cookie != null
                            && !cookie.isEmpty()
            ) {

                request.addRequestHeader(
                        "Cookie",
                        cookie
                );
            }


            request.addRequestHeader(
                    "Referer",
                    SAVE_URL
            );


            // ----------------------------------------------------
            // FOLDER
            // ----------------------------------------------------

            String folder =
                    "TikTokDownloadManager/"
                            + String.format(
                            Locale.US,
                            "%03d",
                            currentIndex + 1
                    );


            String filename =
                    "video.mp4";


            request.setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    folder + "/" + filename
            );


            // ----------------------------------------------------
            // ENQUEUE
            // ----------------------------------------------------

            currentDownloadId =
                    manager.enqueue(
                            request
                    );


            int completedIndex =
                    currentIndex;


            setStatus(
                    completedIndex,
                    "Download dimulai"
            );


            webStatus.setText(
                    "WebView: download dimulai → Download/"
                            + folder
            );


            progress.setText(
                    "Progress: "
                            + (completedIndex + 1)
                            + " / "
                            + urls.size()
            );


            // ----------------------------------------------------
            // SAVE CAPTION
            // ----------------------------------------------------

            saveCaptionFile(
                    completedIndex
            );


            Toast.makeText(
                    this,
                    "Download dimulai: "
                            + folder
                            + "/video.mp4",
                    Toast.LENGTH_SHORT
            ).show();


            // ----------------------------------------------------
            // WAIT DOWNLOAD
            // ----------------------------------------------------

            waitForDownloadCompletion(
                    manager,
                    currentDownloadId,
                    completedIndex,
                    0
            );


        } catch (Exception e) {

            downloadStarted =
                    false;


            processing =
                    false;


            setStatus(
                    currentIndex,
                    "Gagal memulai download"
            );


            webStatus.setText(
                    "WebView: gagal download - "
                            + e.getMessage()
            );
        }
    }


    // ============================================================
    // SAVE CAPTION
    // ============================================================

    private void saveCaptionFile(
            int index
    ) {

        try {

            String caption =
                    currentCaption == null
                            ? ""
                            : currentCaption.trim();


            String folder =
                    Environment.DIRECTORY_DOWNLOADS
                            + "/TikTokDownloadManager/"
                            + String.format(
                            Locale.US,
                            "%03d",
                            index + 1
                    );


            if (
                    Build.VERSION.SDK_INT
                            >= Build.VERSION_CODES.Q
            ) {

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
                        getContentResolver()
                                .insert(
                                        android.provider.MediaStore.Downloads
                                                .EXTERNAL_CONTENT_URI,
                                        values
                                );


                if (uri != null) {

                    try (
                            OutputStream out =
                                    getContentResolver()
                                            .openOutputStream(uri)
                    ) {

                        if (out != null) {

                            out.write(
                                    (
                                            caption.isEmpty()
                                                    ? "Caption tidak ditemukan."
                                                    : caption
                                    ).getBytes(
                                            StandardCharsets.UTF_8
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


                    getContentResolver()
                            .update(
                                    uri,
                                    values,
                                    null,
                                    null
                            );
                }


            } else {

                File dir =
                        new File(
                                Environment
                                        .getExternalStoragePublicDirectory(
                                                Environment.DIRECTORY_DOWNLOADS
                                        ),
                                "TikTokDownloadManager/"
                                        + String.format(
                                        Locale.US,
                                        "%03d",
                                        index + 1
                                )
                        );


                if (!dir.exists()) {

                    dir.mkdirs();
                }


                File file =
                        new File(
                                dir,
                                "caption.txt"
                        );


                try (
                        FileOutputStream out =
                                new FileOutputStream(
                                        file
                                )
                ) {

                    out.write(
                            (
                                    caption.isEmpty()
                                            ? "Caption tidak ditemukan."
                                            : caption
                            ).getBytes(
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


    // ============================================================
    // WAIT DOWNLOAD COMPLETION
    // ============================================================

    private void waitForDownloadCompletion(
            DownloadManager manager,
            long downloadId,
            int completedIndex,
            int attempt
    ) {

        if (
                attempt >= MAX_DOWNLOAD_CHECKS
        ) {

            /*
             * Jangan menganggap selesai jika DownloadManager
             * belum STATUS_SUCCESSFUL.
             */

            setStatus(
                    completedIndex,
                    "Download masih berjalan"
            );


            webStatus.setText(
                    "WebView: download masih berjalan"
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

            if (
                    cursor != null
                            && cursor.moveToFirst()
            ) {

                int status =
                        cursor.getInt(
                                cursor.getColumnIndexOrThrow(
                                        DownloadManager.COLUMN_STATUS
                                )
                        );


                // ------------------------------------------------
                // SUCCESS
                // ------------------------------------------------

                if (
                        status
                                == DownloadManager.STATUS_SUCCESSFUL
                ) {

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


                // ------------------------------------------------
                // FAILED
                // ------------------------------------------------

                if (
                        status
                                == DownloadManager.STATUS_FAILED
                ) {

                    int reason =
                            cursor.getInt(
                                    cursor.getColumnIndexOrThrow(
                                            DownloadManager.COLUMN_REASON
                                    )
                            );


                    downloadStarted =
                            false;


                    processing =
                            false;


                    setStatus(
                            completedIndex,
                            "Gagal download (" + reason + ")"
                    );


                    webStatus.setText(
                            "WebView: download gagal"
                    );


                    return;
                }


                // ------------------------------------------------
                // PROGRESS
                // ------------------------------------------------

                long downloaded =
                        cursor.getLong(
                                cursor.getColumnIndexOrThrow(
                                        DownloadManager
                                                .COLUMN_BYTES_DOWNLOADED_SO_FAR
                                )
                        );


                long total =
                        cursor.getLong(
                                cursor.getColumnIndexOrThrow(
                                        DownloadManager
                                                .COLUMN_TOTAL_SIZE_BYTES
                                )
                        );


                if (
                        total > 0
                ) {

                    int percent =
                            (int) Math.max(
                                    0,
                                    Math.min(
                                            100,
                                            (
                                                    downloaded
                                                            * 100L
                                            ) / total
                                    )
                            );


                    setStatus(
                            completedIndex,
                            "Download "
                                    + percent
                                    + "%"
                    );


                    webStatus.setText(
                            "WebView: download "
                                    + percent
                                    + "%"
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
                DOWNLOAD_CHECK_INTERVAL
        );
    }


    // ============================================================
    // FINISH CURRENT / NEXT
    // ============================================================

    private void finishCurrentAndNext(
            int completedIndex
    ) {

        processing =
                false;


        submitClicked =
                false;


        downloadStarted =
                false;


        long finishedDownloadId =
                currentDownloadId;


        currentDownloadId =
                -1L;


        // --------------------------------------------------------
        // FINISHED CARD
        // --------------------------------------------------------

        if (
                completedIndex >= 0
                        && completedIndex < urls.size()
        ) {

            addFinishedItem(
                    completedIndex,
                    currentCaption,
                    finishedDownloadId
            );
        }


        currentCaption =
                "";


        int next =
                completedIndex + 1;


        // --------------------------------------------------------
        // NEXT
        // --------------------------------------------------------

        if (
                next < urls.size()
        ) {

            startSingle(
                    next
            );


        } else {

            currentIndex =
                    -1;


            webStatus.setText(
                    "WebView: semua antrean selesai"
            );


            progress.setText(
                    "Progress: "
                            + urls.size()
                            + " / "
                            + urls.size()
            );


            Toast.makeText(
                    this,
                    "Semua antrean sudah diproses.",
                    Toast.LENGTH_LONG
            ).show();


            // otomatis tampilkan Finished
            showPage(2);
        }
    }


    // ============================================================
    // CLEAR QUEUE
    // ============================================================

    private void clearQueue() {

        handler.removeCallbacksAndMessages(
                null
        );


        urls.clear();


        currentIndex =
                -1;


        processing =
                false;


        submitClicked =
                false;


        downloadStarted =
                false;


        currentCaption =
                "";


        currentDownloadId =
                -1L;


        handledDownloadUrls.clear();


        if (queue != null) {

            queue.removeAllViews();
        }


        if (finishedList != null) {

            finishedList.removeAllViews();
        }


        if (input != null) {

            input.setText("");
        }


        progress.setText(
                "Progress: 0 / 0"
        );


        webStatus.setText(
                "WebView: siap"
        );


        if (webView != null) {

            webView.stopLoading();

            webView.setVisibility(
                    View.GONE
            );
        }


        showPage(0);
    }


    // ============================================================
    // STATUS QUEUE
    // ============================================================

    private void setStatus(
            int index,
            String value
    ) {

        if (
                index < 0
                        || index >= queue.getChildCount()
        ) {

            return;
        }


        View card =
                queue.getChildAt(
                        index
                );


        Object tag =
                card.getTag();


        if (
                tag instanceof TextView
        ) {

            TextView status =
                    (TextView) tag;


            status.setText(
                    "#"
                            + (index + 1)
                            + "  "
                            + value
            );
        }
    }


    // ============================================================
    // UPDATE PROGRESS
    // ============================================================

    private void updateProgress() {

        progress.setText(
                "Progress: 0 / "
                        + urls.size()
        );
    }


    // ============================================================
    // UI HELPERS
    // ============================================================

    private TextView text(
            String value,
            float size
    ) {

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
                        17,
                        24,
                        39
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


    private Button button(
            String value
    ) {

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


    private Button navButton(
            String icon,
            String label
    ) {

        Button b =
                new Button(this);


        b.setText(
                icon
                        + "\n"
                        + label
        );


        b.setTextSize(
                11
        );


        b.setAllCaps(
                false
        );


        b.setGravity(
                Gravity.CENTER
        );


        b.setPadding(
                0,
                0,
                0,
                0
        );


        b.setBackgroundColor(
                Color.TRANSPARENT
        );


        return b;
    }


    private LinearLayout roundedBox(
            int fill,
            int stroke,
            int radius,
            int strokeWidth
    ) {

        LinearLayout box =
                new LinearLayout(this);


        box.setPadding(
                8,
                0,
                8,
                0
        );


        box.setBackground(
                round(
                        fill,
                        radius,
                        stroke,
                        strokeWidth
                )
        );


        return box;
    }


    private android.graphics.drawable.GradientDrawable
    round(
            int fill,
            int radius
    ) {

        return round(
                fill,
                radius,
                Color.TRANSPARENT,
                0
        );
    }


    private android.graphics.drawable.GradientDrawable
    round(
            int fill,
            int radius,
            int stroke,
            int strokeWidth
    ) {

        android.graphics.drawable.GradientDrawable d =
                new android.graphics.drawable.GradientDrawable();


        d.setColor(
                fill
        );


        d.setCornerRadius(
                radius
        );


        if (
                strokeWidth > 0
        ) {

            d.setStroke(
                    strokeWidth,
                    stroke
            );
        }


        return d;
    }


    private FrameLayout.LayoutParams
    matchParams() {

        return new FrameLayout.LayoutParams(
                -1,
                -1
        );
    }


    private LinearLayout.LayoutParams
    marginParams(
            int left,
            int top,
            int right,
            int bottom
    ) {

        LinearLayout.LayoutParams p =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );


        p.setMargins(
                left,
                top,
                right,
                bottom
        );


        return p;
    }


    // ============================================================
    // STATUS BAR HEIGHT
    // ============================================================

    private int getStatusBarHeight() {

        int resourceId =
                getResources()
                        .getIdentifier(
                                "status_bar_height",
                                "dimen",
                                "android"
                        );


        if (
                resourceId > 0
        ) {

            return getResources()
                    .getDimensionPixelSize(
                            resourceId
                    );
        }


        return 24;
    }


    // ============================================================
    // STORAGE PERMISSION
    // ============================================================

    private void requestStorageIfNeeded() {

        if (
                Build.VERSION.SDK_INT >= 23
                        && Build.VERSION.SDK_INT <= 28
                        && checkSelfPermission(
                        Manifest.permission
                                .WRITE_EXTERNAL_STORAGE
                )
                        != PackageManager.PERMISSION_GRANTED
        ) {

            requestPermissions(
                    new String[]{
                            Manifest.permission
                                    .WRITE_EXTERNAL_STORAGE
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

        if (
                webView != null
                        && webView.getVisibility()
                        == View.VISIBLE
                        && webView.canGoBack()
        ) {

            webView.goBack();

        } else {

            super.onBackPressed();
        }
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
        }


        super.onDestroy();
    }
}
