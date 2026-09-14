package com.example.tiktokdownloadmanager;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
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
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final String SAVE_URL = "https://savetiktok.to/id";

    private static final int REQ_STORAGE = 501;

    private static final int MAX_INPUT_CHECKS = 20;
    private static final int MAX_MP4_HD_CHECKS = 30;

    /*
     * DownloadManager tetap dipantau lama.
     * 1200 x 500 ms = sekitar 10 menit.
     */
    private static final int MAX_DOWNLOAD_CHECKS = 1200;
    private static final long DOWNLOAD_CHECK_INTERVAL = 500L;

    /*
     * Persistent storage
     */
    private static final String PREF_NAME = "tiktok_download_manager";
    private static final String KEY_QUEUE = "queue_data";
    private static final String KEY_AUTO_CLIPBOARD = "auto_clipboard";

    /*
     * UI
     */
    private EditText input;

    private LinearLayout queue;
    private LinearLayout finishedList;

    private TextView progress;
    private TextView webStatus;

    private WebView webView;

    private FrameLayout pageContainer;

    private LinearLayout tabPage;
    private LinearLayout progressPage;
    private LinearLayout finishedPage;

    private Button tabNav;
    private Button progressNav;
    private Button finishedNav;

    private Button autoClipboardButton;

    /*
     * Queue data
     */
    private final ArrayList<String> urls = new ArrayList<>();
    private final ArrayList<String> statuses = new ArrayList<>();
    private final ArrayList<String> captions = new ArrayList<>();
    private final ArrayList<Long> downloadIds = new ArrayList<>();

    /*
     * Finished items
     */
    private final ArrayList<Integer> finishedIndexes = new ArrayList<>();

    /*
     * Runtime state
     */
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

    /*
     * Clipboard
     */
    private ClipboardManager clipboardManager;

    private boolean autoClipboardEnabled = true;

    private boolean clipboardListenerRegistered = false;

    private String lastClipboardText = "";

    /*
     * Prevent duplicate persistence operations from causing
     * unnecessary UI work.
     */
    private boolean restoringState = false;


    // ============================================================
    // ACTIVITY
    // ============================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        buildUi();

        loadPersistentState();

        requestStorageIfNeeded();

        setupClipboardManager();

        renderAllQueue();

        renderFinishedList();

        /*
         * Beri sedikit waktu agar UI selesai dibuat sebelum
         * memeriksa download yang mungkin masih berjalan.
         */
        handler.postDelayed(
                this::recoverDownloadsAfterRestart,
                800
        );
    }


    @Override
    protected void onResume() {
        super.onResume();

        /*
         * Auto clipboard hanya aktif ketika Activity sedang
         * berada di foreground.
         */
        registerClipboardListener();
    }


    @Override
    protected void onPause() {
        unregisterClipboardListener();

        /*
         * Simpan state setiap kali aplikasi masuk background.
         */
        savePersistentState();

        super.onPause();
    }


    // ============================================================
    // UI
    // ============================================================

    private void buildUi() {

        FrameLayout root = new FrameLayout(this);

        root.setBackgroundColor(
                Color.rgb(246, 247, 249)
        );


        pageContainer = new FrameLayout(this);

        FrameLayout.LayoutParams pageParams =
                new FrameLayout.LayoutParams(
                        -1,
                        -1
                );

        pageParams.bottomMargin = 78;

        root.addView(
                pageContainer,
                pageParams
        );


        tabPage = buildTabPage();

        progressPage = buildProgressPage();

        finishedPage = buildFinishedPage();


        pageContainer.addView(
                tabPage,
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


        LinearLayout bottom = new LinearLayout(this);

        bottom.setOrientation(
                LinearLayout.HORIZONTAL
        );

        bottom.setGravity(
                Gravity.CENTER
        );

        bottom.setPadding(
                8,
                5,
                8,
                5
        );

        bottom.setBackgroundColor(
                Color.WHITE
        );


        tabNav = navButton(
                "▣",
                "Download"
        );

        progressNav = navButton(
                "↓",
                "Progress"
        );

        finishedNav = navButton(
                "✓",
                "Finished"
        );


        bottom.addView(
                tabNav,
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


        FrameLayout.LayoutParams bottomParams =
                new FrameLayout.LayoutParams(
                        -1,
                        78,
                        Gravity.BOTTOM
                );

        root.addView(
                bottom,
                bottomParams
        );


        tabNav.setOnClickListener(
                v -> showPage(0)
        );

        progressNav.setOnClickListener(
                v -> showPage(1)
        );

        finishedNav.setOnClickListener(
                v -> showPage(2)
        );


        setContentView(root);


        webView = new WebView(this);

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


        showPage(0);
    }


    private LinearLayout buildTabPage() {

        LinearLayout page =
                new LinearLayout(this);

        page.setOrientation(
                LinearLayout.VERTICAL
        );

        page.setPadding(
                22,
                24,
                22,
                18
        );


        LinearLayout header =
                new LinearLayout(this);

        header.setGravity(
                Gravity.CENTER_VERTICAL
        );


        TextView title =
                text(
                        "Video Downloader",
                        23
                );

        header.addView(
                title,
                new LinearLayout.LayoutParams(
                        0,
                        60,
                        1
                )
        );


        TextView gear =
                text(
                        "⚙",
                        24
                );

        gear.setGravity(
                Gravity.CENTER
        );

        header.addView(
                gear,
                new LinearLayout.LayoutParams(
                        52,
                        60
                )
        );


        page.addView(header);


        /*
         * URL input
         */
        LinearLayout search =
                roundedBox(
                        Color.WHITE,
                        Color.rgb(225, 226, 230),
                        16,
                        1
                );

        search.setGravity(
                Gravity.CENTER_VERTICAL
        );


        TextView icon =
                text(
                        "⌕",
                        25
                );

        icon.setTextColor(
                Color.GRAY
        );


        search.addView(
                icon,
                new LinearLayout.LayoutParams(
                        38,
                        58
                )
        );


        EditText singleUrl =
                new EditText(this);

        input = singleUrl;

        singleUrl.setHint(
                "Search or Type URL"
        );

        singleUrl.setTextSize(15);

        singleUrl.setSingleLine(true);

        singleUrl.setBackgroundColor(
                Color.TRANSPARENT
        );


        search.addView(
                singleUrl,
                new LinearLayout.LayoutParams(
                        0,
                        58,
                        1
                )
        );


        TextView enter =
                text(
                        "↵",
                        22
                );

        enter.setGravity(
                Gravity.CENTER
        );


        search.addView(
                enter,
                new LinearLayout.LayoutParams(
                        38,
                        58
                )
        );


        page.addView(
                search,
                marginParams(
                        0,
                        8,
                        0,
                        12
                )
        );


        /*
         * Auto clipboard switch
         */
        LinearLayout clipboardCard =
                roundedBox(
                        Color.WHITE,
                        Color.rgb(225, 226, 230),
                        16,
                        1
                );

        clipboardCard.setGravity(
                Gravity.CENTER_VERTICAL
        );

        clipboardCard.setPadding(
                14,
                6,
                8,
                6
        );


        LinearLayout clipboardText =
                new LinearLayout(this);

        clipboardText.setOrientation(
                LinearLayout.VERTICAL
        );


        TextView clipboardTitle =
                text(
                        "Auto Detect Clipboard",
                        14
                );

        clipboardTitle.setTypeface(
                null,
                android.graphics.Typeface.BOLD
        );


        TextView clipboardSub =
                text(
                        "Otomatis masukkan link TikTok",
                        11
                );

        clipboardSub.setTextColor(
                Color.GRAY
        );


        clipboardText.addView(
                clipboardTitle
        );

        clipboardText.addView(
                clipboardSub
        );


        clipboardCard.addView(
                clipboardText,
                new LinearLayout.LayoutParams(
                        0,
                        58,
                        1
                )
        );


        autoClipboardButton =
                new Button(this);

        autoClipboardButton.setAllCaps(false);

        autoClipboardButton.setTextSize(12);


        clipboardCard.addView(
                autoClipboardButton,
                new LinearLayout.LayoutParams(
                        78,
                        48
                )
        );


        autoClipboardButton.setOnClickListener(
                v -> toggleAutoClipboard()
        );


        page.addView(
                clipboardCard,
                marginParams(
                        0,
                        0,
                        0,
                        12
                )
        );


        /*
         * Platform display
         */
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


        for (int r = 0; r < 2; r++) {

            LinearLayout row =
                    new LinearLayout(this);

            row.setGravity(
                    Gravity.CENTER
            );


            for (int c = 0; c < 4; c++) {

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
                                22
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
                                        70 + idx * 8,
                                        120 + idx * 4,
                                        190
                                ),
                                50
                        )
                );


                item.addView(
                        circle,
                        new LinearLayout.LayoutParams(
                                48,
                                48
                        )
                );


                TextView label =
                        text(
                                platforms[idx][1],
                                12
                        );

                label.setGravity(
                        Gravity.CENTER
                );


                item.addView(
                        label,
                        new LinearLayout.LayoutParams(
                                82,
                                36
                        )
                );


                row.addView(
                        item,
                        new LinearLayout.LayoutParams(
                                0,
                                92,
                                1
                        )
                );
            }


            grid.addView(
                    row,
                    new LinearLayout.LayoutParams(
                            -1,
                            92
                    )
            );
        }


        page.addView(
                grid,
                marginParams(
                        0,
                        4,
                        0,
                        12
                )
        );


        Button how =
                button(
                        "How to Download?   ›"
                );

        how.setTextSize(14);


        page.addView(
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


        page.addView(
                feedback,
                new LinearLayout.LayoutParams(
                        -1,
                        42
                )
        );


        Space spacer =
                new Space(this);

        page.addView(
                spacer,
                new LinearLayout.LayoutParams(
                        1,
                        0,
                        1
                )
        );


        /*
         * Enter key
         */
        singleUrl.setOnEditorActionListener(
                (v, actionId, event) -> {

                    String u =
                            singleUrl
                                    .getText()
                                    .toString()
                                    .trim();


                    if (!u.isEmpty()) {

                        addUrlToQueue(
                                u,
                                true
                        );

                        singleUrl.setText("");

                        showPage(1);
                    }

                    return true;
                }
        );


        search.setOnClickListener(
                v -> singleUrl.requestFocus()
        );


        updateAutoClipboardButton();


        return page;
    }


    private LinearLayout buildProgressPage() {

        LinearLayout page =
                new LinearLayout(this);

        page.setOrientation(
                LinearLayout.VERTICAL
        );

        page.setPadding(
                22,
                24,
                22,
                18
        );


        TextView title =
                text(
                        "Progress",
                        23
                );

        page.addView(
                title,
                new LinearLayout.LayoutParams(
                        -1,
                        60
                )
        );


        Button start =
                button(
                        "Mulai Semua"
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


        ScrollView scroll =
                new ScrollView(this);


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
                        -1,
                        1
                )
        );


        progress =
                text(
                        "0 / 0",
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


    private LinearLayout buildFinishedPage() {

        LinearLayout page =
                new LinearLayout(this);

        page.setOrientation(
                LinearLayout.VERTICAL
        );

        page.setPadding(
                22,
                24,
                22,
                18
        );


        TextView title =
                text(
                        "Finished",
                        23
                );

        page.addView(
                title,
                new LinearLayout.LayoutParams(
                        -1,
                        60
                )
        );


        ScrollView scroll =
                new ScrollView(this);


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
    // BASIC UI HELPERS
    // ============================================================

    private FrameLayout.LayoutParams matchParams() {
        return new FrameLayout.LayoutParams(
                -1,
                -1
        );
    }


    private LinearLayout.LayoutParams marginParams(
            int l,
            int t,
            int r,
            int b
    ) {

        LinearLayout.LayoutParams p =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );

        p.setMargins(
                l,
                t,
                r,
                b
        );

        return p;
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


    private android.graphics.drawable.GradientDrawable round(
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


    private android.graphics.drawable.GradientDrawable round(
            int fill,
            int radius,
            int stroke,
            int strokeWidth
    ) {

        android.graphics.drawable.GradientDrawable d =
                new android.graphics.drawable.GradientDrawable();

        d.setColor(fill);

        d.setCornerRadius(radius);

        if (strokeWidth > 0) {
            d.setStroke(
                    strokeWidth,
                    stroke
            );
        }

        return d;
    }


    private Button navButton(
            String icon,
            String label
    ) {

        Button b =
                new Button(this);

        b.setText(
                icon + "\n" + label
        );

        b.setTextSize(11);

        b.setAllCaps(false);

        b.setGravity(
                Gravity.CENTER
        );

        b.setBackgroundColor(
                Color.TRANSPARENT
        );

        return b;
    }


    private void showPage(int page) {

        if (tabPage == null) {
            return;
        }


        tabPage.setVisibility(
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


        if (tabNav != null) {

            tabNav.setTextColor(
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
    // CLIPBOARD
    // ============================================================

    private void setupClipboardManager() {

        clipboardManager =
                (ClipboardManager)
                        getSystemService(
                                Context.CLIPBOARD_SERVICE
                        );
    }


    private void registerClipboardListener() {

        if (!autoClipboardEnabled) {
            return;
        }

        if (clipboardManager == null) {
            setupClipboardManager();
        }

        if (clipboardManager == null) {
            return;
        }

        if (clipboardListenerRegistered) {
            return;
        }


        /*
         * Jangan langsung memasukkan clipboard lama ketika
         * aplikasi baru dibuka.
         *
         * Kita hanya menyimpan nilainya sebagai baseline.
         * Link berikutnya yang baru disalin akan memicu listener.
         */
        lastClipboardText =
                getClipboardText();


        clipboardManager.addPrimaryClipChangedListener(
                clipboardListener
        );


        clipboardListenerRegistered = true;
    }


    private void unregisterClipboardListener() {

        if (clipboardManager == null) {
            return;
        }

        if (!clipboardListenerRegistered) {
            return;
        }


        clipboardManager.removePrimaryClipChangedListener(
                clipboardListener
        );


        clipboardListenerRegistered = false;
    }


    private final ClipboardManager.OnPrimaryClipChangedListener clipboardListener =
            () -> {

                if (!autoClipboardEnabled) {
                    return;
                }


                /*
                 * Hanya diproses ketika Activity sedang aktif.
                 */
                if (isFinishing()) {
                    return;
                }


                String text =
                        getClipboardText();


                if (text == null || text.isEmpty()) {
                    return;
                }


                if (text.equals(lastClipboardText)) {
                    return;
                }


                lastClipboardText = text;


                String tiktokUrl =
                        extractTikTokUrl(text);


                if (tiktokUrl == null) {
                    return;
                }


                /*
                 * Tambahkan ke queue.
                 */
                boolean added =
                        addUrlToQueue(
                                tiktokUrl,
                                false
                        );


                if (added) {

                    Toast.makeText(
                            MainActivity.this,
                            "Link TikTok masuk ke antrean",
                            Toast.LENGTH_SHORT
                    ).show();


                    showPage(1);
                }
            };


    private String getClipboardText() {

        try {

            if (clipboardManager == null) {
                return "";
            }


            if (!clipboardManager.hasPrimaryClip()) {
                return "";
            }


            ClipData clip =
                    clipboardManager.getPrimaryClip();


            if (clip == null ||
                    clip.getItemCount() == 0) {

                return "";
            }


            CharSequence text =
                    clip.getItemAt(0)
                            .coerceToText(this);


            return text == null
                    ? ""
                    : text.toString().trim();

        } catch (Exception e) {

            return "";
        }
    }


    /*
     * Menerima:
     *
     * https://www.tiktok.com/...
     * https://vt.tiktok.com/...
     * https://vm.tiktok.com/...
     *
     * Kalau clipboard berisi kalimat yang mengandung URL TikTok,
     * URL TikTok tersebut juga dapat diambil.
     */
    private String extractTikTokUrl(String text) {

        if (text == null) {
            return null;
        }


        String value =
                text.trim();


        if (value.isEmpty()) {
            return null;
        }


        String[] parts =
                value.split("\\s+");


        for (String part : parts) {

            String cleaned =
                    part.trim();


            cleaned =
                    cleaned.replace(
                            "(",
                            ""
                    );

            cleaned =
                    cleaned.replace(
                            ")",
                            ""
                    );

            cleaned =
                    cleaned.replace(
                            "[",
                            ""
                    );

            cleaned =
                    cleaned.replace(
                            "]",
                            ""
                    );

            cleaned =
                    cleaned.replace(
                            "\"",
                            ""
                    );

            cleaned =
                    cleaned.replace(
                            "'",
                            ""
                    );


            if (isTikTokUrl(cleaned)) {
                return cleaned;
            }
        }


        return null;
    }


    private boolean isTikTokUrl(String url) {

        if (url == null) {
            return false;
        }


        try {

            Uri uri =
                    Uri.parse(
                            url.trim()
                    );


            String scheme =
                    uri.getScheme();


            String host =
                    uri.getHost();


            if (scheme == null ||
                    host == null) {

                return false;
            }


            if (!scheme.equalsIgnoreCase("http") &&
                    !scheme.equalsIgnoreCase("https")) {

                return false;
            }


            host =
                    host.toLowerCase(
                            Locale.US
                    );


            return host.equals("tiktok.com") ||
                    host.endsWith(".tiktok.com");

        } catch (Exception e) {

            return false;
        }
    }


    private void toggleAutoClipboard() {

        autoClipboardEnabled =
                !autoClipboardEnabled;


        getPreferences(
                MODE_PRIVATE
        )
                .edit()
                .putBoolean(
                        KEY_AUTO_CLIPBOARD,
                        autoClipboardEnabled
                )
                .apply();


        if (autoClipboardEnabled) {

            /*
             * Baseline baru supaya clipboard lama tidak
             * langsung masuk.
             */
            lastClipboardText =
                    getClipboardText();


            registerClipboardListener();

        } else {

            unregisterClipboardListener();
        }


        updateAutoClipboardButton();


        Toast.makeText(
                this,
                autoClipboardEnabled
                        ? "Auto Clipboard ON"
                        : "Auto Clipboard OFF",
                Toast.LENGTH_SHORT
        ).show();
    }


    private void updateAutoClipboardButton() {

        if (autoClipboardButton == null) {
            return;
        }


        if (autoClipboardEnabled) {

            autoClipboardButton.setText(
                    "ON"
            );

            autoClipboardButton.setTextColor(
                    Color.WHITE
            );

            autoClipboardButton.setBackground(
                    round(
                            Color.rgb(
                                    37,
                                    99,
                                    235
                            ),
                            20
                    )
            );

        } else {

            autoClipboardButton.setText(
                    "OFF"
            );

            autoClipboardButton.setTextColor(
                    Color.DKGRAY
            );

            autoClipboardButton.setBackground(
                    round(
                            Color.rgb(
                                    230,
                                    231,
                                    235
                            ),
                            20
                    )
            );
        }
    }


    // ============================================================
    // QUEUE
    // ============================================================

    private boolean addUrlToQueue(
            String url,
            boolean showToast
    ) {

        if (!isTikTokUrl(url)) {

            if (showToast) {

                Toast.makeText(
                        this,
                        "URL bukan link TikTok",
                        Toast.LENGTH_SHORT
                ).show();
            }

            return false;
        }


        String normalized =
                normalizeTikTokUrl(url);


        /*
         * Anti duplikat.
         */
        for (String existing : urls) {

            if (normalizeTikTokUrl(existing)
                    .equalsIgnoreCase(normalized)) {

                if (showToast) {

                    Toast.makeText(
                            this,
                            "Link sudah ada di antrean",
                            Toast.LENGTH_SHORT
                    ).show();
                }

                return false;
            }
        }


        urls.add(normalized);

        statuses.add("Menunggu");

        captions.add("");

        downloadIds.add(-1L);


        renderQueueItem(
                urls.size() - 1
        );


        updateProgress();

        savePersistentState();


        if (showToast) {

            Toast.makeText(
                    this,
                    "URL ditambahkan ke antrean",
                    Toast.LENGTH_SHORT
            ).show();
        }


        return true;
    }


    private String normalizeTikTokUrl(String url) {

        if (url == null) {
            return "";
        }

        return url.trim();
    }


    private void renderAllQueue() {

        if (queue == null) {
            return;
        }


        queue.removeAllViews();


        for (int i = 0;
             i < urls.size();
             i++) {

            renderQueueItem(i);
        }


        updateProgress();
    }


    private void renderQueueItem(int index) {

        if (queue == null) {
            return;
        }

        if (index < 0 ||
                index >= urls.size()) {

            return;
        }


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


        LinearLayout.LayoutParams cp =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );

        cp.setMargins(
                0,
                0,
                0,
                10
        );


        queue.addView(
                card,
                cp
        );


        String status =
                getStatus(index);


        TextView statusView =
                text(
                        "#" +
                                (index + 1) +
                                "  " +
                                status,
                        16
                );


        statusView.setTypeface(
                null,
                android.graphics.Typeface.BOLD
        );


        TextView link =
                text(
                        urls.get(index),
                        12
                );

        link.setMaxLines(3);


        Button process =
                button(
                        "Proses Link Ini"
                );


        final int itemIndex =
                index;


        process.setOnClickListener(
                v -> startSingle(
                        itemIndex
                )
        );


        card.addView(
                statusView
        );

        card.addView(
                link
        );

        card.addView(
                process
        );


        card.setTag(
                statusView
        );
    }


    private String getStatus(int index) {

        if (index < 0 ||
                index >= statuses.size()) {

            return "Menunggu";
        }


        String value =
                statuses.get(index);


        if (value == null ||
                value.trim().isEmpty()) {

            return "Menunggu";
        }


        return value;
    }


    private void setStatus(
            int index,
            String value
    ) {

        if (index < 0 ||
                index >= statuses.size()) {

            return;
        }


        statuses.set(
                index,
                value
        );


        if (queue != null &&
                index < queue.getChildCount()) {

            View card =
                    queue.getChildAt(index);


            Object tag =
                    card.getTag();


            if (tag instanceof TextView) {

                ((TextView) tag)
                        .setText(
                                "#" +
                                        (index + 1) +
                                        "  " +
                                        value
                        );
            }
        }


        savePersistentState();
    }


    private void updateProgress() {

        int completed = 0;


        for (String status : statuses) {

            if (status != null &&
                    status.equalsIgnoreCase("Selesai")) {

                completed++;
            }
        }


        if (progress != null) {

            progress.setText(
                    "Progress: " +
                            completed +
                            " / " +
                            urls.size()
            );
        }
    }


    // ============================================================
    // START PROCESS
    // ============================================================

    private void startSingle(int index) {

        if (index < 0 ||
                index >= urls.size()) {

            return;
        }


        handler.removeCallbacksAndMessages(
                null
        );


        currentIndex =
                index;


        processing = true;

        submitClicked = false;

        downloadStarted = false;


        currentCaption =
                getCaption(index);


        currentDownloadId =
                getDownloadId(index);


        handledDownloadUrls.clear();


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


        /*
         * Jika sudah ada DownloadManager ID,
         * berarti proses download pernah dimulai.
         */
        if (currentDownloadId > 0) {

            DownloadManager manager =
                    (DownloadManager)
                            getSystemService(
                                    Context.DOWNLOAD_SERVICE
                            );


            if (manager != null) {

                if (isDownloadSuccessful(
                        manager,
                        currentDownloadId
                )) {

                    setStatus(
                            index,
                            "Selesai"
                    );

                    processing = false;

                    addFinishedItem(
                            index,
                            getCaption(index)
                    );

                    updateProgress();

                    savePersistentState();

                    return;
                }
            }
        }


        /*
         * Untuk proses normal:
         * kembali ke SaveTikTok.
         */
        currentDownloadId = -1L;

        setDownloadId(
                index,
                -1L
        );


        webView.loadUrl(
                SAVE_URL
        );


        savePersistentState();
    }


    private void startAll() {

        if (urls.isEmpty()) {

            Toast.makeText(
                    this,
                    "Belum ada URL di antrean.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }


        if (processing) {

            Toast.makeText(
                    this,
                    "Masih ada download yang sedang diproses.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }


        /*
         * Cari item pertama yang belum selesai.
         */
        for (int i = 0;
             i < urls.size();
             i++) {

            String status =
                    getStatus(i);


            if (!status.equalsIgnoreCase("Selesai")) {

                startSingle(i);

                showPage(1);

                return;
            }
        }


        Toast.makeText(
                this,
                "Semua antrean sudah selesai.",
                Toast.LENGTH_SHORT
        ).show();
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
                "Mozilla/5.0 (Linux; Android 12) " +
                        "AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) " +
                        "Chrome/140.0 Mobile Safari/537.36"
        );


        CookieManager
                .getInstance()
                .setAcceptCookie(true);


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


    private boolean isSaveTikTokPage(
            String url
    ) {

        try {

            String host =
                    Uri.parse(url)
                            .getHost();


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
    // SAVETIKTOK INPUT
    // ============================================================

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

            processing = false;

            savePersistentState();

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


    private void injectTikTokUrl(
            String tiktokUrl
    ) {

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

                        "return p.includes('tautan')" +
                        "||p.includes('tiktok')" +
                        "||p.includes('link')" +
                        "||p.includes('url')" +
                        "||el.type==='url';" +

                        "});" +

                        "if(!field)return 'NO_FIELD';" +

                        "var proto=" +
                        "field instanceof HTMLTextAreaElement?" +
                        "HTMLTextAreaElement.prototype:" +
                        "HTMLInputElement.prototype;" +

                        "var desc=" +
                        "Object.getOwnPropertyDescriptor(proto,'value');" +

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
                        "input[type=button],a'" +
                        "));" +

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


                        savePersistentState();


                        handler.postDelayed(
                                () ->
                                        findMp4HdButton(0),
                                2000
                        );

                    } else {

                        submitClicked = false;


                        webStatus.setText(
                                "WebView: URL belum berhasil dikirim, mencoba lagi..."
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
    // MP4 HD
    // ============================================================

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
                    "WebView: tombol MP4 HD tidak ditemukan"
            );


            setStatus(
                    currentIndex,
                    "MP4 HD tidak ditemukan"
            );


            processing = false;

            savePersistentState();

            return;
        }


        /*
         * Selector yang sudah kita temukan:
         *
         * #download-result
         *   .video-data
         *   .tik-video
         *
         * Caption:
         *
         * .tik-left
         * .thumbnail
         * .content
         * .clearfix
         * h3
         */
        String js =
                "(function(){" +

                        "var root=" +
                        "document.querySelector('#download-result');" +

                        "if(!root)" +
                        "return JSON.stringify({state:'WAIT'});" +

                        "var card=" +
                        "root.querySelector('.video-data .tik-video');" +

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

                        "var hd=" +
                        "buttons.find(function(el){" +

                        "var t=(" +
                        "el.innerText||" +
                        "el.textContent||''" +
                        ").replace(/\\s+/g,' ')" +
                        ".trim().toLowerCase();" +

                        "return t==='unduh mp4 hd'" +
                        "||t==='download mp4 hd';" +

                        "});" +

                        "if(!hd)" +
                        "return JSON.stringify({state:'WAIT'});" +

                        "var caption=" +
                        "captionEl?" +
                        "(captionEl.innerText||" +
                        "captionEl.textContent||'')" +
                        ".trim():" +
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
                                        : String.valueOf(parsed);


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
                                    "WebView: caption kosong, MP4 HD tidak diproses"
                            );


                            setStatus(
                                    currentIndex,
                                    "Caption kosong"
                            );


                            processing = false;

                            savePersistentState();

                            return;
                        }


                        captions.set(
                                currentIndex,
                                currentCaption
                        );


                        webStatus.setText(
                                "WebView: caption ditemukan → " +
                                        currentCaption
                        );


                        setStatus(
                                currentIndex,
                                "Caption ditemukan"
                        );


                        savePersistentState();


                        /*
                         * Klik MP4 HD di card yang sama.
                         */
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

                                        "var hd=" +
                                        "buttons.find(function(el){" +

                                        "var t=(" +
                                        "el.innerText||" +
                                        "el.textContent||''" +
                                        ").replace(/\\s+/g,' ')" +
                                        ".trim().toLowerCase();" +

                                        "return t==='unduh mp4 hd'" +
                                        "||t==='download mp4 hd';" +

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
                                                "WebView: MP4 HD dipilih, menunggu download..."
                                        );


                                        setStatus(
                                                currentIndex,
                                                "MP4 HD dipilih"
                                        );


                                        savePersistentState();

                                    } else {

                                        webStatus.setText(
                                                "WebView: tombol MP4 HD gagal diklik"
                                        );


                                        setStatus(
                                                currentIndex,
                                                "Gagal klik MP4 HD"
                                        );


                                        processing = false;

                                        savePersistentState();
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

                        savePersistentState();
                    }
                }
        );
    }


    // ============================================================
    // WEB DOWNLOAD
    // ============================================================

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

            webStatus.setText(
                    "WebView: download ditolak karena bukan video"
            );

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


        /*
         * SaveTikTok pada sebagian download MP4 HD
         * mengirim application/octet-stream / .bin.
         */
        if (isBin &&
                submitClicked) {

            looksLikeVideo = true;
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

            handledDownloadUrls.remove(
                    first
            );
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


    // ============================================================
    // ENQUEUE DOWNLOAD
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
             * Folder tetap sama seperti baseline:
             *
             * Download/
             *   TikTokDownloadManager/
             *      001/
             *         video.mp4
             *         caption.txt
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


            request.setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    folder +
                            "/" +
                            filename
            );


            currentDownloadId =
                    manager.enqueue(
                            request
                    );


            int completedIndex =
                    currentIndex;


            setDownloadId(
                    completedIndex,
                    currentDownloadId
            );


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


            saveCaptionFile(
                    completedIndex
            );


            savePersistentState();


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


            savePersistentState();
        }
    }


    // ============================================================
    // CAPTION FILE
    // ============================================================

    private void saveCaptionFile(
            int index
    ) {

        try {

            String caption =
                    currentCaption == null
                            ? ""
                            : currentCaption.trim();


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
                        Environment.DIRECTORY_DOWNLOADS +
                                "/TikTokDownloadManager/" +
                                String.format(
                                        Locale.US,
                                        "%03d",
                                        index + 1
                                )
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
                                    (
                                            caption.isEmpty()
                                                    ? "Caption tidak ditemukan."
                                                    : caption
                                    )
                                            .getBytes(
                                                    java.nio.charset.StandardCharsets.UTF_8
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
                                                Environment.DIRECTORY_DOWNLOADS
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
                                new java.io.FileOutputStream(file)
                ) {

                    out.write(
                            (
                                    caption.isEmpty()
                                            ? "Caption tidak ditemukan."
                                            : caption
                            )
                                    .getBytes("UTF-8")
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
    // DOWNLOAD MONITOR
    // ============================================================

    private void waitForDownloadCompletion(
            DownloadManager manager,
            long downloadId,
            int completedIndex,
            int attempt
    ) {

        if (attempt >= MAX_DOWNLOAD_CHECKS) {

            /*
             * Jangan langsung menganggap selesai.
             * Status tetap "Download berjalan".
             */
            setStatus(
                    completedIndex,
                    "Download berjalan"
            );


            savePersistentState();


            handler.postDelayed(
                    () ->
                            waitForDownloadCompletion(
                                    manager,
                                    downloadId,
                                    completedIndex,
                                    0
                            ),
                    DOWNLOAD_CHECK_INTERVAL
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
                                        DownloadManager.COLUMN_STATUS
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


                    savePersistentState();


                    handler.postDelayed(
                            () ->
                                    finishCurrentAndNext(
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
                                            DownloadManager.COLUMN_REASON
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


                    savePersistentState();


                    return;
                }


                int downloaded =
                        cursor.getInt(
                                cursor.getColumnIndexOrThrow(
                                        DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR
                                )
                        );


                int total =
                        cursor.getInt(
                                cursor.getColumnIndexOrThrow(
                                        DownloadManager.COLUMN_TOTAL_SIZE_BYTES
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
                                            ) /
                                                    total
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


                savePersistentState();
            }


        } catch (Exception ignored) {
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
    // FINISH
    // ============================================================

    private void finishCurrentAndNext(
            int completedIndex
    ) {

        processing = false;

        submitClicked = false;

        downloadStarted = false;

        currentDownloadId = -1L;


        /*
         * Finished list.
         */
        addFinishedItem(
                completedIndex,
                getCaption(completedIndex)
        );


        currentCaption = "";


        savePersistentState();


        updateProgress();


        int next =
                completedIndex + 1;


        if (next < urls.size()) {

            /*
             * Cari berikutnya yang belum selesai.
             */
            int nextIndex =
                    findNextPendingIndex(
                            next
                    );


            if (nextIndex >= 0) {

                startSingle(
                        nextIndex
                );

            } else {

                currentIndex = -1;

                webStatus.setText(
                        "WebView: semua antrean selesai"
                );

                Toast.makeText(
                        this,
                        "Semua antrean sudah diproses.",
                        Toast.LENGTH_LONG
                ).show();
            }


        } else {

            currentIndex = -1;


            webStatus.setText(
                    "WebView: semua antrean selesai"
            );


            Toast.makeText(
                    this,
                    "Semua antrean sudah diproses.",
                    Toast.LENGTH_LONG
            ).show();
        }


        savePersistentState();
    }


    private int findNextPendingIndex(
            int start
    ) {

        for (int i = start;
             i < urls.size();
             i++) {

            String status =
                    getStatus(i);


            if (!status.equalsIgnoreCase(
                    "Selesai"
            )) {

                return i;
            }
        }


        return -1;
    }


    // ============================================================
    // FINISHED UI
    // ============================================================

    private void renderFinishedList() {

        if (finishedList == null) {
            return;
        }


        finishedList.removeAllViews();


        finishedIndexes.clear();


        for (int i = 0;
             i < urls.size();
             i++) {

            if (getStatus(i)
                    .equalsIgnoreCase(
                            "Selesai"
                    )) {

                addFinishedItem(
                        i,
                        getCaption(i)
                );
            }
        }
    }


    private void addFinishedItem(
            int index,
            String caption
    ) {

        if (finishedList == null) {
            return;
        }


        /*
         * Hindari duplikat card.
         */
        if (finishedIndexes.contains(
                index
        )) {

            return;
        }


        finishedIndexes.add(
                index
        );


        LinearLayout card =
                new LinearLayout(this);

        card.setOrientation(
                LinearLayout.VERTICAL
        );

        card.setPadding(
                14,
                12,
                14,
                12
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


        LinearLayout.LayoutParams cp =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );


        cp.setMargins(
                0,
                0,
                0,
                10
        );


        finishedList.addView(
                card,
                cp
        );


        TextView head =
                text(
                        "✓  Video " +
                                String.format(
                                        Locale.US,
                                        "%03d",
                                        index + 1
                                ),
                        16
                );


        head.setTypeface(
                null,
                android.graphics.Typeface.BOLD
        );


        card.addView(
                head
        );


        TextView cap =
                text(
                        caption == null ||
                                caption.isEmpty()
                                ? "Tanpa caption"
                                : caption,
                        13
                );


        cap.setMaxLines(3);


        card.addView(
                cap
        );


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
    }


    // ============================================================
    // PERSISTENCE
    // ============================================================

    private SharedPreferences getPrefs() {

        return getSharedPreferences(
                PREF_NAME,
                MODE_PRIVATE
        );
    }


    private void savePersistentState() {

        if (restoringState) {
            return;
        }


        try {

            JSONArray array =
                    new JSONArray();


            for (int i = 0;
                 i < urls.size();
                 i++) {


                JSONObject item =
                        new JSONObject();


                item.put(
                        "url",
                        urls.get(i)
                );


                item.put(
                        "status",
                        getStatus(i)
                );


                item.put(
                        "caption",
                        getCaption(i)
                );


                item.put(
                        "downloadId",
                        getDownloadId(i)
                );


                array.put(
                        item
                );
            }


            getPrefs()
                    .edit()
                    .putString(
                            KEY_QUEUE,
                            array.toString()
                    )
                    .putBoolean(
                            KEY_AUTO_CLIPBOARD,
                            autoClipboardEnabled
                    )
                    .apply();


        } catch (Exception ignored) {
        }
    }


    private void loadPersistentState() {

        restoringState = true;


        try {

            autoClipboardEnabled =
                    getPrefs().getBoolean(
                            KEY_AUTO_CLIPBOARD,
                            true
                    );


            String saved =
                    getPrefs().getString(
                            KEY_QUEUE,
                            ""
                    );


            urls.clear();

            statuses.clear();

            captions.clear();

            downloadIds.clear();


            if (saved != null &&
                    !saved.trim().isEmpty()) {


                JSONArray array =
                        new JSONArray(
                                saved
                        );


                for (int i = 0;
                     i < array.length();
                     i++) {


                    JSONObject item =
                            array.getJSONObject(i);


                    String url =
                            item.optString(
                                    "url",
                                    ""
                            ).trim();


                    if (url.isEmpty()) {
                        continue;
                    }


                    urls.add(
                            url
                    );


                    statuses.add(
                            item.optString(
                                    "status",
                                    "Menunggu"
                            )
                    );


                    captions.add(
                            item.optString(
                                    "caption",
                                    ""
                            )
                    );


                    downloadIds.add(
                            item.optLong(
                                    "downloadId",
                                    -1L
                            )
                    );
                }
            }


        } catch (Exception e) {

            urls.clear();

            statuses.clear();

            captions.clear();

            downloadIds.clear();
        }


        restoringState = false;
    }


    private String getCaption(
            int index
    ) {

        if (index < 0 ||
                index >= captions.size()) {

            return "";
        }


        String value =
                captions.get(index);


        return value == null
                ? ""
                : value;
    }


    private long getDownloadId(
            int index
    ) {

        if (index < 0 ||
                index >= downloadIds.size()) {

            return -1L;
        }


        Long value =
                downloadIds.get(index);


        return value == null
                ? -1L
                : value;
    }


    private void setDownloadId(
            int index,
            long id
    ) {

        if (index < 0 ||
                index >= downloadIds.size()) {

            return;
        }


        downloadIds.set(
                index,
                id
        );


        savePersistentState();
    }


    // ============================================================
    // RECOVER DOWNLOAD AFTER APP RESTART
    // ============================================================

    private void recoverDownloadsAfterRestart() {

        if (urls.isEmpty()) {
            return;
        }


        DownloadManager manager =
                (DownloadManager)
                        getSystemService(
                                Context.DOWNLOAD_SERVICE
                        );


        if (manager == null) {
            return;
        }


        /*
         * Cari download yang sebelumnya sudah dibuat
         * oleh DownloadManager.
         */
        for (int i = 0;
             i < urls.size();
             i++) {


            long id =
                    getDownloadId(i);


            if (id <= 0) {
                continue;
            }


            int status =
                    getDownloadStatus(
                            manager,
                            id
                    );


            if (status ==
                    DownloadManager.STATUS_SUCCESSFUL) {


                statuses.set(
                        i,
                        "Selesai"
                );


            } else if (status ==
                    DownloadManager.STATUS_FAILED) {


                /*
                 * Kalau sebelumnya gagal, jangan otomatis
                 * mengulang download tanpa perintah user.
                 */
                statuses.set(
                        i,
                        "Gagal download"
                );


            } else if (
                    status ==
                            DownloadManager.STATUS_PENDING ||
                    status ==
                            DownloadManager.STATUS_RUNNING ||
                    status ==
                            DownloadManager.STATUS_PAUSED
            ) {


                /*
                 * Download masih hidup di DownloadManager.
                 */
                statuses.set(
                        i,
                        "Download berjalan"
                );


                if (!processing) {

                    currentIndex =
                            i;

                    currentDownloadId =
                            id;

                    processing = true;

                    downloadStarted = true;

                    submitClicked = true;


                    webStatus.setText(
                            "Download dipulihkan..."
                    );


                    waitForDownloadCompletion(
                            manager,
                            id,
                            i,
                            0
                    );
                }


                break;
            }
        }


        savePersistentState();

        renderAllQueue();

        renderFinishedList();
    }


    private int getDownloadStatus(
            DownloadManager manager,
            long id
    ) {

        DownloadManager.Query query =
                new DownloadManager.Query();


        query.setFilterById(id);


        try (
                android.database.Cursor cursor =
                        manager.query(query)
        ) {

            if (cursor != null &&
                    cursor.moveToFirst()) {

                return cursor.getInt(
                        cursor.getColumnIndexOrThrow(
                                DownloadManager.COLUMN_STATUS
                        )
                );
            }

        } catch (Exception ignored) {
        }


        return -1;
    }


    private boolean isDownloadSuccessful(
            DownloadManager manager,
            long id
    ) {

        return getDownloadStatus(
                manager,
                id
        ) == DownloadManager.STATUS_SUCCESSFUL;
    }


    // ============================================================
    // CLEAR QUEUE
    // ============================================================

    private void clearQueue() {

        handler.removeCallbacksAndMessages(
                null
        );


        /*
         * Jangan hapus DownloadManager download yang sudah
         * berjalan. Tombol clear hanya menghapus data aplikasi.
         */
        urls.clear();

        statuses.clear();

        captions.clear();

        downloadIds.clear();


        currentIndex = -1;

        processing = false;

        submitClicked = false;

        downloadStarted = false;

        currentCaption = "";

        currentDownloadId = -1L;


        handledDownloadUrls.clear();


        if (queue != null) {
            queue.removeAllViews();
        }


        if (finishedList != null) {
            finishedList.removeAllViews();
        }


        finishedIndexes.clear();


        input.setText("");


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


        savePersistentState();
    }


    // ============================================================
    // TEXT / BUTTON
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


        button.setText(
                value
        );


        button.setAllCaps(
                false
        );


        return button;
    }


    // ============================================================
    // STORAGE
    // ============================================================

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


    // ============================================================
    // DESTROY / BACK
    // ============================================================

    @Override
    protected void onDestroy() {

        /*
         * Simpan dulu sebelum Activity dihancurkan.
         */
        savePersistentState();


        unregisterClipboardListener();


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

            /*
             * Jangan menghapus queue ketika user keluar.
             */
            savePersistentState();

            super.onBackPressed();
        }
    }
}
