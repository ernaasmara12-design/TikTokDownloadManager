package com.example.tiktokdownloadmanager;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {

    private static final String SAVE_URL =
            "https://savetiktok.to/id";

    private static final int REQ_STORAGE = 501;

    private static final int MAX_INPUT_CHECKS = 20;
    private static final int MAX_MP4_HD_CHECKS = 30;

    private EditText input;
    private LinearLayout queue;
    private LinearLayout finishedList;

    private TextView progressText;
    private TextView webStatus;

    private WebView webView;

    private FrameLayout pageContainer;

    private LinearLayout downloadPage;
    private LinearLayout progressPage;
    private LinearLayout finishedPage;

    private TextView navDownload;
    private TextView navProgress;
    private TextView navFinished;

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
            new Handler(Looper.getMainLooper());

    // =========================================================
    // CLIPBOARD
    // =========================================================

    private ClipboardManager clipboardManager;

    private ClipboardManager.OnPrimaryClipChangedListener
            clipboardListener;

    private boolean clipboardListenerActive = false;

    private String lastClipboardText = "";

    private final LinkedHashSet<String>
            clipboardAddedUrls =
            new LinkedHashSet<>();

    // =========================================================
    // ACTIVITY
    // =========================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prepareWindow();

        clipboardManager =
                (ClipboardManager)
                        getSystemService(
                                CLIPBOARD_SERVICE
                        );

        buildUi();

        requestStorageIfNeeded();

        handleShareIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        setIntent(intent);

        handleShareIntent(intent);
    }

    // =========================================================
    // SHARE
    // =========================================================

    private void handleShareIntent(Intent intent) {

        if (intent == null) {
            return;
        }

        if (!Intent.ACTION_SEND.equals(
                intent.getAction()
        )) {
            return;
        }

        String text =
                intent.getStringExtra(
                        Intent.EXTRA_TEXT
                );

        String tikTokUrl =
                extractTikTokUrl(text);

        if (tikTokUrl == null) {

            Toast.makeText(
                    this,
                    "Link TikTok tidak ditemukan.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        addSharedUrl(tikTokUrl);
    }

    private void addSharedUrl(String url) {

        if (url == null ||
                url.trim().isEmpty()) {
            return;
        }

        url = url.trim();

        for (String existing : urls) {

            if (existing.equalsIgnoreCase(url)) {

                Toast.makeText(
                        this,
                        "Link sudah ada di antrean.",
                        Toast.LENGTH_SHORT
                ).show();

                showPage(1);
                return;
            }
        }

        int newIndex = urls.size();

        urls.add(url);

        clipboardAddedUrls.add(url);

        appendUrlToInput(url);

        addQueueItem(
                newIndex,
                url
        );

        updateProgress();

        webStatus.setText(
                "Share: link TikTok masuk antrean"
        );

        showPage(1);

        Toast.makeText(
                this,
                "Link TikTok masuk antrean.",
                Toast.LENGTH_SHORT
        ).show();

        if (!processing) {
            startSingle(newIndex);
        }
    }

    // =========================================================
    // WINDOW
    // =========================================================

    private void prepareWindow() {

        Window window = getWindow();

        window.setStatusBarColor(
                Color.rgb(246, 247, 249)
        );

        window.setNavigationBarColor(
                Color.WHITE
        );

        if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.M) {

            window.getDecorView()
                    .setSystemUiVisibility(
                            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                    );
        }
    }

    // =========================================================
    // RESUME / PAUSE
    // =========================================================

    @Override
    protected void onResume() {

        super.onResume();

        lastClipboardText =
                readClipboardText();

        startClipboardListener();
    }

    @Override
    protected void onPause() {

        stopClipboardListener();

        super.onPause();
    }

    @Override
    protected void onDestroy() {

        stopClipboardListener();

        handler.removeCallbacksAndMessages(null);

        if (webView != null) {

            webView.stopLoading();
            webView.destroy();
        }

        super.onDestroy();
    }

    // =========================================================
    // CLIPBOARD
    // =========================================================

    private void startClipboardListener() {

        if (clipboardManager == null ||
                clipboardListenerActive) {
            return;
        }

        clipboardListener = () -> {

            if (isFinishing()) {
                return;
            }

            if (Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.JELLY_BEAN_MR1) {

                if (isDestroyed()) {
                    return;
                }
            }

            String text =
                    readClipboardText();

            if (text == null) {
                text = "";
            }

            if (text.equals(
                    lastClipboardText
            )) {
                return;
            }

            lastClipboardText = text;

            String tikTokUrl =
                    extractTikTokUrl(text);

            if (tikTokUrl == null) {
                return;
            }

            String finalUrl =
                    tikTokUrl;

            runOnUiThread(
                    () -> addClipboardUrl(finalUrl)
            );
        };

        clipboardManager
                .addPrimaryClipChangedListener(
                        clipboardListener
                );

        clipboardListenerActive = true;
    }

    private void stopClipboardListener() {

        if (clipboardManager == null ||
                !clipboardListenerActive) {
            return;
        }

        if (clipboardListener != null) {

            clipboardManager
                    .removePrimaryClipChangedListener(
                            clipboardListener
                    );
        }

        clipboardListener = null;
        clipboardListenerActive = false;
    }

    private String readClipboardText() {

        try {

            if (clipboardManager == null ||
                    !clipboardManager.hasPrimaryClip()) {
                return "";
            }

            ClipData clipData =
                    clipboardManager.getPrimaryClip();

            if (clipData == null ||
                    clipData.getItemCount() == 0) {
                return "";
            }

            CharSequence text =
                    clipData.getItemAt(0)
                            .coerceToText(this);

            return text == null
                    ? ""
                    : text.toString();

        } catch (Exception e) {

            return "";
        }
    }

    private String extractTikTokUrl(String text) {

        if (text == null) {
            return null;
        }

        String value =
                text.trim();

        if (value.isEmpty()) {
            return null;
        }

        Pattern pattern =
                Pattern.compile(
                        "https?://(?:www\\.|m\\.|vm\\.|vt\\.)?"
                                + "tiktok\\.com/[^\\s]+",
                        Pattern.CASE_INSENSITIVE
                );

        Matcher matcher =
                pattern.matcher(value);

        if (!matcher.find()) {
            return null;
        }

        String url =
                matcher.group();

        if (url == null) {
            return null;
        }

        while (
                url.endsWith(".") ||
                        url.endsWith(",") ||
                        url.endsWith("!") ||
                        url.endsWith("?") ||
                        url.endsWith(")") ||
                        url.endsWith("]") ||
                        url.endsWith("}")
        ) {

            url = url.substring(
                    0,
                    url.length() - 1
            );
        }

        return url;
    }

    private void addClipboardUrl(String url) {

        if (url == null ||
                url.trim().isEmpty()) {
            return;
        }

        url = url.trim();

        for (String existing : urls) {

            if (existing.equalsIgnoreCase(url)) {

                Toast.makeText(
                        this,
                        "Link sudah ada di antrean",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }
        }

        for (String existing :
                clipboardAddedUrls) {

            if (existing.equalsIgnoreCase(url)) {
                return;
            }
        }

        clipboardAddedUrls.add(url);

        int index = urls.size();

        urls.add(url);

        appendUrlToInput(url);

        addQueueItem(
                index,
                url
        );

        updateProgress();

        webStatus.setText(
                "Clipboard: link TikTok otomatis masuk antrean"
        );

        Toast.makeText(
                this,
                "Link TikTok ditambahkan ke antrean",
                Toast.LENGTH_SHORT
        ).show();

        showPage(1);

        if (!processing) {
            startSingle(index);
        }
    }

    private void appendUrlToInput(String url) {

        if (input == null) {
            return;
        }

        String current =
                input.getText()
                        .toString()
                        .trim();

        if (current.isEmpty()) {

            input.setText(url);

        } else {

            input.setText(
                    current + "\n" + url
            );
        }

        input.setSelection(
                input.getText().length()
        );
    }

    // =========================================================
    // UI
    // =========================================================

    private void buildUi() {

        FrameLayout root =
                new FrameLayout(this);

        root.setBackgroundColor(
                Color.rgb(246, 247, 249)
        );

        if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.R) {

            root.setOnApplyWindowInsetsListener(
                    (v, insets) -> {

                        android.graphics.Insets system =
                                insets.getInsets(
                                        WindowInsets.Type.systemBars()
                                );

                        v.setPadding(
                                0,
                                system.top,
                                0,
                                system.bottom
                        );

                        return insets;
                    }
            );

        } else {

            root.setPadding(
                    0,
                    dp(24),
                    0,
                    dp(24)
            );
        }

        pageContainer =
                new FrameLayout(this);

        root.addView(
                pageContainer,
                new FrameLayout.LayoutParams(
                        -1,
                        -1
                )
        );

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

        LinearLayout bottom =
                new LinearLayout(this);

        bottom.setOrientation(
                LinearLayout.HORIZONTAL
        );

        bottom.setGravity(
                Gravity.CENTER
        );

        bottom.setPadding(
                dp(8),
                dp(4),
                dp(8),
                dp(4)
        );

        bottom.setBackgroundColor(
                Color.WHITE
        );

        navDownload =
                navItem("⌂", "Download");

        navProgress =
                navItem("↓", "Progress");

        navFinished =
                navItem("✓", "Finished");

        bottom.addView(
                navDownload,
                new LinearLayout.LayoutParams(
                        0,
                        dp(68),
                        1
                )
        );

        bottom.addView(
                navProgress,
                new LinearLayout.LayoutParams(
                        0,
                        dp(68),
                        1
                )
        );

        bottom.addView(
                navFinished,
                new LinearLayout.LayoutParams(
                        0,
                        dp(68),
                        1
                )
        );

        FrameLayout.LayoutParams bottomParams =
                new FrameLayout.LayoutParams(
                        -1,
                        dp(68),
                        Gravity.BOTTOM
                );

        root.addView(
                bottom,
                bottomParams
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

        setContentView(root);

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

        showPage(0);
    }

    // =========================================================
    // DOWNLOAD PAGE
    // =========================================================

    private LinearLayout buildDownloadPage() {

        LinearLayout outer =
                new LinearLayout(this);

        outer.setOrientation(
                LinearLayout.VERTICAL
        );

        outer.setBackgroundColor(
                Color.rgb(246, 247, 249)
        );

        ScrollView scroll =
                new ScrollView(this);

        scroll.setFillViewport(true);

        LinearLayout page =
                new LinearLayout(this);

        page.setOrientation(
                LinearLayout.VERTICAL
        );

        page.setPadding(
                dp(20),
                dp(20),
                dp(20),
                dp(25)
        );

        TextView title =
                text(
                        "TikTok Download Manager",
                        25
                );

        title.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        page.addView(
                title,
                marginParams(0, 0, 0, 5)
        );

        TextView subtitle =
                text(
                        "Download video TikTok dengan mudah",
                        14
                );

        subtitle.setTextColor(
                Color.rgb(107, 114, 128)
        );

        page.addView(
                subtitle,
                marginParams(0, 0, 0, 20)
        );

        LinearLayout inputCard =
                roundedBox(
                        Color.WHITE,
                        Color.rgb(225, 226, 230),
                        16,
                        1
                );

        inputCard.setPadding(
                dp(14),
                dp(4),
                dp(14),
                dp(4)
        );

        input =
                new EditText(this);

        input.setHint(
                "Tempel link TikTok di sini"
        );

        input.setTextSize(15);

        input.setTextColor(
                Color.rgb(17, 24, 39)
        );

        input.setHintTextColor(
                Color.rgb(156, 163, 175)
        );

        input.setGravity(
                Gravity.TOP
        );

        input.setSingleLine(false);
        input.setMinLines(3);
        input.setMaxLines(6);

        input.setPadding(
                dp(2),
                dp(10),
                dp(2),
                dp(10)
        );

        input.setBackgroundColor(
                Color.TRANSPARENT
        );

        inputCard.addView(
                input,
                new LinearLayout.LayoutParams(
                        -1,
                        dp(100)
                )
        );

        page.addView(
                inputCard,
                marginParams(0, 0, 0, 12)
        );

        Button addButton =
                primaryButton(
                        "＋  Tambahkan ke Antrean"
                );

        page.addView(
                addButton,
                marginParams(0, 0, 0, 10)
        );

        addButton.setOnClickListener(
                v -> loadQueue()
        );

        Button clearButton =
                secondaryButton(
                        "Bersihkan"
                );

        page.addView(
                clearButton,
                marginParams(0, 0, 0, 22)
        );

        clearButton.setOnClickListener(
                v -> clearQueue()
        );

        LinearLayout info =
                roundedBox(
                        Color.WHITE,
                        Color.rgb(230, 231, 235),
                        16,
                        1
                );

        info.setOrientation(
                LinearLayout.VERTICAL
        );

        info.setPadding(
                dp(16),
                dp(14),
                dp(16),
                dp(14)
        );

        TextView infoTitle =
                text(
                        "Cara kerja",
                        17
                );

        infoTitle.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        TextView infoText =
                text(
                        "1. Masukkan link TikTok\n"
                                + "2. Link masuk ke antrean\n"
                                + "3. Aplikasi mengambil MP4 HD\n"
                                + "4. Video dan caption disimpan otomatis",
                        13
                );

        infoText.setTextColor(
                Color.rgb(107, 114, 128)
        );

        info.addView(
                infoTitle
        );

        info.addView(
                infoText,
                marginParams(0, 6, 0, 0)
        );

        page.addView(
                info,
                marginParams(0, 0, 0, 20)
        );

        LinearLayout clipboardCard =
                roundedBox(
                        Color.rgb(239, 246, 255),
                        Color.rgb(191, 219, 254),
                        16,
                        1
                );

        clipboardCard.setOrientation(
                LinearLayout.VERTICAL
        );

        clipboardCard.setPadding(
                dp(16),
                dp(14),
                dp(16),
                dp(14)
        );

        TextView clipboardTitle =
                text(
                        "Clipboard otomatis aktif",
                        15
                );

        clipboardTitle.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        TextView clipboardText =
                text(
                        "Saat aplikasi sedang dibuka, copy link TikTok "
                                + "akan otomatis masuk ke antrean.\n\n"
                                + "Untuk menerima link tanpa berpindah aplikasi, "
                                + "gunakan tombol Bagikan → TikTok Download Manager.",
                        13
                );

        clipboardText.setTextColor(
                Color.rgb(75, 85, 99)
        );

        clipboardCard.addView(
                clipboardTitle
        );

        clipboardCard.addView(
                clipboardText,
                marginParams(0, 5, 0, 0)
        );

        page.addView(
                clipboardCard,
                marginParams(0, 0, 0, 20)
        );

        TextView supported =
                text(
                        "Supported",
                        18
                );

        supported.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        page.addView(
                supported,
                marginParams(0, 0, 0, 10)
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

        LinearLayout grid =
                new LinearLayout(this);

        grid.setOrientation(
                LinearLayout.VERTICAL
        );

        for (int rowIndex = 0;
             rowIndex < 2;
             rowIndex++) {

            LinearLayout row =
                    new LinearLayout(this);

            row.setGravity(
                    Gravity.CENTER
            );

            for (int col = 0;
                 col < 4;
                 col++) {

                int index =
                        rowIndex * 4 + col;

                LinearLayout item =
                        new LinearLayout(this);

                item.setOrientation(
                        LinearLayout.VERTICAL
                );

                item.setGravity(
                        Gravity.CENTER
                );

                TextView icon =
                        text(
                                platforms[index][0],
                                20
                        );

                icon.setGravity(
                        Gravity.CENTER
                );

                icon.setTextColor(
                        Color.WHITE
                );

                icon.setBackground(
                        round(
                                Color.rgb(
                                        70,
                                        120,
                                        190
                                ),
                                50
                        )
                );

                item.addView(
                        icon,
                        new LinearLayout.LayoutParams(
                                dp(44),
                                dp(44)
                        )
                );

                TextView label =
                        text(
                                platforms[index][1],
                                11
                        );

                label.setGravity(
                        Gravity.CENTER
                );

                label.setTextColor(
                        Color.rgb(
                                75,
                                85,
                                99
                        )
                );

                item.addView(
                        label,
                        new LinearLayout.LayoutParams(
                                dp(78),
                                dp(32)
                        )
                );

                row.addView(
                        item,
                        new LinearLayout.LayoutParams(
                                0,
                                dp(78),
                                1
                        )
                );
            }

            grid.addView(
                    row,
                    new LinearLayout.LayoutParams(
                            -1,
                            dp(78)
                    )
            );
        }

        page.addView(
                grid,
                marginParams(0, 0, 0, 20)
        );

        TextView footer =
                text(
                        "TikTok Download Manager",
                        12
                );

        footer.setGravity(
                Gravity.CENTER
        );

        footer.setTextColor(
                Color.rgb(156, 163, 175)
        );

        page.addView(
                footer,
                new LinearLayout.LayoutParams(
                        -1,
                        dp(45)
                )
        );

        scroll.addView(
                page,
                new ScrollView.LayoutParams(
                        -1,
                        -2
                )
        );

        outer.addView(
                scroll,
                new LinearLayout.LayoutParams(
                        -1,
                        0,
                        1
                )
        );

        return outer;
    }

    // =========================================================
    // PROGRESS PAGE
    // =========================================================

    private LinearLayout buildProgressPage() {

        LinearLayout page =
                new LinearLayout(this);

        page.setOrientation(
                LinearLayout.VERTICAL
        );

        page.setPadding(
                dp(20),
                dp(20),
                dp(20),
                dp(10)
        );

        TextView title =
                text(
                        "Progress",
                        25
                );

        title.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        page.addView(
                title,
                marginParams(0, 0, 0, 4)
        );

        TextView subtitle =
                text(
                        "Antrean download kamu",
                        13
                );

        subtitle.setTextColor(
                Color.rgb(107, 114, 128)
        );

        page.addView(
                subtitle,
                marginParams(0, 0, 0, 14)
        );

        LinearLayout summary =
                roundedBox(
                        Color.WHITE,
                        Color.rgb(230, 231, 235),
                        15,
                        1
                );

        summary.setPadding(
                dp(15),
                dp(12),
                dp(15),
                dp(12)
        );

        progressText =
                text(
                        "0 / 0",
                        14
                );

        progressText.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        summary.addView(
                progressText,
                new LinearLayout.LayoutParams(
                        0,
                        dp(42),
                        1
                )
        );

        Button start =
                primaryButton(
                        "Mulai Semua"
                );

        summary.addView(
                start,
                new LinearLayout.LayoutParams(
                        dp(130),
                        dp(48)
                )
        );

        start.setOnClickListener(
                v -> startAll()
        );

        page.addView(
                summary,
                marginParams(0, 0, 0, 12)
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
                        11
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

        return page;
    }

    // =========================================================
    // FINISHED PAGE
    // =========================================================

    private LinearLayout buildFinishedPage() {

        LinearLayout page =
                new LinearLayout(this);

        page.setOrientation(
                LinearLayout.VERTICAL
        );

        page.setPadding(
                dp(20),
                dp(20),
                dp(20),
                dp(10)
        );

        TextView title =
                text(
                        "Finished",
                        25
                );

        title.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        page.addView(
                title,
                marginParams(0, 0, 0, 4)
        );

        TextView subtitle =
                text(
                        "Video yang sudah selesai",
                        13
                );

        subtitle.setTextColor(
                Color.rgb(107, 114, 128)
        );

        page.addView(
                subtitle,
                marginParams(0, 0, 0, 14)
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

    // =========================================================
    // FINISHED
    // =========================================================

    private void addFinishedItem(
            int index,
            String caption
    ) {

        if (finishedList == null) {
            return;
        }

        LinearLayout card =
                new LinearLayout(this);

        card.setOrientation(
                LinearLayout.VERTICAL
        );

        card.setPadding(
                dp(14),
                dp(14),
                dp(14),
                dp(14)
        );

        card.setBackground(
                round(
                        Color.WHITE,
                        18,
                        Color.rgb(
                                230,
                                231,
                                235
                        ),
                        1
                )
        );

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );

        cardParams.setMargins(
                0,
                0,
                0,
                dp(14)
        );

        finishedList.addView(
                card,
                cardParams
        );

        TextView header =
                text(
                        "✓  Video "
                                + String.format(
                                Locale.US,
                                "%03d",
                                index + 1
                        ),
                        16
                );

        header.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        card.addView(
                header,
                marginParams(
                        0,
                        0,
                        0,
                        8
                )
        );

        // =====================================================
        // VIDEO PREVIEW
        // =====================================================

        try {

            DownloadManager manager =
                    (DownloadManager)
                            getSystemService(
                                    Context.DOWNLOAD_SERVICE
                            );

            if (manager != null &&
                    currentDownloadId != -1L) {

                final Uri videoUri =
                        manager.getUriForDownloadedFile(
                                currentDownloadId
                        );

                if (videoUri != null) {

                    FrameLayout preview =
                            new FrameLayout(this);

                    preview.setBackgroundColor(
                            Color.BLACK
                    );

                    // =================================================
                    // THUMBNAIL
                    // =================================================

                    ImageView thumbnail =
                            new ImageView(this);

                    thumbnail.setScaleType(
                            ImageView.ScaleType.CENTER_CROP
                    );

                    thumbnail.setBackgroundColor(
                            Color.BLACK
                    );

                    Bitmap bitmap = null;

                    MediaMetadataRetriever retriever =
                            new MediaMetadataRetriever();

                    try {

                        retriever.setDataSource(
                                this,
                                videoUri
                        );

                        // Ambil frame sekitar detik pertama
                        bitmap =
                                retriever.getFrameAtTime(
                                        1000000L,
                                        MediaMetadataRetriever
                                                .OPTION_CLOSEST_SYNC
                                );

                        // Jika frame pertama gagal,
                        // coba frame paling awal.
                        if (bitmap == null) {

                            bitmap =
                                    retriever.getFrameAtTime(
                                            0L,
                                            MediaMetadataRetriever
                                                    .OPTION_CLOSEST_SYNC
                                    );
                        }

                    } catch (Exception ignored) {

                    } finally {

                        try {
                            retriever.release();
                        } catch (Exception ignored) {
                        }
                    }

                    if (bitmap != null) {

                        thumbnail.setImageBitmap(
                                bitmap
                        );
                    }

                    preview.addView(
                            thumbnail,
                            new FrameLayout.LayoutParams(
                                    -1,
                                    -1
                            )
                    );

                    // =================================================
                    // PLAY BUTTON
                    // =================================================

                    TextView play =
                            new TextView(this);

                    play.setText(
                            "▶"
                    );

                    play.setTextSize(
                            28
                    );

                    play.setTextColor(
                            Color.WHITE
                    );

                    play.setGravity(
                            Gravity.CENTER
                    );

                    play.setTypeface(
                            Typeface.DEFAULT,
                            Typeface.BOLD
                    );

                    play.setBackground(
                            round(
                                    Color.rgb(
                                            37,
                                            99,
                                            235
                                    ),
                                    50
                            )
                    );

                    FrameLayout.LayoutParams playParams =
                            new FrameLayout.LayoutParams(
                                    dp(64),
                                    dp(64),
                                    Gravity.CENTER
                            );

                    preview.addView(
                            play,
                            playParams
                    );

                    // =================================================
                    // PLAY VIDEO
                    // =================================================

                    View.OnClickListener playListener =
                            v -> {

                                preview.removeAllViews();

                                VideoView video =
                                        new VideoView(
                                                MainActivity.this
                                        );

                                video.setBackgroundColor(
                                        Color.BLACK
                                );

                                MediaController controller =
                                        new MediaController(
                                                MainActivity.this
                                        );

                                controller.setAnchorView(
                                        video
                                );

                                video.setMediaController(
                                        controller
                                );

                                video.setVideoURI(
                                        videoUri
                                );

                                preview.addView(
                                        video,
                                        new FrameLayout.LayoutParams(
                                                -1,
                                                -1
                                        )
                                );

                                video.setOnPreparedListener(
                                        mp -> {

                                            mp.setLooping(
                                                    false
                                            );

                                            video.start();
                                        }
                                );
                            };

                    thumbnail.setOnClickListener(
                            playListener
                    );

                    play.setOnClickListener(
                            playListener
                    );

                    card.addView(
                            preview,
                            new LinearLayout.LayoutParams(
                                    -1,
                                    dp(205)
                            )
                    );
                }
            }

        } catch (Exception ignored) {
        }

        // =====================================================
        // CAPTION
        // =====================================================

        TextView cap =
                text(
                        caption == null ||
                                caption.trim().isEmpty()
                                ? "Tanpa caption"
                                : caption,
                        13
                );

        cap.setTextColor(
                Color.rgb(
                        55,
                        65,
                        81
                )
        );

        cap.setMaxLines(
                4
        );

        card.addView(
                cap,
                marginParams(
                        0,
                        10,
                        0,
                        5
                )
        );

        // =====================================================
        // FILE INFO
        // =====================================================

        TextView files =
                text(
                        "video.mp4  •  caption.txt",
                        12
                );

        files.setTextColor(
                Color.rgb(
                        107,
                        114,
                        128
                )
        );

        card.addView(
                files
        );
    }

    // =========================================================
    // WEBVIEW
    // =========================================================

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
        settings.setJavaScriptCanOpenWindowsAutomatically(false);

        settings.setUserAgentString(
                "Mozilla/5.0 (Linux; Android 12) "
                        + "AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) "
                        + "Chrome/140.0 Mobile Safari/537.36"
        );

        CookieManager.getInstance()
                .setAcceptCookie(true);

        CookieManager.getInstance()
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

    private boolean isSaveTikTokPage(String url) {

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

    // =========================================================
    // LOAD QUEUE
    // =========================================================

    private void loadQueue() {

        LinkedHashSet<String> unique =
                new LinkedHashSet<>();

        String raw =
                input.getText()
                        .toString();

        for (String line :
                raw.split("\\r?\\n")) {

            String url =
                    line.trim();

            if (
                    url.startsWith("http://") ||
                            url.startsWith("https://")
            ) {

                unique.add(url);
            }
        }

        if (unique.isEmpty()) {

            Toast.makeText(
                    this,
                    "Masukkan minimal satu link TikTok.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        urls.clear();
        urls.addAll(unique);

        clipboardAddedUrls.clear();

        for (String url : urls) {
            clipboardAddedUrls.add(url);
        }

        currentIndex = -1;
        processing = false;
        submitClicked = false;
        downloadStarted = false;
        currentCaption = "";
        currentDownloadId = -1L;

        handledDownloadUrls.clear();

        handler.removeCallbacksAndMessages(null);

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
                "WebView: antrean siap"
        );

        Toast.makeText(
                this,
                urls.size()
                        + " link masuk antrean",
                Toast.LENGTH_SHORT
        ).show();

        showPage(1);
    }

    // =========================================================
    // QUEUE ITEM
    // =========================================================

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
                dp(15),
                dp(14),
                dp(15),
                dp(14)
        );

        card.setBackground(
                round(
                        Color.WHITE,
                        16,
                        Color.rgb(
                                230,
                                231,
                                235
                        ),
                        1
                )
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
                dp(10)
        );

        queue.addView(
                card,
                params
        );

        TextView status =
                text(
                        "#" + (index + 1)
                                + "   Menunggu",
                        16
                );

        status.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        TextView link =
                text(
                        url,
                        12
                );

        link.setTextColor(
                Color.rgb(
                        107,
                        114,
                        128
                )
        );

        link.setMaxLines(
                3
        );

        Button process =
                secondaryButton(
                        "Proses Link Ini"
                );

        process.setOnClickListener(
                v -> startSingle(index)
        );

        card.addView(
                status
        );

        card.addView(
                link,
                marginParams(
                        0,
                        3,
                        0,
                        8
                )
        );

        card.addView(
                process,
                new LinearLayout.LayoutParams(
                        -1,
                        dp(44)
                )
        );

        card.setTag(
                status
        );
    }

    // =========================================================
    // START
    // =========================================================

    private void startSingle(int index) {

        if (index < 0 ||
                index >= urls.size()) {
            return;
        }

        if (processing) {

            Toast.makeText(
                    this,
                    "Masih memproses antrean.",
                    Toast.LENGTH_SHORT
            ).show();

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

        progressText.setText(
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

    private void startAll() {

        if (urls.isEmpty()) {

            Toast.makeText(
                    this,
                    "Belum ada antrean.",
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

        startSingle(0);
    }

    // =========================================================
    // SAVETIKTOK INPUT
    // =========================================================

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
                    "Kolom URL tidak ditemukan"
            );

            processing = false;

            return;
        }

        String js =
                "(function(){"
                        + "var fields=Array.from("
                        + "document.querySelectorAll('input,textarea'));"
                        + "var field=fields.find(function(el){"
                        + "var p=((el.placeholder||'')+' '+"
                        + "(el.getAttribute('aria-label')||'')+' '+"
                        + "(el.name||'')+' '+"
                        + "(el.type||'')).toLowerCase();"
                        + "return p.includes('tautan')"
                        + "||p.includes('tiktok')"
                        + "||p.includes('link')"
                        + "||p.includes('url')"
                        + "||el.type==='url';"
                        + "});"
                        + "return field?'FOUND':'WAIT';"
                        + "})()";

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

    // =========================================================
    // INJECT
    // =========================================================

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
                "(function(){"
                        + "var fields=Array.from("
                        + "document.querySelectorAll('input,textarea'));"
                        + "var field=fields.find(function(el){"
                        + "var p=((el.placeholder||'')+' '+"
                        + "(el.getAttribute('aria-label')||'')+' '+"
                        + "(el.name||'')+' '+"
                        + "(el.type||'')).toLowerCase();"
                        + "return p.includes('tautan')"
                        + "||p.includes('tiktok')"
                        + "||p.includes('link')"
                        + "||p.includes('url')"
                        + "||el.type==='url';"
                        + "});"
                        + "if(!field)return 'NO_FIELD';"
                        + "var proto=field instanceof "
                        + "HTMLTextAreaElement?"
                        + "HTMLTextAreaElement.prototype:"
                        + "HTMLInputElement.prototype;"
                        + "var desc=Object.getOwnPropertyDescriptor("
                        + "proto,'value');"
                        + "if(desc&&desc.set){"
                        + "desc.set.call(field,"
                        + safeUrl
                        + ");"
                        + "}else{field.value="
                        + safeUrl
                        + ";}"
                        + "field.dispatchEvent(new Event("
                        + "'input',{bubbles:true}));"
                        + "field.dispatchEvent(new Event("
                        + "'change',{bubbles:true}));"
                        + "field.focus();"
                        + "if(field.value!=="
                        + safeUrl
                        + ")return 'VALUE_FAILED';"
                        + "var buttons=Array.from("
                        + "document.querySelectorAll("
                        + "'button,input[type=submit],"
                        + "input[type=button],a'));"
                        + "var downloadButton=buttons.find(function(el){"
                        + "var t=(el.innerText||el.textContent||"
                        + "el.value||el.getAttribute('aria-label')||'')"
                        + ".replace(/\\s+/g,' ').trim().toLowerCase();"
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
                                () ->
                                        findMp4HdButton(0),
                                2000
                        );

                    } else {

                        submitClicked = false;

                        webStatus.setText(
                                "WebView: URL belum berhasil dikirim..."
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

    // =========================================================
    // MP4 HD
    // =========================================================

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

            return;
        }

        String js =
                "(function(){"
                        + "var root=document.querySelector("
                        + "'#download-result');"
                        + "if(!root)return JSON.stringify({state:'WAIT'});"
                        + "var card=root.querySelector("
                        + "'.video-data .tik-video');"
                        + "if(!card)return JSON.stringify({state:'WAIT'});"
                        + "var captionEl=card.querySelector("
                        + "'.tik-left .thumbnail .content .clearfix h3');"
                        + "var buttons=Array.from(card.querySelectorAll("
                        + "'.dl-action a.tik-button-dl'));"
                        + "var hd=buttons.find(function(el){"
                        + "var t=(el.innerText||el.textContent||'')"
                        + ".replace(/\\s+/g,' ').trim().toLowerCase();"
                        + "return t==='unduh mp4 hd'||"
                        + "t==='download mp4 hd';"
                        + "});"
                        + "if(!hd)return JSON.stringify({state:'WAIT'});"
                        + "var caption=captionEl?"
                        + "(captionEl.innerText||captionEl.textContent||'')"
                        + ".trim():'';"
                        + "return JSON.stringify({"
                        + "state:'FOUND',"
                        + "caption:caption,"
                        + "hasCaption:!!captionEl});"
                        + "})()";

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

                        String clickJs =
                                "(function(){"
                                        + "var card=document.querySelector("
                                        + "'#download-result .video-data .tik-video');"
                                        + "if(!card)return 'WAIT';"
                                        + "var buttons=Array.from("
                                        + "card.querySelectorAll("
                                        + "'.dl-action a.tik-button-dl'));"
                                        + "var hd=buttons.find(function(el){"
                                        + "var t=(el.innerText||el.textContent||'')"
                                        + ".replace(/\\s+/g,' ').trim().toLowerCase();"
                                        + "return t==='unduh mp4 hd'||"
                                        + "t==='download mp4 hd';"
                                        + "});"
                                        + "if(!hd)return 'WAIT';"
                                        + "hd.click();"
                                        + "return 'CLICKED';"
                                        + "})()";

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

    // =========================================================
    // DOWNLOAD
    // =========================================================

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
                lowerMime.contains("text/html")
                        ||
                        lowerMime.contains(
                                "application/xhtml"
                        );

        if (looksLikeHtml) {
            return;
        }

        boolean isBin =
                lowerMime.contains(
                        "application/octet-stream"
                )
                        ||
                        lowerDisposition.contains(".bin")
                        ||
                        lowerUrl.matches(
                                ".*\\.bin(?:[?#].*)?$"
                        );

        boolean looksLikeVideo =
                lowerMime.startsWith("video/")
                        ||
                        lowerMime.contains("mp4")
                        ||
                        lowerDisposition.contains(".mp4")
                        ||
                        lowerUrl.contains(".mp4");

        if (isBin && submitClicked) {
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

            handledDownloadUrls.remove(first);
        }

        downloadStarted = true;

        webStatus.setText(
                "WebView: file MP4 ditemukan..."
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

            String folder =
                    "TikTokDownloadManager/"
                            + String.format(
                                    Locale.US,
                                    "%03d",
                                    currentIndex + 1
                            );

            request.setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    folder + "/video.mp4"
            );

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
                    "WebView: download dimulai → "
                            + "Download/"
                            + folder
            );

            progressText.setText(
                    "Progress: "
                            + (completedIndex + 1)
                            + " / "
                            + urls.size()
            );

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
                    "WebView: gagal download - "
                            + e.getMessage()
            );
        }
    }

    // =========================================================
    // CAPTION
    // =========================================================

    private void saveCaptionFile(int index) {

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
                                android.provider.MediaStore
                                        .Downloads
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

                    getContentResolver().update(
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
                                                Environment
                                                        .DIRECTORY_DOWNLOADS
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
                                    StandardCharsets.UTF_8
                            )
                    );
                }
            }

        } catch (Exception e) {

            webStatus.setText(
                    "WebView: caption gagal disimpan"
            );
        }
    }

    // =========================================================
    // DOWNLOAD MONITOR
    // =========================================================

    private void waitForDownloadCompletion(
            DownloadManager manager,
            long downloadId,
            int completedIndex,
            int attempt
    ) {

        /*
         * 1200 x 500 ms = sekitar 10 menit.
         *
         * Download berikutnya hanya dimulai
         * setelah DownloadManager benar-benar
         * menyatakan download selesai.
         */

        if (attempt >= 1200) {

            setStatus(
                    completedIndex,
                    "Download masih berjalan"
            );

            handler.postDelayed(
                    () ->
                            waitForDownloadCompletion(
                                    manager,
                                    downloadId,
                                    completedIndex,
                                    attempt
                            ),
                    5000
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
                                            DownloadManager
                                                    .COLUMN_REASON
                                    )
                            );

                    downloadStarted = false;
                    processing = false;

                    setStatus(
                            completedIndex,
                            "Gagal download ("
                                    + reason
                                    + ")"
                    );

                    webStatus.setText(
                            "WebView: download gagal"
                    );

                    return;
                }

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

                if (total > 0) {

                    int percent =
                            (int) Math.max(
                                    0,
                                    Math.min(
                                            100,
                                            (downloaded * 100L)
                                                    / total
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
                () ->
                        waitForDownloadCompletion(
                                manager,
                                downloadId,
                                completedIndex,
                                attempt + 1
                        ),
                500
        );
    }

    // =========================================================
    // FINISH
    // =========================================================

    private void finishCurrentAndNext(
            int completedIndex
    ) {

        processing = false;
        submitClicked = false;
        downloadStarted = false;

        /*
         * currentDownloadId masih dipertahankan
         * ketika Finished card dibuat karena
         * addFinishedItem() membutuhkan URI file.
         */

        if (completedIndex >= 0 &&
                completedIndex < urls.size()) {

            addFinishedItem(
                    completedIndex,
                    currentCaption
            );
        }

        currentCaption = "";
        currentDownloadId = -1L;

        int next =
                completedIndex + 1;

        if (next < urls.size()) {

            startSingle(next);

        } else {

            currentIndex = -1;

            webStatus.setText(
                    "WebView: semua antrean selesai"
            );

            progressText.setText(
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

            showPage(2);
        }
    }

    // =========================================================
    // CLEAR
    // =========================================================

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
        clipboardAddedUrls.clear();

        if (queue != null) {
            queue.removeAllViews();
        }

        if (finishedList != null) {
            finishedList.removeAllViews();
        }

        if (input != null) {
            input.setText("");
        }

        if (progressText != null) {
            progressText.setText(
                    "0 / 0"
            );
        }

        if (webStatus != null) {

            webStatus.setText(
                    "WebView: siap"
            );
        }

        if (webView != null) {

            webView.stopLoading();

            webView.setVisibility(
                    View.GONE
            );
        }

        Toast.makeText(
                this,
                "Antrean dibersihkan.",
                Toast.LENGTH_SHORT
        ).show();

        showPage(0);
    }

    // =========================================================
    // STATUS
    // =========================================================

    private void setStatus(
            int index,
            String value
    ) {

        if (queue == null ||
                index < 0 ||
                index >= queue.getChildCount()) {
            return;
        }

        View card =
                queue.getChildAt(index);

        Object tag =
                card.getTag();

        if (tag instanceof TextView) {

            ((TextView) tag).setText(
                    "#" + (index + 1)
                            + "   "
                            + value
            );
        }
    }

    private void updateProgress() {

        if (progressText == null) {
            return;
        }

        progressText.setText(
                "0 / "
                        + urls.size()
        );
    }

    // =========================================================
    // PAGE
    // =========================================================

    private void showPage(int page) {

        if (downloadPage == null) {
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

        if (navDownload != null) {

            navDownload.setTextColor(
                    page == 0
                            ? Color.rgb(
                                    17,
                                    24,
                                    39
                            )
                            : Color.rgb(
                                    156,
                                    163,
                                    175
                            )
            );
        }

        if (navProgress != null) {

            navProgress.setTextColor(
                    page == 1
                            ? Color.rgb(
                                    17,
                                    24,
                                    39
                            )
                            : Color.rgb(
                                    156,
                                    163,
                                    175
                            )
            );
        }

        if (navFinished != null) {

            navFinished.setTextColor(
                    page == 2
                            ? Color.rgb(
                                    17,
                                    24,
                                    39
                            )
                            : Color.rgb(
                                    156,
                                    163,
                                    175
                            )
            );
        }
    }

    // =========================================================
    // UI HELPERS
    // =========================================================

    private TextView navItem(
            String icon,
            String label
    ) {

        TextView view =
                new TextView(this);

        view.setText(
                icon + "\n" + label
        );

        view.setTextSize(
                11
        );

        view.setGravity(
                Gravity.CENTER
        );

        view.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        view.setBackgroundColor(
                Color.TRANSPARENT
        );

        view.setTextColor(
                Color.rgb(
                        156,
                        163,
                        175
                )
        );

        return view;
    }

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

        view.setGravity(
                Gravity.CENTER_VERTICAL
        );

        return view;
    }

    private Button primaryButton(
            String value
    ) {

        Button button =
                new Button(this);

        button.setText(
                value
        );

        button.setTextSize(
                14
        );

        button.setAllCaps(
                false
        );

        button.setTextColor(
                Color.WHITE
        );

        button.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        button.setGravity(
                Gravity.CENTER
        );

        button.setBackground(
                round(
                        Color.rgb(
                                37,
                                99,
                                235
                        ),
                        14
                )
        );

        return button;
    }

    private Button secondaryButton(
            String value
    ) {

        Button button =
                new Button(this);

        button.setText(
                value
        );

        button.setTextSize(
                13
        );

        button.setAllCaps(
                false
        );

        button.setTextColor(
                Color.rgb(
                        31,
                        41,
                        55
                )
        );

        button.setGravity(
                Gravity.CENTER
        );

        button.setBackground(
                round(
                        Color.rgb(
                                243,
                                244,
                                246
                        ),
                        12,
                        Color.rgb(
                                229,
                                231,
                                235
                        ),
                        1
                )
        );

        return button;
    }

    private LinearLayout roundedBox(
            int fill,
            int stroke,
            int radius,
            int strokeWidth
    ) {

        LinearLayout box =
                new LinearLayout(this);

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

    private GradientDrawable round(
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

    private GradientDrawable round(
            int fill,
            int radius,
            int stroke,
            int strokeWidth
    ) {

        GradientDrawable drawable =
                new GradientDrawable();

        drawable.setColor(
                fill
        );

        drawable.setCornerRadius(
                dp(radius)
        );

        if (strokeWidth > 0) {

            drawable.setStroke(
                    dp(strokeWidth),
                    stroke
            );
        }

        return drawable;
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

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );

        params.setMargins(
                dp(left),
                dp(top),
                dp(right),
                dp(bottom)
        );

        return params;
    }

    private int dp(int value) {

        return (int) (
                value *
                        getResources()
                                .getDisplayMetrics()
                                .density
        );
    }

    // =========================================================
    // STORAGE
    // =========================================================

    private void requestStorageIfNeeded() {

        if (
                Build.VERSION.SDK_INT >= 23 &&
                        Build.VERSION.SDK_INT <= 28 &&
                        checkSelfPermission(
                                Manifest.permission
                                        .WRITE_EXTERNAL_STORAGE
                        )
                                !=
                                PackageManager.PERMISSION_GRANTED
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

    // =========================================================
    // BACK
    // =========================================================

    @Override
    public void onBackPressed() {

        if (
                webView != null &&
                        webView.getVisibility()
                                == View.VISIBLE &&
                        webView.canGoBack()
        ) {

            webView.goBack();

        } else {

            super.onBackPressed();
        }
    }
}
