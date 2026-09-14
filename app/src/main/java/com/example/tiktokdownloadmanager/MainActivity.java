package com.example.tiktokdownloadmanager;

import android.Manifest;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.webkit.URLUtil;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends android.app.Activity {

    // ============================================================
    // WEBVIEW / ENGINE
    // ============================================================

    private WebView webView;
    private Handler handler = new Handler();

    private static final String SAVE_TIKTOK_URL = "https://savetiktok.to/id";

    private EditText input;

    private boolean submitClicked = false;
    private boolean mp4HdClicked = false;
    private boolean waitingForDownload = false;

    private String currentCaption = "";
    private String currentTikTokUrl = "";

    private long currentDownloadId = -1;

    // ============================================================
    // QUEUE
    // ============================================================

    private final ArrayList<String> urlQueue = new ArrayList<>();
    private int currentIndex = 0;

    private LinearLayout queue;
    private LinearLayout finishedList;

    private TextView emptyProgressText;
    private TextView emptyFinishedText;

    private Button startAllButton;

    // ============================================================
    // UI PAGES
    // ============================================================

    private FrameLayout pageContainer;

    private View tabPage;
    private View progressPage;
    private View finishedPage;

    private TextView navTab;
    private TextView navProgress;
    private TextView navFinished;

    private int currentPage = 0;

    // ============================================================
    // DOWNLOAD MANAGER
    // ============================================================

    private DownloadManager downloadManager;

    private final BroadcastReceiver downloadReceiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {

                    if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(
                            intent.getAction())) {

                        long id = intent.getLongExtra(
                                DownloadManager.EXTRA_DOWNLOAD_ID,
                                -1
                        );

                        if (id == currentDownloadId) {

                            currentDownloadId = -1;
                            waitingForDownload = false;

                            waitForDownloadCompletion();
                        }
                    }
                }
            };

    // ============================================================
    // ON CREATE
    // ============================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        downloadManager =
                (DownloadManager) getSystemService(
                        DOWNLOAD_SERVICE
                );

        createHiddenWebView();

        buildUi();

        loadSaveTikTok();

        registerDownloadReceiver();
    }

    // ============================================================
    // HIDDEN WEBVIEW
    // ============================================================

    private void createHiddenWebView() {

        webView = new WebView(this);

        WebSettings settings = webView.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);

        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(false);

        settings.setUserAgentString(
                "Mozilla/5.0 (Linux; Android 12; Mobile) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/120.0 Mobile Safari/537.36"
        );

        CookieManager.getInstance()
                .setAcceptCookie(true);

        CookieManager.getInstance()
                .setAcceptThirdPartyCookies(
                        webView,
                        true
                );

        webView.setWebViewClient(
                new WebViewClient() {

                    @Override
                    public void onPageFinished(
                            WebView view,
                            String url
                    ) {
                        super.onPageFinished(view, url);

                        handler.postDelayed(
                                new Runnable() {
                                    @Override
                                    public void run() {

                                        if (!urlQueue.isEmpty()
                                                && currentIndex < urlQueue.size()) {

                                            injectTikTokUrl();
                                        }
                                    }
                                },
                                1200
                        );
                    }
                }
        );

        webView.setWebChromeClient(
                new WebChromeClient()
        );

        webView.setDownloadListener(
                new DownloadListener() {

                    @Override
                    public void onDownloadStart(
                            String url,
                            String userAgent,
                            String contentDisposition,
                            String mimetype,
                            long contentLength
                    ) {

                        handleWebDownload(
                                url,
                                userAgent,
                                contentDisposition,
                                mimetype
                        );
                    }
                }
        );

        // WebView sengaja tidak ditampilkan.
        FrameLayout hiddenContainer =
                new FrameLayout(this);

        hiddenContainer.addView(
                webView,
                new FrameLayout.LayoutParams(
                        1,
                        1
                )
        );

        addContentView(
                hiddenContainer,
                new ViewGroup.LayoutParams(
                        1,
                        1
                )
        );
    }

    // ============================================================
    // LOAD SAVE TIKTOK
    // ============================================================

    private void loadSaveTikTok() {

        submitClicked = false;
        mp4HdClicked = false;
        waitingForDownload = false;

        webView.loadUrl(
                SAVE_TIKTOK_URL
        );
    }

    // ============================================================
    // MAIN UI
    // ============================================================

    private void buildUi() {

        LinearLayout root =
                new LinearLayout(this);

        root.setOrientation(
                LinearLayout.VERTICAL
        );

        root.setBackgroundColor(
                Color.rgb(248, 249, 251)
        );

        // --------------------------------------------------------
        // PAGE CONTAINER
        // --------------------------------------------------------

        pageContainer =
                new FrameLayout(this);

        LinearLayout.LayoutParams
                pageParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1
                );

        root.addView(
                pageContainer,
                pageParams
        );

        // --------------------------------------------------------
        // CREATE PAGES
        // --------------------------------------------------------

        tabPage =
                buildTabPage();

        progressPage =
                buildProgressPage();

        finishedPage =
                buildFinishedPage();

        // Hanya satu page yang ditampilkan.
        pageContainer.addView(
                tabPage
        );

        // --------------------------------------------------------
        // BOTTOM NAVIGATION
        // --------------------------------------------------------

        LinearLayout bottomNav =
                new LinearLayout(this);

        bottomNav.setOrientation(
                LinearLayout.HORIZONTAL
        );

        bottomNav.setGravity(
                Gravity.CENTER
        );

        bottomNav.setPadding(
                8,
                8,
                8,
                8
        );

        bottomNav.setBackgroundColor(
                Color.WHITE
        );

        navTab =
                createNavItem(
                        "Download",
                        "↓"
                );

        navProgress =
                createNavItem(
                        "Progress",
                        "◷"
                );

        navFinished =
                createNavItem(
                        "Finished",
                        "✓"
                );

        bottomNav.addView(
                navTab,
                new LinearLayout.LayoutParams(
                        0,
                        70,
                        1
                )
        );

        bottomNav.addView(
                navProgress,
                new LinearLayout.LayoutParams(
                        0,
                        70,
                        1
                )
        );

        bottomNav.addView(
                navFinished,
                new LinearLayout.LayoutParams(
                        0,
                        70,
                        1
                )
        );

        navTab.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showPage(0);
                    }
                }
        );

        navProgress.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showPage(1);
                    }
                }
        );

        navFinished.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showPage(2);
                    }
                }
        );

        root.addView(
                bottomNav,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        80
                )
        );

        setContentView(root);

        updateNavigation();
    }

    // ============================================================
    // NAV ITEM
    // ============================================================

    private TextView createNavItem(
            String title,
            String icon
    ) {

        TextView text =
                new TextView(this);

        text.setText(
                icon + "\n" + title
        );

        text.setTextSize(
                12
        );

        text.setGravity(
                Gravity.CENTER
        );

        text.setTextColor(
                Color.DKGRAY
        );

        return text;
    }

    // ============================================================
    // SHOW PAGE
    // ============================================================

    private void showPage(int page) {

        currentPage = page;

        pageContainer.removeAllViews();

        if (page == 0) {

            pageContainer.addView(
                    tabPage
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
    // UPDATE NAVIGATION
    // ============================================================

    private void updateNavigation() {

        if (navTab == null) {
            return;
        }

        navTab.setTextColor(
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
    // TAB PAGE
    // ============================================================

    private View buildTabPage() {

        ScrollView scroll =
                new ScrollView(this);

        LinearLayout content =
                new LinearLayout(this);

        content.setOrientation(
                LinearLayout.VERTICAL
        );

        content.setPadding(
                20,
                24,
                20,
                24
        );

        // --------------------------------------------------------
        // TITLE
        // --------------------------------------------------------

        TextView title =
                new TextView(this);

        title.setText(
                "TikTok Download Manager"
        );

        title.setTextSize(
                25
        );

        title.setTextColor(
                Color.rgb(25, 25, 25)
        );

        title.setTypeface(
                null,
                android.graphics.Typeface.BOLD
        );

        content.addView(
                title
        );

        // --------------------------------------------------------
        // SUBTITLE
        // --------------------------------------------------------

        TextView subtitle =
                new TextView(this);

        subtitle.setText(
                "Download video TikTok dengan cepat dan terorganisir."
        );

        subtitle.setTextSize(
                14
        );

        subtitle.setTextColor(
                Color.GRAY
        );

        LinearLayout.LayoutParams
                subtitleParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        subtitleParams.setMargins(
                0,
                6,
                0,
                22
        );

        content.addView(
                subtitle,
                subtitleParams
        );

        // --------------------------------------------------------
        // URL CARD
        // --------------------------------------------------------

        LinearLayout urlCard =
                createCard();

        TextView urlTitle =
                new TextView(this);

        urlTitle.setText(
                "TikTok URL"
        );

        urlTitle.setTextSize(
                15
        );

        urlTitle.setTypeface(
                null,
                android.graphics.Typeface.BOLD
        );

        urlTitle.setTextColor(
                Color.DKGRAY
        );

        urlCard.addView(
                urlTitle
        );

        // --------------------------------------------------------
        // INPUT
        // --------------------------------------------------------

        EditText singleUrl =
                new EditText(this);

        singleUrl.setHint(
                "Paste URL TikTok di sini"
        );

        singleUrl.setTextSize(
                14
        );

        singleUrl.setSingleLine(
                true
        );

        singleUrl.setPadding(
                16,
                12,
                16,
                12
        );

        input = singleUrl;

        LinearLayout.LayoutParams
                inputParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        inputParams.setMargins(
                0,
                12,
                0,
                12
        );

        urlCard.addView(
                singleUrl,
                inputParams
        );

        // --------------------------------------------------------
        // ADD BUTTON
        // --------------------------------------------------------

        Button addButton =
                new Button(this);

        addButton.setText(
                "Tambah ke Antrian"
        );

        addButton.setAllCaps(
                false
        );

        addButton.setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {

                        String url =
                                singleUrl
                                        .getText()
                                        .toString()
                                        .trim();

                        if (url.isEmpty()) {
                            return;
                        }

                        addUrlToQueue(
                                url
                        );

                        singleUrl.setText("");

                        showPage(1);
                    }
                }
        );

        urlCard.addView(
                addButton
        );

        content.addView(
                urlCard
        );

        // --------------------------------------------------------
        // PLATFORM CARD
        // --------------------------------------------------------

        LinearLayout platformCard =
                createCard();

        TextView platformTitle =
                new TextView(this);

        platformTitle.setText(
                "Supported Platform"
        );

        platformTitle.setTextSize(
                15
        );

        platformTitle.setTypeface(
                null,
                android.graphics.Typeface.BOLD
        );

        platformTitle.setTextColor(
                Color.DKGRAY
        );

        platformCard.addView(
                platformTitle
        );

        LinearLayout platformRow =
                new LinearLayout(this);

        platformRow.setOrientation(
                LinearLayout.HORIZONTAL
        );

        platformRow.setPadding(
                0,
                15,
                0,
                5
        );

        TextView tiktok =
                createPlatformItem(
                        "TikTok",
                        "♪"
                );

        TextView publicOnly =
                createPlatformItem(
                        "Public Video",
                        "✓"
                );

        platformRow.addView(
                tiktok,
                new LinearLayout.LayoutParams(
                        0,
                        70,
                        1
                )
        );

        platformRow.addView(
                publicOnly,
                new LinearLayout.LayoutParams(
                        0,
                        70,
                        1
                )
        );

        platformCard.addView(
                platformRow
        );

        content.addView(
                platformCard
        );

        // --------------------------------------------------------
        // HOW TO DOWNLOAD
        // --------------------------------------------------------

        LinearLayout howCard =
                createCard();

        TextView howTitle =
                new TextView(this);

        howTitle.setText(
                "How to Download?"
        );

        howTitle.setTextSize(
                17
        );

        howTitle.setTypeface(
                null,
                android.graphics.Typeface.BOLD
        );

        howTitle.setTextColor(
                Color.DKGRAY
        );

        howCard.addView(
                howTitle
        );

        addStep(
                howCard,
                "1",
                "Paste link TikTok"
        );

        addStep(
                howCard,
                "2",
                "Tambahkan ke antrian"
        );

        addStep(
                howCard,
                "3",
                "Tekan Mulai Semua"
        );

        addStep(
                howCard,
                "4",
                "Video dan caption tersimpan otomatis"
        );

        content.addView(
                howCard
        );

        scroll.addView(
                content
        );

        return scroll;
    }

    // ============================================================
    // CREATE CARD
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
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
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
    // PLATFORM ITEM
    // ============================================================

    private TextView createPlatformItem(
            String title,
            String icon
    ) {

        TextView item =
                new TextView(this);

        item.setText(
                icon + "  " + title
        );

        item.setTextSize(
                14
        );

        item.setGravity(
                Gravity.CENTER_VERTICAL
        );

        item.setTextColor(
                Color.DKGRAY
        );

        return item;
    }

    // ============================================================
    // HOW TO STEP
    // ============================================================

    private void addStep(
            LinearLayout parent,
            String number,
            String text
    ) {

        TextView step =
                new TextView(this);

        step.setText(
                number + "    " + text
        );

        step.setTextSize(
                14
        );

        step.setTextColor(
                Color.DKGRAY
        );

        step.setPadding(
                4,
                12,
                4,
                12
        );

        parent.addView(
                step
        );
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
                24,
                20,
                24
        );

        TextView title =
                new TextView(this);

        title.setText(
                "Progress"
        );

        title.setTextSize(
                25
        );

        title.setTypeface(
                null,
                android.graphics.Typeface.BOLD
        );

        title.setTextColor(
                Color.rgb(25, 25, 25)
        );

        content.addView(
                title
        );

        TextView subtitle =
                new TextView(this);

        subtitle.setText(
                "Daftar video yang sedang diproses."
        );

        subtitle.setTextSize(
                14
        );

        subtitle.setTextColor(
                Color.GRAY
        );

        LinearLayout.LayoutParams
                subtitleParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        subtitleParams.setMargins(
                0,
                6,
                0,
                18
        );

        content.addView(
                subtitle,
                subtitleParams
        );

        // --------------------------------------------------------
        // START ALL BUTTON
        // --------------------------------------------------------

        startAllButton =
                new Button(this);

        startAllButton.setText(
                "Mulai Semua"
        );

        startAllButton.setAllCaps(
                false
        );

        startAllButton.setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {

                        if (urlQueue.isEmpty()) {
                            return;
                        }

                        currentIndex = 0;

                        startCurrentDownload();
                    }
                }
        );

        content.addView(
                startAllButton
        );

        // --------------------------------------------------------
        // QUEUE CONTAINER
        // --------------------------------------------------------

        queue =
                new LinearLayout(this);

        queue.setOrientation(
                LinearLayout.VERTICAL
        );

        LinearLayout.LayoutParams
                queueParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        queueParams.setMargins(
                0,
                18,
                0,
                0
        );

        content.addView(
                queue,
                queueParams
        );

        emptyProgressText =
                new TextView(this);

        emptyProgressText.setText(
                "Belum ada video dalam antrian."
        );

        emptyProgressText.setTextSize(
                14
        );

        emptyProgressText.setTextColor(
                Color.GRAY
        );

        emptyProgressText.setGravity(
                Gravity.CENTER
        );

        emptyProgressText.setPadding(
                0,
                40,
                0,
                40
        );

        content.addView(
                emptyProgressText
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
                24,
                20,
                24
        );

        TextView title =
                new TextView(this);

        title.setText(
                "Finished"
        );

        title.setTextSize(
                25
        );

        title.setTypeface(
                null,
                android.graphics.Typeface.BOLD
        );

        title.setTextColor(
                Color.rgb(25, 25, 25)
        );

        content.addView(
                title
        );

        TextView subtitle =
                new TextView(this);

        subtitle.setText(
                "Video yang sudah selesai diunduh."
        );

        subtitle.setTextSize(
                14
        );

        subtitle.setTextColor(
                Color.GRAY
        );

        LinearLayout.LayoutParams
                subtitleParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        subtitleParams.setMargins(
                0,
                6,
                0,
                18
        );

        content.addView(
                subtitle,
                subtitleParams
        );

        // --------------------------------------------------------
        // FINISHED LIST
        // --------------------------------------------------------

        finishedList =
                new LinearLayout(this);

        finishedList.setOrientation(
                LinearLayout.VERTICAL
        );

        content.addView(
                finishedList
        );

        emptyFinishedText =
                new TextView(this);

        emptyFinishedText.setText(
                "Belum ada download selesai."
        );

        emptyFinishedText.setTextSize(
                14
        );

        emptyFinishedText.setTextColor(
                Color.GRAY
        );

        emptyFinishedText.setGravity(
                Gravity.CENTER
        );

        emptyFinishedText.setPadding(
                0,
                40,
                0,
                40
        );

        content.addView(
                emptyFinishedText
        );

        scroll.addView(
                content
        );

        return scroll;
    }

    // ============================================================
    // ADD URL TO QUEUE
    // ============================================================

    private void addUrlToQueue(
            String url
    ) {

        if (url == null) {
            return;
        }

        url = url.trim();

        if (url.isEmpty()) {
            return;
        }

        if (!url.startsWith(
                "http://"
        )
                && !url.startsWith(
                "https://"
        )) {

            return;
        }

        urlQueue.add(
                url
        );

        addQueueItem(
                url,
                urlQueue.size() - 1
        );

        updateEmptyStates();
    }

    // ============================================================
    // ADD QUEUE ITEM
    // ============================================================

    private void addQueueItem(
            String url,
            int index
    ) {

        if (queue == null) {
            return;
        }

        LinearLayout card =
                createCard();

        card.setTag(
                index
        );

        // --------------------------------------------------------
        // NUMBER
        // --------------------------------------------------------

        TextView number =
                new TextView(this);

        number.setText(
                String.format(
                        Locale.getDefault(),
                        "%03d",
                        index + 1
                )
        );

        number.setTextSize(
                12
        );

        number.setTextColor(
                Color.GRAY
        );

        card.addView(
                number
        );

        // --------------------------------------------------------
        // URL
        // --------------------------------------------------------

        TextView urlText =
                new TextView(this);

        urlText.setText(
                url
        );

        urlText.setTextSize(
                14
        );

        urlText.setTextColor(
                Color.DKGRAY
        );

        urlText.setMaxLines(
                2
        );

        LinearLayout.LayoutParams
                urlParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        urlParams.setMargins(
                0,
                7,
                0,
                7
        );

        card.addView(
                urlText,
                urlParams
        );

        // --------------------------------------------------------
        // STATUS
        // --------------------------------------------------------

        TextView status =
                new TextView(this);

        status.setText(
                "Menunggu"
        );

        status.setTextSize(
                13
        );

        status.setTextColor(
                Color.GRAY
        );

        status.setTag(
                "status"
        );

        card.addView(
                status
        );

        queue.addView(
                card
        );
    }

    // ============================================================
    // SET QUEUE STATUS
    // ============================================================

    private void setStatus(
            int index,
            String statusText
    ) {

        if (queue == null) {
            return;
        }

        if (index < 0
                || index >= queue.getChildCount()) {

            return;
        }

        View item =
                queue.getChildAt(
                        index
                );

        View status =
                item.findViewWithTag(
                        "status"
                );

        if (status instanceof TextView) {

            ((TextView) status)
                    .setText(
                            statusText
                    );
        }
    }

    // ============================================================
    // UPDATE EMPTY STATES
    // ============================================================

    private void updateEmptyStates() {

        if (emptyProgressText != null) {

            emptyProgressText.setVisibility(
                    urlQueue.isEmpty()
                            ? View.VISIBLE
                            : View.GONE
            );
        }

        if (emptyFinishedText != null
                && finishedList != null) {

            emptyFinishedText.setVisibility(
                    finishedList.getChildCount() == 0
                            ? View.VISIBLE
                            : View.GONE
            );
        }
    }

    // ============================================================
    // START CURRENT DOWNLOAD
    // ============================================================

    private void startCurrentDownload() {

        if (urlQueue.isEmpty()) {
            return;
        }

        if (currentIndex < 0
                || currentIndex >= urlQueue.size()) {

            return;
        }

        currentTikTokUrl =
                urlQueue.get(
                        currentIndex
                );

        submitClicked = false;
        mp4HdClicked = false;
        waitingForDownload = false;

        currentCaption = "";

        setStatus(
                currentIndex,
                "Memproses..."
        );

        showPage(1);

        handler.postDelayed(
                new Runnable() {

                    @Override
                    public void run() {

                        injectTikTokUrl();
                    }
                },
                500
        );
    }

    // ============================================================
    // WAIT FOR SAVETIKTOK INPUT
    // ============================================================

    private void waitForSaveTikTokInput() {

        handler.postDelayed(
                new Runnable() {

                    @Override
                    public void run() {

                        injectTikTokUrl();
                    }
                },
                800
        );
    }

    // ============================================================
    // INJECT TIKTOK URL
    // ============================================================

    private void injectTikTokUrl() {

        if (webView == null) {
            return;
        }

        if (currentTikTokUrl == null
                || currentTikTokUrl.trim().isEmpty()) {

            return;
        }

        if (submitClicked) {
            return;
        }

        final String safeUrl =
                currentTikTokUrl
                        .replace(
                                "\\",
                                "\\\\"
                        )
                        .replace(
                                "'",
                                "\\'"
                        )
                        .replace(
                                "\n",
                                "\\n"
                        )
                        .replace(
                                "\r",
                                ""
                        );

        String javascript =
                "(function(){"
                        + "var input=document.querySelector('input[type=\"text\"], input[name=\"url\"], textarea');"
                        + "if(!input){return 'NO_INPUT';}"
                        + "input.focus();"
                        + "input.value='" + safeUrl + "';"
                        + "input.dispatchEvent(new Event('input',{bubbles:true}));"
                        + "input.dispatchEvent(new Event('change',{bubbles:true}));"
                        + "var buttons=document.querySelectorAll('button,a,input[type=\"submit\"]');"
                        + "for(var i=0;i<buttons.length;i++){"
                        + "var t=(buttons[i].innerText||buttons[i].value||'').trim().toLowerCase();"
                        + "if(t==='unduh'||t==='download'){"
                        + "buttons[i].click();"
                        + "return 'CLICKED';"
                        + "}"
                        + "}"
                        + "return 'NO_BUTTON';"
                        + "})();";

        webView.evaluateJavascript(
                javascript,
                value -> {

                    if (value == null) {
                        return;
                    }

                    if (value.contains(
                            "CLICKED"
                    )) {

                        submitClicked = true;

                        setStatus(
                                currentIndex,
                                "Mencari video..."
                        );

                        handler.postDelayed(
                                new Runnable() {

                                    @Override
                                    public void run() {

                                        findMp4HdButton();
                                    }
                                },
                                1500
                        );

                    } else {

                        handler.postDelayed(
                                new Runnable() {

                                    @Override
                                    public void run() {

                                        injectTikTokUrl();
                                    }
                                },
                                1000
                        );
                    }
                }
        );
    }

    // ============================================================
    // FIND MP4 HD BUTTON + CAPTION
    // ============================================================

    private void findMp4HdButton() {

        if (webView == null) {
            return;
        }

        if (mp4HdClicked) {
            return;
        }

        String javascript =
                "(function(){"

                        + "var result=document.querySelector('#download-result');"
                        + "if(!result){return 'NO_RESULT';}"

                        + "var card=result.querySelector('.video-data .tik-video');"
                        + "if(!card){return 'NO_CARD';}"

                        + "var captionEl=card.querySelector('.tik-left .thumbnail .content .clearfix h3');"

                        + "var caption=captionEl ? captionEl.innerText.trim() : '';"

                        + "var links=card.querySelectorAll('.dl-action a.tik-button-dl');"

                        + "for(var i=0;i<links.length;i++){"

                        + "var text=(links[i].innerText||'').trim().toLowerCase();"

                        + "if(text.indexOf('mp4 hd')!==-1){"

                        + "if(!caption){return 'NO_CAPTION';}"

                        + "links[i].click();"

                        + "return 'HD_CLICKED|'+encodeURIComponent(caption);"

                        + "}"

                        + "}"

                        + "return 'NO_HD';"

                        + "})();";

        webView.evaluateJavascript(
                javascript,
                value -> {

                    if (value == null) {
                        retryFindMp4Hd();
                        return;
                    }

                    String decoded =
                            value
                                    .replace(
                                            "\"",
                                            ""
                                    );

                    if (decoded.contains(
                            "HD_CLICKED|"
                    )) {

                        String encoded =
                                decoded.substring(
                                        decoded.indexOf(
                                                "HD_CLICKED|"
                                        ) + 10
                                );

                        try {

                            currentCaption =
                                    Uri.decode(
                                            encoded
                                    );

                        } catch (Exception e) {

                            currentCaption =
                                    encoded;
                        }

                        mp4HdClicked = true;

                        setStatus(
                                currentIndex,
                                "Download video..."
                        );

                    } else {

                        retryFindMp4Hd();
                    }
                }
        );
    }

    // ============================================================
    // RETRY MP4 HD
    // ============================================================

    private void retryFindMp4Hd() {

        handler.postDelayed(
                new Runnable() {

                    @Override
                    public void run() {

                        findMp4HdButton();
                    }
                },
                1000
        );
    }

    // ============================================================
    // HANDLE WEB DOWNLOAD
    // ============================================================

    private void handleWebDownload(
            String url,
            String userAgent,
            String contentDisposition,
            String mimetype
    ) {

        if (url == null
                || url.trim().isEmpty()) {

            return;
        }

        // Hanya menerima download setelah
        // tombol MP4 HD benar-benar diklik.
        if (!mp4HdClicked) {
            return;
        }

        String lowerUrl =
                url.toLowerCase(
                        Locale.US
                );

        String lowerMime =
                mimetype == null
                        ? ""
                        : mimetype.toLowerCase(
                        Locale.US
                );

        boolean videoMime =
                lowerMime.startsWith(
                        "video/"
                );

        boolean binaryMime =
                lowerMime.contains(
                        "octet-stream"
                );

        boolean binUrl =
                lowerUrl.contains(
                        ".bin"
                );

        boolean videoExtension =
                lowerUrl.endsWith(
                        ".mp4"
                )
                        || lowerUrl.contains(
                        ".mp4?"
                );

        if (!videoMime
                && !binaryMime
                && !binUrl
                && !videoExtension) {

            return;
        }

        if (waitingForDownload) {
            return;
        }

        waitingForDownload = true;

        setStatus(
                currentIndex,
                "Mengunduh..."
        );

        enqueueDownload(
                url,
                userAgent,
                contentDisposition,
                lowerMime
        );
    }

    // ============================================================
    // ENQUEUE DOWNLOAD
    // ============================================================

    private void enqueueDownload(
            String url,
            String userAgent,
            String contentDisposition,
            String mimetype
    ) {

        try {

            int folderNumber =
                    currentIndex + 1;

            String folderName =
                    String.format(
                            Locale.getDefault(),
                            "%03d",
                            folderNumber
                    );

            String fileName =
                    "video.mp4";

            DownloadManager.Request request =
                    new DownloadManager.Request(
                            Uri.parse(url)
                    );

            request.setTitle(
                    "TikTok " + folderName
            );

            request.setDescription(
                    "TikTokDownloadManager"
            );

            request.setMimeType(
                    "video/mp4"
            );

            request.setNotificationVisibility(
                    DownloadManager.Request
                            .VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            );

            request.setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS
                            + "/TikTokDownloadManager/"
                            + folderName,
                    fileName
            );

            if (userAgent != null
                    && !userAgent.isEmpty()) {

                request.addRequestHeader(
                        "User-Agent",
                        userAgent
                );
            }

            String cookies =
                    CookieManager
                            .getInstance()
                            .getCookie(url);

            if (cookies != null
                    && !cookies.isEmpty()) {

                request.addRequestHeader(
                        "Cookie",
                        cookies
                );
            }

            currentDownloadId =
                    downloadManager.enqueue(
                            request
                    );

            saveCaptionFile(
                    folderName,
                    currentCaption
            );

            waitForDownloadCompletion();

        } catch (Exception e) {

            waitingForDownload = false;

            setStatus(
                    currentIndex,
                    "Download gagal"
            );
        }
    }
        // ============================================================
    // WAIT FOR DOWNLOAD COMPLETION
    // ============================================================

    private void waitForDownloadCompletion() {

        if (currentDownloadId == -1) {
            return;
        }

        handler.postDelayed(
                new Runnable() {

                    @Override
                    public void run() {

                        checkDownloadStatus();
                    }
                },
                1000
        );
    }

    // ============================================================
    // CHECK DOWNLOAD STATUS
    // ============================================================

    private void checkDownloadStatus() {

        if (currentDownloadId == -1) {
            return;
        }

        DownloadManager.Query query =
                new DownloadManager.Query();

        query.setFilterById(
                currentDownloadId
        );

        Cursor cursor = null;

        try {

            cursor =
                    downloadManager.query(
                            query
                    );

            if (cursor != null
                    && cursor.moveToFirst()) {

                int status =
                        cursor.getInt(
                                cursor.getColumnIndexOrThrow(
                                        DownloadManager.COLUMN_STATUS
                                )
                        );

                if (status ==
                        DownloadManager.STATUS_SUCCESSFUL) {

                    cursor.close();

                    downloadFinished();

                    return;

                } else if (status ==
                        DownloadManager.STATUS_FAILED) {

                    cursor.close();

                    downloadFailed();

                    return;
                }
            }

        } catch (Exception e) {

            // Abaikan error sementara.
            // Status akan diperiksa kembali.
        } finally {

            if (cursor != null
                    && !cursor.isClosed()) {

                cursor.close();
            }
        }

        // Belum selesai.
        handler.postDelayed(
                new Runnable() {

                    @Override
                    public void run() {

                        checkDownloadStatus();
                    }
                },
                1000
        );
    }

    // ============================================================
    // DOWNLOAD FINISHED
    // ============================================================

    private void downloadFinished() {

        waitingForDownload = false;
        currentDownloadId = -1;

        setStatus(
                currentIndex,
                "Selesai ✓"
        );

        addFinishedItem(
                currentIndex,
                currentTikTokUrl,
                currentCaption
        );

        // Beri sedikit jeda sebelum
        // berpindah ke video berikutnya.
        handler.postDelayed(
                new Runnable() {

                    @Override
                    public void run() {

                        finishCurrentAndNext();
                    }
                },
                800
        );
    }

    // ============================================================
    // DOWNLOAD FAILED
    // ============================================================

    private void downloadFailed() {

        waitingForDownload = false;
        currentDownloadId = -1;

        setStatus(
                currentIndex,
                "Download gagal"
        );

        handler.postDelayed(
                new Runnable() {

                    @Override
                    public void run() {

                        finishCurrentAndNext();
                    }
                },
                800
        );
    }

    // ============================================================
    // FINISH CURRENT AND NEXT
    // ============================================================

    private void finishCurrentAndNext() {

        currentCaption = "";
        currentTikTokUrl = "";

        currentIndex++;

        if (currentIndex < urlQueue.size()) {

            submitClicked = false;
            mp4HdClicked = false;
            waitingForDownload = false;

            startCurrentDownload();

        } else {

            submitClicked = false;
            mp4HdClicked = false;
            waitingForDownload = false;

            showPage(2);
        }
    }

    // ============================================================
    // ADD FINISHED ITEM
    // ============================================================

    private void addFinishedItem(
            int index,
            String url,
            String caption
    ) {

        if (finishedList == null) {
            return;
        }

        LinearLayout card =
                createCard();

        // --------------------------------------------------------
        // HEADER
        // --------------------------------------------------------

        LinearLayout header =
                new LinearLayout(this);

        header.setOrientation(
                LinearLayout.HORIZONTAL
        );

        header.setGravity(
                Gravity.CENTER_VERTICAL
        );

        TextView number =
                new TextView(this);

        number.setText(
                String.format(
                        Locale.getDefault(),
                        "%03d",
                        index + 1
                )
        );

        number.setTextSize(
                12
        );

        number.setTextColor(
                Color.GRAY
        );

        header.addView(
                number,
                new LinearLayout.LayoutParams(
                        70,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        TextView done =
                new TextView(this);

        done.setText(
                "Selesai ✓"
        );

        done.setTextSize(
                13
        );

        done.setTextColor(
                Color.rgb(
                        35,
                        130,
                        70
                )
        );

        header.addView(
                done,
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1
                )
        );

        card.addView(
                header
        );

        // --------------------------------------------------------
        // CAPTION
        // --------------------------------------------------------

        TextView captionText =
                new TextView(this);

        String displayCaption =
                caption == null
                        ? ""
                        : caption.trim();

        if (displayCaption.isEmpty()) {

            displayCaption =
                    "Caption tidak tersedia.";
        }

        captionText.setText(
                displayCaption
        );

        captionText.setTextSize(
                14
        );

        captionText.setTextColor(
                Color.DKGRAY
        );

        captionText.setPadding(
                0,
                12,
                0,
                8
        );

        card.addView(
                captionText
        );

        // --------------------------------------------------------
        // FOLDER INFO
        // --------------------------------------------------------

        TextView folderText =
                new TextView(this);

        String folderName =
                String.format(
                        Locale.getDefault(),
                        "%03d",
                        index + 1
                );

        folderText.setText(
                "Folder: Download/TikTokDownloadManager/"
                        + folderName
        );

        folderText.setTextSize(
                12
        );

        folderText.setTextColor(
                Color.GRAY
        );

        card.addView(
                folderText
        );

        // --------------------------------------------------------
        // OPEN FOLDER BUTTON
        // --------------------------------------------------------

        Button openButton =
                new Button(this);

        openButton.setText(
                "Buka Folder"
        );

        openButton.setAllCaps(
                false
        );

        openButton.setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {

                        openDownloadFolder(
                                folderName
                        );
                    }
                }
        );

        LinearLayout.LayoutParams
                buttonParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        buttonParams.setMargins(
                0,
                10,
                0,
                0
        );

        card.addView(
                openButton,
                buttonParams
        );

        finishedList.addView(
                card,
                0
        );

        updateEmptyStates();
    }

    // ============================================================
    // OPEN DOWNLOAD FOLDER
    // ============================================================

    private void openDownloadFolder(
            String folderName
    ) {

        try {

            Uri uri =
                    Uri.parse(
                            "content://com.android.externalstorage.documents/root/primary"
                    );

            Intent intent =
                    new Intent(
                            Intent.ACTION_OPEN_DOCUMENT_TREE
                    );

            intent.putExtra(
                    "android.provider.extra.SHOW_ADVANCED",
                    true
            );

            startActivityForResult(
                    intent,
                    500
            );

        } catch (Exception e) {

            try {

                Intent intent =
                        new Intent(
                                Intent.ACTION_VIEW
                        );

                intent.setData(
                        Uri.parse(
                                "content://com.android.externalstorage.documents/root/primary"
                        )
                );

                startActivity(
                        intent
                );

            } catch (Exception ignored) {
            }
        }
    }

    // ============================================================
    // SAVE CAPTION FILE
    // ============================================================

    private void saveCaptionFile(
            String folderName,
            String caption
    ) {

        if (caption == null) {
            caption = "";
        }

        final String finalCaption =
                caption.trim();

        try {

            if (Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.Q) {

                ContentValuesHelper
                        .saveCaption(
                                this,
                                folderName,
                                finalCaption
                        );

            } else {

                File downloads =
                        Environment
                                .getExternalStoragePublicDirectory(
                                        Environment.DIRECTORY_DOWNLOADS
                                );

                File folder =
                        new File(
                                downloads,
                                "TikTokDownloadManager/"
                                        + folderName
                        );

                if (!folder.exists()) {

                    folder.mkdirs();
                }

                File captionFile =
                        new File(
                                folder,
                                "caption.txt"
                        );

                FileOutputStream output =
                        new FileOutputStream(
                                captionFile
                        );

                OutputStreamWriter writer =
                        new OutputStreamWriter(
                                output,
                                "UTF-8"
                        );

                BufferedWriter bufferedWriter =
                        new BufferedWriter(
                                writer
                        );

                bufferedWriter.write(
                        finalCaption
                );

                bufferedWriter.flush();
                bufferedWriter.close();
                output.close();
            }

        } catch (Exception e) {

            // Caption gagal disimpan,
            // download video tetap dilanjutkan.
        }
    }

    // ============================================================
    // DOWNLOAD RECEIVER
    // ============================================================

    private void registerDownloadReceiver() {

        try {

            IntentFilter filter =
                    new IntentFilter(
                            DownloadManager.ACTION_DOWNLOAD_COMPLETE
                    );

            if (Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.TIRAMISU) {

                registerReceiver(
                        downloadReceiver,
                        filter,
                        Context.RECEIVER_NOT_EXPORTED
                );

            } else {

                registerReceiver(
                        downloadReceiver,
                        filter
                );
            }

        } catch (Exception e) {
            // Receiver gagal didaftarkan.
        }
    }

    // ============================================================
    // ON DESTROY
    // ============================================================

    @Override
    protected void onDestroy() {

        try {

            unregisterReceiver(
                    downloadReceiver
            );

        } catch (Exception ignored) {
        }

        if (webView != null) {

            webView.stopLoading();

            webView.loadUrl(
                    "about:blank"
            );

            webView.clearHistory();

            webView.removeAllViews();

            webView.destroy();

            webView = null;
        }

        super.onDestroy();
    }

    // ============================================================
    // RELOAD SAVE TIKTOK
    // ============================================================

    private void reloadSaveTikTok() {

        if (webView == null) {
            return;
        }

        submitClicked = false;
        mp4HdClicked = false;
        waitingForDownload = false;

        currentCaption = "";

        webView.loadUrl(
                SAVE_TIKTOK_URL
        );
    }

    // ============================================================
    // CLEAR QUEUE
    // ============================================================

    private void clearQueue() {

        urlQueue.clear();

        currentIndex = 0;

        submitClicked = false;
        mp4HdClicked = false;
        waitingForDownload = false;

        currentTikTokUrl = "";
        currentCaption = "";

        if (queue != null) {

            queue.removeAllViews();
        }

        updateEmptyStates();
    }

    // ============================================================
    // ADD MULTIPLE URL
    // ============================================================

    private void addMultipleUrls(
            String text
    ) {

        if (text == null
                || text.trim().isEmpty()) {

            return;
        }

        String[] lines =
                text.split(
                        "\\r?\\n"
                );

        for (String line : lines) {

            String url =
                    line.trim();

            if (!url.isEmpty()) {

                addUrlToQueue(
                        url
                );
            }
        }

        updateEmptyStates();
    }
        // ============================================================
    // CONTENT VALUES HELPER
    // Android 10+
    // ============================================================

    private static class ContentValuesHelper {

        static void saveCaption(
                Context context,
                String folderName,
                String caption
        ) throws Exception {

            android.content.ContentResolver resolver =
                    context.getContentResolver();

            android.content.ContentValues values =
                    new android.content.ContentValues();

            values.put(
                    MediaStore.Downloads.DISPLAY_NAME,
                    "caption.txt"
            );

            values.put(
                    MediaStore.Downloads.MIME_TYPE,
                    "text/plain"
            );

            // Folder tujuan:
            // Download/TikTokDownloadManager/001/
            String relativePath =
                    Environment.DIRECTORY_DOWNLOADS
                            + "/TikTokDownloadManager/"
                            + folderName
                            + "/";

            values.put(
                    MediaStore.Downloads.RELATIVE_PATH,
                    relativePath
            );

            Uri uri =
                    resolver.insert(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                            values
                    );

            if (uri == null) {
                throw new Exception(
                        "Tidak dapat membuat caption.txt"
                );
            }

            OutputStream output =
                    null;

            try {

                output =
                        resolver.openOutputStream(
                                uri
                        );

                if (output == null) {
                    throw new Exception(
                            "OutputStream null"
                    );
                }

                byte[] data =
                        caption.getBytes(
                                "UTF-8"
                        );

                output.write(
                        data
                );

                output.flush();

            } finally {

                if (output != null) {

                    try {
                        output.close();
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }

    // ============================================================
    // WEBVIEW INPUT HELPER
    // ============================================================

    private void clearSaveTikTokInput() {

        if (webView == null) {
            return;
        }

        webView.evaluateJavascript(
                "(function(){"
                        + "var input=document.querySelector('input[type=\"text\"],input[name=\"url\"],textarea');"
                        + "if(input){"
                        + "input.value='';"
                        + "input.dispatchEvent(new Event('input',{bubbles:true}));"
                        + "input.dispatchEvent(new Event('change',{bubbles:true}));"
                        + "}"
                        + "})();",
                null
        );
    }

    // ============================================================
    // MP4 HD BUTTON HELPER
    // ============================================================

    private void clickMp4HdButton() {

        if (webView == null) {
            return;
        }

        webView.evaluateJavascript(
                "(function(){"
                        + "var card=document.querySelector('#download-result .video-data .tik-video');"
                        + "if(!card){return 'NO_CARD';}"
                        + "var links=card.querySelectorAll('.dl-action a.tik-button-dl');"
                        + "for(var i=0;i<links.length;i++){"
                        + "var text=(links[i].innerText||'').trim().toLowerCase();"
                        + "if(text.indexOf('mp4 hd')!==-1){"
                        + "links[i].click();"
                        + "return 'CLICKED';"
                        + "}"
                        + "}"
                        + "return 'NO_HD';"
                        + "})();",
                value -> {
                    // Tombol ditangani oleh findMp4HdButton().
                }
        );
    }

    // ============================================================
    // PERMISSION HELPER
    // Untuk Android lama jika diperlukan.
    // ============================================================

    private void requestStoragePermissionIfNeeded() {

        if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.Q) {

            return;
        }

        if (checkSelfPermission(
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) != PackageManager.PERMISSION_GRANTED) {

            requestPermissions(
                    new String[]{
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                    },
                    1001
            );
        }
    }

    // ============================================================
    // BACK BUTTON
    // ============================================================

    @Override
    public void onBackPressed() {

        // Kalau sedang di halaman Progress,
        // kembali ke halaman Download.
        if (currentPage != 0) {

            showPage(0);

            return;
        }

        super.onBackPressed();
    }
}
