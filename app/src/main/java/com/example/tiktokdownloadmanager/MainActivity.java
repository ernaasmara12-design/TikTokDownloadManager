package com.example.tiktokdownloadmanager;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends android.app.Activity {

    // ============================================================
    // SAVE TIKTOK
    // ============================================================

    private static final String SAVE_TIKTOK_URL =
            "https://savetiktok.to/id";

    // ============================================================
    // WEBVIEW
    // ============================================================

    private WebView webView;

    private final android.os.Handler handler =
            new android.os.Handler();

    // ============================================================
    // INPUT
    // ============================================================

    private EditText input;

    // ============================================================
    // QUEUE
    // ============================================================

    private final ArrayList<String> urlQueue =
            new ArrayList<>();

    private int currentIndex = 0;

    private String currentTikTokUrl = "";
    private String currentCaption = "";

    // ============================================================
    // STATE
    // ============================================================

    private boolean pageReady = false;
    private boolean processing = false;
    private boolean submitClicked = false;
    private boolean mp4HdClicked = false;
    private boolean waitingForDownload = false;

    private long currentDownloadId = -1;

    // ============================================================
    // UI
    // ============================================================

    private FrameLayout pageContainer;

    private View tabPage;
    private View progressPage;
    private View finishedPage;

    private LinearLayout queue;
    private LinearLayout finishedList;

    private TextView emptyProgressText;
    private TextView emptyFinishedText;

    private TextView navTab;
    private TextView navProgress;
    private TextView navFinished;

    private Button startAllButton;

    private int currentPage = 0;

    // ============================================================
    // DOWNLOAD MANAGER
    // ============================================================

    private DownloadManager downloadManager;

    // ============================================================
    // DOWNLOAD RECEIVER
    // ============================================================

    private final BroadcastReceiver downloadReceiver =
            new BroadcastReceiver() {

                @Override
                public void onReceive(
                        Context context,
                        Intent intent
                ) {

                    if (!DownloadManager.ACTION_DOWNLOAD_COMPLETE
                            .equals(intent.getAction())) {
                        return;
                    }

                    long id =
                            intent.getLongExtra(
                                    DownloadManager.EXTRA_DOWNLOAD_ID,
                                    -1
                            );

                    if (id == currentDownloadId) {

                        checkDownloadStatus();
                    }
                }
            };

    // ============================================================
    // ON CREATE
    // ============================================================

    @Override
    protected void onCreate(
            Bundle savedInstanceState
    ) {

        super.onCreate(savedInstanceState);

        downloadManager =
                (DownloadManager)
                        getSystemService(
                                DOWNLOAD_SERVICE
                        );

        buildUi();

        createWebView();

        registerDownloadReceiver();

        loadSaveTikTok();
    }

    // ============================================================
    // CREATE WEBVIEW
    // ============================================================

    private void createWebView() {

        webView =
                new WebView(this);

        WebSettings settings =
                webView.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);

        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        settings.setSupportMultipleWindows(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(
                true
        );

        settings.setUserAgentString(
                "Mozilla/5.0 (Linux; Android 12; Mobile) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/120.0 Mobile Safari/537.36"
        );

        CookieManager cookies =
                CookieManager.getInstance();

        cookies.setAcceptCookie(true);

        if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.LOLLIPOP) {

            cookies.setAcceptThirdPartyCookies(
                    webView,
                    true
            );
        }

        webView.setWebViewClient(
                new WebViewClient() {

                    @Override
                    public void onPageFinished(
                            WebView view,
                            String url
                    ) {

                        super.onPageFinished(
                                view,
                                url
                        );

                        if (!url.contains(
                                "savetiktok.to"
                        )) {
                            return;
                        }

                        pageReady = true;

                        // Jika sedang memproses video,
                        // mulai inject setelah halaman siap.
                        if (processing) {

                            handler.postDelayed(
                                    new Runnable() {

                                        @Override
                                        public void run() {

                                            injectTikTokUrl();
                                        }
                                    },
                                    1200
                            );
                        }
                    }
                }
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

        // --------------------------------------------------------
        // WEBVIEW DISEMBUNYIKAN
        // --------------------------------------------------------

        FrameLayout hidden =
                new FrameLayout(this);

        hidden.addView(
                webView,
                new FrameLayout.LayoutParams(
                        1,
                        1
                )
        );

        addContentView(
                hidden,
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

        pageReady = false;

        if (webView != null) {

            webView.loadUrl(
                    SAVE_TIKTOK_URL
            );
        }
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
                        248,
                        249,
                        251
                )
        );

        // --------------------------------------------------------
        // PAGE CONTAINER
        // --------------------------------------------------------

        pageContainer =
                new FrameLayout(this);

        root.addView(
                pageContainer,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1
                )
        );

        // --------------------------------------------------------
        // PAGES
        // --------------------------------------------------------

        tabPage =
                buildTabPage();

        progressPage =
                buildProgressPage();

        finishedPage =
                buildFinishedPage();

        pageContainer.addView(
                tabPage
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

        bottom.setBackgroundColor(
                Color.WHITE
        );

        navTab =
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

        bottom.addView(
                navTab,
                new LinearLayout.LayoutParams(
                        0,
                        72,
                        1
                )
        );

        bottom.addView(
                navProgress,
                new LinearLayout.LayoutParams(
                        0,
                        72,
                        1
                )
        );

        bottom.addView(
                navFinished,
                new LinearLayout.LayoutParams(
                        0,
                        72,
                        1
                )
        );

        navTab.setOnClickListener(
                v -> showPage(0)
        );

        navProgress.setOnClickListener(
                v -> showPage(1)
        );

        navFinished.setOnClickListener(
                v -> showPage(2)
        );

        root.addView(
                bottom,
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
            String icon,
            String title
    ) {

        TextView view =
                new TextView(this);

        view.setText(
                icon + "\n" + title
        );

        view.setTextSize(
                12
        );

        view.setGravity(
                Gravity.CENTER
        );

        view.setTextColor(
                Color.GRAY
        );

        return view;
    }

    // ============================================================
    // SHOW PAGE
    // ============================================================

    private void showPage(
            int page
    ) {

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
    // UPDATE NAV
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

        TextView title =
                new TextView(this);

        title.setText(
                "TikTok Download Manager"
        );

        title.setTextSize(
                25
        );

        title.setTypeface(
                null,
                android.graphics.Typeface.BOLD
        );

        title.setTextColor(
                Color.rgb(
                        25,
                        25,
                        25
                )
        );

        content.addView(
                title
        );

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

        LinearLayout.LayoutParams subParams =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );

        subParams.setMargins(
                0,
                6,
                0,
                22
        );

        content.addView(
                subtitle,
                subParams
        );

        // --------------------------------------------------------
        // URL CARD
        // --------------------------------------------------------

        LinearLayout card =
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

        card.addView(
                urlTitle
        );

        input =
                new EditText(this);

        input.setHint(
                "Paste URL TikTok di sini"
        );

        input.setTextSize(
                14
        );

        input.setSingleLine(
                true
        );

        input.setPadding(
                16,
                12,
                16,
                12
        );

        LinearLayout.LayoutParams inputParams =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );

        inputParams.setMargins(
                0,
                12,
                0,
                12
        );

        card.addView(
                input,
                inputParams
        );

        Button add =
                new Button(this);

        add.setText(
                "Tambah ke Antrian"
        );

        add.setAllCaps(
                false
        );

        add.setOnClickListener(
                v -> {

                    String url =
                            input.getText()
                                    .toString()
                                    .trim();

                    if (url.isEmpty()) {
                        return;
                    }

                    addUrlToQueue(
                            url
                    );

                    input.setText("");

                    showPage(1);
                }
        );

        card.addView(
                add
        );

        content.addView(
                card
        );

        // --------------------------------------------------------
        // PLATFORM
        // --------------------------------------------------------

        LinearLayout platform =
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

        platform.addView(
                platformTitle
        );

        TextView platformText =
                new TextView(this);

        platformText.setText(
                "♪  TikTok        ✓  Public Video"
        );

        platformText.setTextSize(
                14
        );

        platformText.setTextColor(
                Color.DKGRAY
        );

        platformText.setPadding(
                0,
                16,
                0,
                8
        );

        platform.addView(
                platformText
        );

        content.addView(
                platform
        );

        // --------------------------------------------------------
        // HOW TO
        // --------------------------------------------------------

        LinearLayout how =
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

        how.addView(
                howTitle
        );

        addStep(
                how,
                "1",
                "Paste link TikTok"
        );

        addStep(
                how,
                "2",
                "Tambahkan ke antrian"
        );

        addStep(
                how,
                "3",
                "Tekan Mulai Semua"
        );

        addStep(
                how,
                "4",
                "Video dan caption tersimpan otomatis"
        );

        content.addView(
                how
        );

        scroll.addView(
                content
        );

        return scroll;
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

        LinearLayout.LayoutParams params =
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
                Color.rgb(
                        25,
                        25,
                        25
                )
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

        LinearLayout.LayoutParams subParams =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );

        subParams.setMargins(
                0,
                6,
                0,
                18
        );

        content.addView(
                subtitle,
                subParams
        );

        startAllButton =
                new Button(this);

        startAllButton.setText(
                "Mulai Semua"
        );

        startAllButton.setAllCaps(
                false
        );

        startAllButton.setOnClickListener(
                v -> startAll()
        );

        content.addView(
                startAllButton
        );

        queue =
                new LinearLayout(this);

        queue.setOrientation(
                LinearLayout.VERTICAL
        );

        LinearLayout.LayoutParams queueParams =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
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
                Color.rgb(
                        25,
                        25,
                        25
                )
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

        LinearLayout.LayoutParams subParams =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );

        subParams.setMargins(
                0,
                6,
                0,
                18
        );

        content.addView(
                subtitle,
                subParams
        );

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
    // ADD URL
    // ============================================================

    private void addUrlToQueue(
            String url
    ) {

        if (url == null) {
            return;
        }

        url =
                url.trim();

        if (url.isEmpty()) {
            return;
        }

        if (!url.startsWith("http://")
                && !url.startsWith("https://")) {

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
    // QUEUE ITEM
    // ============================================================

    private void addQueueItem(
            String url,
            int index
    ) {

        LinearLayout card =
                createCard();

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

        LinearLayout.LayoutParams urlParams =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
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
    // SET STATUS
    // ============================================================

    private void setStatus(
            int index,
            String text
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
                            text
                    );
        }
    }

    // ============================================================
    // START ALL
    // ============================================================

    private void startAll() {

        if (urlQueue.isEmpty()) {
            return;
        }

        if (processing) {
            return;
        }

        currentIndex = 0;

        startCurrentDownload();
    }

    // ============================================================
    // START CURRENT
    // ============================================================

    private void startCurrentDownload() {

        if (currentIndex < 0
                || currentIndex >= urlQueue.size()) {

            processing = false;

            showPage(2);

            return;
        }

        processing = true;

        currentTikTokUrl =
                urlQueue.get(
                        currentIndex
                );

        currentCaption = "";

        submitClicked = false;
        mp4HdClicked = false;
        waitingForDownload = false;
        currentDownloadId = -1;

        setStatus(
                currentIndex,
                "Membuka SaveTikTok..."
        );

        showPage(1);

        // --------------------------------------------------------
        // PENTING:
        // Setiap URL membuka ulang halaman SaveTikTok.
        // --------------------------------------------------------

        pageReady = false;

        webView.loadUrl(
                SAVE_TIKTOK_URL
        );
    }

    // ============================================================
    // INJECT URL
    // ============================================================

    private void injectTikTokUrl() {

        if (!processing) {
            return;
        }

        if (!pageReady) {
            return;
        }

        if (submitClicked) {
            return;
        }

        if (currentTikTokUrl == null
                || currentTikTokUrl.trim().isEmpty()) {

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

        String js =
                "(function(){"
                        + "var input="
                        + "document.querySelector("
                        + "'input[type=\"text\"],input[name=\"url\"],textarea'"
                        + ");"

                        + "if(!input){"
                        + "return 'NO_INPUT';"
                        + "}"

                        + "input.focus();"

                        + "input.value='"
                        + safeUrl
                        + "';"

                        + "input.dispatchEvent("
                        + "new Event('input',{bubbles:true})"
                        + ");"

                        + "input.dispatchEvent("
                        + "new Event('change',{bubbles:true})"
                        + ");"

                        + "var els="
                        + "document.querySelectorAll("
                        + " 'button,a,input[type=\"submit\"]'"
                        + ");"

                        + "for(var i=0;i<els.length;i++){"

                        + "var t=("
                        + "els[i].innerText||"
                        + "els[i].value||"
                        + "''"
                        + ").trim().toLowerCase();"

                        + "if(t==='unduh'||"
                        + "t==='download'){"

                        + "els[i].click();"

                        + "return 'CLICKED';"

                        + "}"

                        + "}"

                        + "return 'NO_BUTTON';"

                        + "})();";

        webView.evaluateJavascript(
                js,
                value -> {

                    if (value == null) {

                        retryInject();

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
                                () -> findMp4HdButton(),
                                1800
                        );

                    } else {

                        retryInject();
                    }
                }
        );
    }

    // ============================================================
    // RETRY INPUT
    // ============================================================

    private void retryInject() {

        handler.postDelayed(
                () -> {

                    if (processing) {

                        injectTikTokUrl();
                    }

                },
                1000
        );
    }

    // ============================================================
    // FIND MP4 HD
    // ============================================================

    private void findMp4HdButton() {

        if (!processing) {
            return;
        }

        if (mp4HdClicked) {
            return;
        }

        String js =
                "(function(){"

                        + "var result="
                        + "document.querySelector("
                        + "'#download-result'"
                        + ");"

                        + "if(!result){"
                        + "return 'NO_RESULT';"
                        + "}"

                        + "var card="
                        + "result.querySelector("
                        + "'.video-data .tik-video'"
                        + ");"

                        + "if(!card){"
                        + "return 'NO_CARD';"
                        + "}"

                        + "var captionEl="
                        + "card.querySelector("
                        + "'.tik-left .thumbnail .content .clearfix h3'"
                        + ");"

                        + "var caption="
                        + "captionEl ? "
                        + "captionEl.innerText.trim() : '';"

                        + "var links="
                        + "card.querySelectorAll("
                        + "'.dl-action a.tik-button-dl'"
                        + ");"

                        + "for(var i=0;i<links.length;i++){"

                        + "var text=("
                        + "links[i].innerText||''"
                        + ").trim().toLowerCase();"

                        + "if(text.indexOf('mp4 hd')!==-1){"

                        + "if(!caption){"
                        + "return 'NO_CAPTION';"
                        + "}"

                        + "links[i].click();"

                        + "return 'HD_CLICKED|' + "
                        + "encodeURIComponent(caption);"

                        + "}"

                        + "}"

                        + "return 'NO_HD';"

                        + "})();";

        webView.evaluateJavascript(
                js,
                value -> {

                    if (value == null) {

                        retryFindMp4Hd();

                        return;
                    }

                    String result =
                            value;

                    // evaluateJavascript mengembalikan
                    // string dengan tanda kutip.
                    if (result.length() >= 2
                            && result.startsWith("\"")
                            && result.endsWith("\"")) {

                        result =
                                result.substring(
                                        1,
                                        result.length() - 1
                                );
                    }

                    result =
                            result.replace(
                                    "\\\"",
                                    "\""
                            );

                    if (result.contains(
                            "HD_CLICKED|"
                    )) {

                        String encoded =
                                result.substring(
                                        result.indexOf(
                                                "HD_CLICKED|"
                                        ) + 11
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
                                "Mengunduh..."
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
                () -> {

                    if (processing) {

                        findMp4HdButton();
                    }

                },
                1000
        );
    }

    // ============================================================
    // WEB DOWNLOAD
    // ============================================================

    private void handleWebDownload(
            String url,
            String userAgent,
            String contentDisposition,
            String mimetype
    ) {

        if (!processing) {
            return;
        }

        if (!mp4HdClicked) {
            return;
        }

        if (url == null
                || url.trim().isEmpty()) {

            return;
        }

        if (waitingForDownload) {
            return;
        }

        // --------------------------------------------------------
        // Karena DownloadListener dipanggil setelah link download
        // ditekan, kita tidak perlu memaksa URL harus berakhiran
        // .mp4. SaveTikTok dapat menggunakan URL token/dynamic URL.
        // --------------------------------------------------------

        waitingForDownload = true;

        setStatus(
                currentIndex,
                "Mengunduh video..."
        );

        enqueueDownload(
                url,
                userAgent,
                contentDisposition,
                mimetype
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

            String folderName =
                    String.format(
                            Locale.getDefault(),
                            "%03d",
                            currentIndex + 1
                    );

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

            // ----------------------------------------------------
            // Folder:
            //
            // Download/
            //   TikTokDownloadManager/
            //      001/
            //         video.mp4
            //         caption.txt
            // ----------------------------------------------------

            request.setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS
                            + "/TikTokDownloadManager/"
                            + folderName,
                    "video.mp4"
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
                            .getCookie(
                                    SAVE_TIKTOK_URL
                            );

            if (cookies != null
                    && !cookies.isEmpty()) {

                request.addRequestHeader(
                        "Cookie",
                        cookies
                );
            }

            request.addRequestHeader(
                    "Referer",
                    SAVE_TIKTOK_URL
            );

            currentDownloadId =
                    downloadManager.enqueue(
                            request
                    );

            // Simpan caption ke folder yang sama.
            saveCaptionFile(
                    folderName,
                    currentCaption
            );

            checkDownloadStatus();

        } catch (Exception e) {

            waitingForDownload = false;

            setStatus(
                    currentIndex,
                    "Download gagal"
            );

            handler.postDelayed(
                    () -> nextVideo(),
                    1500
            );
        }
    }

    // ============================================================
    // CHECK DOWNLOAD
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

        android.database.Cursor cursor =
                null;

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

                    downloadSuccess();

                    return;
                }

                if (status ==
                        DownloadManager.STATUS_FAILED) {

                    cursor.close();

                    downloadFailed();

                    return;
                }
            }

        } catch (Exception ignored) {

        } finally {

            if (cursor != null
                    && !cursor.isClosed()) {

                cursor.close();
            }
        }

        handler.postDelayed(
                () -> checkDownloadStatus(),
                1000
        );
    }

    // ============================================================
    // DOWNLOAD SUCCESS
    // ============================================================

    private void downloadSuccess() {

        waitingForDownload = false;
        currentDownloadId = -1;

        setStatus(
                currentIndex,
                "Selesai ✓"
        );

        addFinishedItem(
                currentIndex,
                currentCaption
        );

        handler.postDelayed(
                () -> nextVideo(),
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
                () -> nextVideo(),
                1200
        );
    }

    // ============================================================
    // NEXT VIDEO
    // ============================================================

    private void nextVideo() {

        currentIndex++;

        if (currentIndex >= urlQueue.size()) {

            processing = false;

            currentTikTokUrl = "";
            currentCaption = "";

            submitClicked = false;
            mp4HdClicked = false;
            waitingForDownload = false;

            showPage(2);

            return;
        }

        currentTikTokUrl = "";
        currentCaption = "";

        submitClicked = false;
        mp4HdClicked = false;
        waitingForDownload = false;
        currentDownloadId = -1;

        startCurrentDownload();
    }

    // ============================================================
    // ADD FINISHED ITEM
    // ============================================================

    private void addFinishedItem(
            int index,
            String caption
    ) {

        if (finishedList == null) {
            return;
        }

        LinearLayout card =
                createCard();

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

        done.setPadding(
                0,
                8,
                0,
                8
        );

        card.addView(
                done
        );

        TextView captionText =
                new TextView(this);

        String text =
                caption == null
                        ? ""
                        : caption.trim();

        if (text.isEmpty()) {

            text =
                    "Caption tidak tersedia.";
        }

        captionText.setText(
                text
        );

        captionText.setTextSize(
                14
        );

        captionText.setTextColor(
                Color.DKGRAY
        );

        captionText.setPadding(
                0,
                8,
                0,
                8
        );

        card.addView(
                captionText
        );

        TextView folder =
                new TextView(this);

        String folderName =
                String.format(
                        Locale.getDefault(),
                        "%03d",
                        index + 1
                );

        folder.setText(
                "Download/TikTokDownloadManager/"
                        + folderName
        );

        folder.setTextSize(
                12
        );

        folder.setTextColor(
                Color.GRAY
        );

        card.addView(
                folder
        );

        finishedList.addView(
                card,
                0
        );

        updateEmptyStates();
    }

    // ============================================================
    // SAVE CAPTION
    // ============================================================

    private void saveCaptionFile(
            String folderName,
            String caption
    ) {

        if (caption == null) {
            caption = "";
        }

        try {

            if (Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.Q) {

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

                values.put(
                        MediaStore.Downloads.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS
                                + "/TikTokDownloadManager/"
                                + folderName
                                + "/"
                );

                Uri uri =
                        getContentResolver().insert(
                                MediaStore.Downloads
                                        .EXTERNAL_CONTENT_URI,
                                values
                        );

                if (uri != null) {

                    OutputStream output =
                            getContentResolver()
                                    .openOutputStream(
                                            uri
                                    );

                    if (output != null) {

                        output.write(
                                caption.getBytes(
                                        "UTF-8"
                                )
                        );

                        output.flush();
                        output.close();
                    }
                }

            } else {

                File downloads =
                        Environment
                                .getExternalStoragePublicDirectory(
                                        Environment
                                                .DIRECTORY_DOWNLOADS
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

                File file =
                        new File(
                                folder,
                                "caption.txt"
                        );

                FileOutputStream output =
                        new FileOutputStream(
                                file
                        );

                OutputStreamWriter writer =
                        new OutputStreamWriter(
                                output,
                                "UTF-8"
                        );

                BufferedWriter buffered =
                        new BufferedWriter(
                                writer
                        );

                buffered.write(
                        caption
                );

                buffered.flush();
                buffered.close();
                output.close();
            }

        } catch (Exception ignored) {

        }
    }

    // ============================================================
    // EMPTY STATES
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
    // DOWNLOAD RECEIVER
    // ============================================================

    private void registerDownloadReceiver() {

        try {

            IntentFilter filter =
                    new IntentFilter(
                            DownloadManager
                                    .ACTION_DOWNLOAD_COMPLETE
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

        } catch (Exception ignored) {

        }
    }

    // ============================================================
    // CLEAR QUEUE
    // ============================================================

    private void clearQueue() {

        if (processing) {
            return;
        }

        urlQueue.clear();

        currentIndex = 0;

        currentTikTokUrl = "";
        currentCaption = "";

        submitClicked = false;
        mp4HdClicked = false;
        waitingForDownload = false;

        if (queue != null) {

            queue.removeAllViews();
        }

        updateEmptyStates();
    }

    // ============================================================
    // MULTIPLE URL
    // ============================================================

    private void addMultipleUrls(
            String text
    ) {

        if (text == null) {
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
    // ON BACK PRESSED
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

            webView.removeAllViews();

            webView.destroy();

            webView = null;
        }

        super.onDestroy();
    }
}
