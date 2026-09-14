package com.example.tiktokdownloadmanager;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final String SAVE_URL =
            "https://savetiktok.to/id";

    private static final int REQ_STORAGE = 501;

    private static final int MAX_INPUT_CHECKS = 20;
    private static final int MAX_MP4_HD_CHECKS = 30;

    private static final int MAX_DOWNLOAD_CHECKS = 1200;

    private static final long DOWNLOAD_CHECK_INTERVAL = 500L;

    // =========================
    // COLORS
    // =========================

    private static final int BG =
            Color.rgb(247, 249, 252);

    private static final int CARD =
            Color.WHITE;

    private static final int TEXT =
            Color.rgb(15, 23, 42);

    private static final int SECONDARY =
            Color.rgb(100, 116, 139);

    private static final int BLUE =
            Color.rgb(37, 99, 235);

    private static final int GREEN =
            Color.rgb(22, 163, 74);

    private static final int RED =
            Color.rgb(220, 38, 38);

    private static final int ORANGE =
            Color.rgb(245, 158, 11);


    // =========================
    // UI
    // =========================

    private LinearLayout root;

    private FrameLayout contentContainer;

    private LinearLayout downloadPage;
    private LinearLayout progressPage;
    private LinearLayout finishedPage;

    private LinearLayout downloadList;
    private LinearLayout progressList;
    private LinearLayout finishedList;

    private TextView tabDownload;
    private TextView tabProgress;
    private TextView tabFinished;

    private TextView downloadCount;
    private TextView processingCount;
    private TextView successCount;
    private TextView failedCount;

    private TextView progressTitle;
    private TextView progressSubtitle;

    private TextView finishedTitle;
    private TextView finishedSubtitle;

    private EditText input;

    private WebView webView;


    // =========================
    // DATA
    // =========================

    private final ArrayList<String> urls =
            new ArrayList<>();

    private final ArrayList<String> statuses =
            new ArrayList<>();

    private final ArrayList<Integer> percentages =
            new ArrayList<>();

    private final ArrayList<String> captions =
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


    // =========================
    // LIFECYCLE
    // =========================

    @Override
    protected void onCreate(
            Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        buildUi();

        requestStorageIfNeeded();
    }


    // =========================
    // MAIN UI
    // =========================

    private void buildUi() {

        root =
                new LinearLayout(this);

        root.setOrientation(
                LinearLayout.VERTICAL
        );

        root.setBackgroundColor(BG);


        contentContainer =
                new FrameLayout(this);


        root.addView(
                contentContainer,
                new LinearLayout.LayoutParams(
                        -1,
                        0,
                        1
                )
        );


        buildDownloadPage();

        buildProgressPage();

        buildFinishedPage();


        contentContainer.addView(
                downloadPage
        );

        contentContainer.addView(
                progressPage
        );

        contentContainer.addView(
                finishedPage
        );


        downloadPage.setVisibility(
                View.VISIBLE
        );

        progressPage.setVisibility(
                View.GONE
        );

        finishedPage.setVisibility(
                View.GONE
        );


        LinearLayout bottom =
                buildBottomNavigation();


        root.addView(
                bottom,
                new LinearLayout.LayoutParams(
                        -1,
                        dp(76)
                )
        );


        setContentView(root);
    }


    // =========================
    // DOWNLOAD PAGE
    // =========================

    private void buildDownloadPage() {

        downloadPage =
                new LinearLayout(this);

        downloadPage.setOrientation(
                LinearLayout.VERTICAL
        );

        downloadPage.setBackgroundColor(BG);


        ScrollView scroll =
                new ScrollView(this);

        scroll.setFillViewport(true);


        LinearLayout body =
                new LinearLayout(this);

        body.setOrientation(
                LinearLayout.VERTICAL
        );

        body.setPadding(
                dp(18),
                dp(18),
                dp(18),
                dp(20)
        );


        // HEADER

        LinearLayout header =
                new LinearLayout(this);

        header.setGravity(
                Gravity.CENTER_VERTICAL
        );


        LinearLayout titleBox =
                new LinearLayout(this);

        titleBox.setOrientation(
                LinearLayout.VERTICAL
        );


        TextView title =
                makeText(
                        "TikTok Download Manager",
                        25,
                        TEXT
                );

        title.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );


        TextView subtitle =
                makeText(
                        "Download video + caption otomatis",
                        14,
                        SECONDARY
                );


        titleBox.addView(title);

        titleBox.addView(
                subtitle,
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                )
        );


        header.addView(
                titleBox,
                new LinearLayout.LayoutParams(
                        0,
                        -2,
                        1
                )
        );


        TextView settings =
                makeText(
                        "⚙",
                        25,
                        TEXT
                );

        settings.setGravity(
                Gravity.CENTER
        );


        header.addView(
                settings,
                new LinearLayout.LayoutParams(
                        dp(45),
                        dp(45)
                )
        );


        body.addView(header);


        addSpace(
                body,
                16
        );


        // INPUT CARD

        LinearLayout inputCard =
                roundedCard(
                        CARD,
                        16
                );


        LinearLayout inputRow =
                new LinearLayout(this);

        inputRow.setGravity(
                Gravity.CENTER_VERTICAL
        );


        TextView linkIcon =
                makeText(
                        "↗",
                        24,
                        TEXT
                );

        linkIcon.setGravity(
                Gravity.CENTER
        );


        inputRow.addView(
                linkIcon,
                new LinearLayout.LayoutParams(
                        dp(38),
                        dp(50)
                )
        );


        input =
                new EditText(this);

        input.setHint(
                "Tempel link TikTok di sini..."
        );

        input.setTextSize(14);

        input.setTextColor(TEXT);

        input.setHintTextColor(
                Color.rgb(
                        148,
                        163,
                        184
                )
        );

        input.setSingleLine(false);

        input.setMinLines(1);

        input.setMaxLines(4);

        input.setPadding(
                0,
                0,
                0,
                0
        );

        input.setBackgroundColor(
                Color.TRANSPARENT
        );


        inputRow.addView(
                input,
                new LinearLayout.LayoutParams(
                        0,
                        -2,
                        1
                )
        );


        TextView clearInput =
                makeText(
                        "×",
                        25,
                        SECONDARY
                );

        clearInput.setGravity(
                Gravity.CENTER
        );

        clearInput.setOnClickListener(
                v -> input.setText("")
        );


        inputRow.addView(
                clearInput,
                new LinearLayout.LayoutParams(
                        dp(38),
                        dp(50)
                )
        );


        inputCard.addView(inputRow);


        body.addView(
                inputCard
        );


        addSpace(
                body,
                10
        );


        // BUTTONS

        LinearLayout buttonRow =
                new LinearLayout(this);

        buttonRow.setOrientation(
                LinearLayout.HORIZONTAL
        );


        Button load =
                modernButton(
                        "▣  Muat Antrean",
                        false
                );


        Button clear =
                modernButton(
                        "▢  Bersihkan",
                        false
                );


        buttonRow.addView(
                load,
                new LinearLayout.LayoutParams(
                        0,
                        dp(52),
                        1
                )
        );


        LinearLayout.LayoutParams
                clearParams =
                new LinearLayout.LayoutParams(
                        0,
                        dp(52),
                        1
                );

        clearParams.setMargins(
                dp(8),
                0,
                0,
                0
        );


        buttonRow.addView(
                clear,
                clearParams
        );


        body.addView(
                buttonRow
        );


        load.setOnClickListener(
                v -> loadQueue()
        );


        clear.setOnClickListener(
                v -> clearQueue()
        );


        addSpace(
                body,
                14
        );


        // STATISTICS

        LinearLayout stats =
                new LinearLayout(this);

        stats.setOrientation(
                LinearLayout.HORIZONTAL
        );


        downloadCount =
                statCard(
                        "0",
                        "Antrean",
                        BLUE
                );


        processingCount =
                statCard(
                        "0",
                        "Diproses",
                        ORANGE
                );


        successCount =
                statCard(
                        "0",
                        "Selesai",
                        GREEN
                );


        failedCount =
                statCard(
                        "0",
                        "Gagal",
                        RED
                );


        addStat(
                stats,
                downloadCount
        );

        addStat(
                stats,
                processingCount
        );

        addStat(
                stats,
                successCount
        );

        addStat(
                stats,
                failedCount
        );


        body.addView(stats);


        addSpace(
                body,
                18
        );


        // MAIN BUTTON

        Button startAll =
                blueButton(
                        "▶  Mulai Semua"
                );


        body.addView(
                startAll,
                new LinearLayout.LayoutParams(
                        -1,
                        dp(54)
                )
        );


        startAll.setOnClickListener(
                v -> startAll()
        );


        addSpace(
                body,
                20
        );


        // QUEUE TITLE

        LinearLayout queueHeader =
                new LinearLayout(this);

        queueHeader.setGravity(
                Gravity.CENTER_VERTICAL
        );


        TextView queueTitle =
                makeText(
                        "Daftar Antrean",
                        18,
                        TEXT
                );

        queueTitle.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );


        TextView queueNumber =
                makeText(
                        "(0)",
                        14,
                        SECONDARY
                );


        queueHeader.addView(
                queueTitle
        );


        queueHeader.addView(
                queueNumber
        );


        body.addView(queueHeader);


        addSpace(
                body,
                10
        );


        downloadList =
                new LinearLayout(this);

        downloadList.setOrientation(
                LinearLayout.VERTICAL
        );


        body.addView(
                downloadList
        );


        // HIDDEN WEBVIEW

        webView =
                new WebView(this);

        webView.setVisibility(
                View.GONE
        );


        body.addView(
                webView,
                new LinearLayout.LayoutParams(
                        -1,
                        dp(650)
                )
        );


        configureWebView();


        scroll.addView(body);


        downloadPage.addView(
                scroll,
                new LinearLayout.LayoutParams(
                        -1,
                        0,
                        1
                )
        );
    }


    // =========================
    // PROGRESS PAGE
    // =========================

    private void buildProgressPage() {

        progressPage =
                new LinearLayout(this);

        progressPage.setOrientation(
                LinearLayout.VERTICAL
        );

        progressPage.setBackgroundColor(BG);


        ScrollView scroll =
                new ScrollView(this);


        LinearLayout body =
                new LinearLayout(this);

        body.setOrientation(
                LinearLayout.VERTICAL
        );

        body.setPadding(
                dp(18),
                dp(18),
                dp(18),
                dp(20)
        );


        TextView title =
                makeText(
                        "Sedang Diproses",
                        26,
                        TEXT
                );

        title.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );


        body.addView(title);


        progressSubtitle =
                makeText(
                        "Tidak ada video sedang diproses",
                        14,
                        SECONDARY
                );


        body.addView(
                progressSubtitle
        );


        addSpace(
                body,
                15
        );


        LinearLayout statsCard =
                roundedCard(
                        CARD,
                        18
                );


        LinearLayout statRow =
                new LinearLayout(this);

        statRow.setOrientation(
                LinearLayout.HORIZONTAL
        );


        TextView p1 =
                miniStat(
                        "0",
                        "Diproses",
                        ORANGE
                );

        TextView p2 =
                miniStat(
                        "0",
                        "Antrean",
                        BLUE
                );

        TextView p3 =
                miniStat(
                        "0",
                        "Selesai",
                        GREEN
                );

        TextView p4 =
                miniStat(
                        "0",
                        "Gagal",
                        RED
                );


        statRow.addView(
                p1,
                new LinearLayout.LayoutParams(
                        0,
                        dp(75),
                        1
                )
        );

        statRow.addView(
                p2,
                new LinearLayout.LayoutParams(
                        0,
                        dp(75),
                        1
                )
        );

        statRow.addView(
                p3,
                new LinearLayout.LayoutParams(
                        0,
                        dp(75),
                        1
                )
        );

        statRow.addView(
                p4,
                new LinearLayout.LayoutParams(
                        0,
                        dp(75),
                        1
                )
        );


        statsCard.addView(statRow);

        body.addView(statsCard);


        addSpace(
                body,
                15
        );


        progressList =
                new LinearLayout(this);

        progressList.setOrientation(
                LinearLayout.VERTICAL
        );


        body.addView(
                progressList
        );


        scroll.addView(body);


        progressPage.addView(
                scroll,
                new LinearLayout.LayoutParams(
                        -1,
                        0,
                        1
                )
        );
    }


    // =========================
    // FINISHED PAGE
    // =========================

    private void buildFinishedPage() {

        finishedPage =
                new LinearLayout(this);

        finishedPage.setOrientation(
                LinearLayout.VERTICAL
        );

        finishedPage.setBackgroundColor(BG);


        ScrollView scroll =
                new ScrollView(this);


        LinearLayout body =
                new LinearLayout(this);

        body.setOrientation(
                LinearLayout.VERTICAL
        );

        body.setPadding(
                dp(18),
                dp(18),
                dp(18),
                dp(20)
        );


        TextView title =
                makeText(
                        "Selesai",
                        26,
                        TEXT
                );

        title.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );


        body.addView(title);


        finishedSubtitle =
                makeText(
                        "0 video berhasil diunduh",
                        14,
                        SECONDARY
                );


        body.addView(
                finishedSubtitle
        );


        addSpace(
                body,
                15
        );


        finishedList =
                new LinearLayout(this);

        finishedList.setOrientation(
                LinearLayout.VERTICAL
        );


        body.addView(
                finishedList
        );


        scroll.addView(body);


        finishedPage.addView(
                scroll,
                new LinearLayout.LayoutParams(
                        -1,
                        0,
                        1
                )
        );
    }


    // =========================
    // BOTTOM NAVIGATION
    // =========================

    private LinearLayout buildBottomNavigation() {

        LinearLayout nav =
                new LinearLayout(this);

        nav.setOrientation(
                LinearLayout.HORIZONTAL
        );

        nav.setGravity(
                Gravity.CENTER
        );

        nav.setBackgroundColor(
                Color.WHITE
        );


        tabDownload =
                navItem(
                        "⌂",
                        "Download"
                );


        tabProgress =
                navItem(
                        "↓",
                        "Progress"
                );


        tabFinished =
                navItem(
                        "✓",
                        "Finished"
                );


        nav.addView(
                tabDownload,
                new LinearLayout.LayoutParams(
                        0,
                        -1,
                        1
                )
        );

        nav.addView(
                tabProgress,
                new LinearLayout.LayoutParams(
                        0,
                        -1,
                        1
                )
        );

        nav.addView(
                tabFinished,
                new LinearLayout.LayoutParams(
                        0,
                        -1,
                        1
                )
        );


        tabDownload.setOnClickListener(
                v -> showPage(0)
        );

        tabProgress.setOnClickListener(
                v -> showPage(1)
        );

        tabFinished.setOnClickListener(
                v -> showPage(2)
        );


        return nav;
    }


    private TextView navItem(
            String icon,
            String label) {

        TextView view =
                makeText(
                        icon + "\n" + label,
                        13,
                        SECONDARY
                );

        view.setGravity(
                Gravity.CENTER
        );


        return view;
    }


    private void showPage(int page) {

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


        tabDownload.setTextColor(
                page == 0
                        ? BLUE
                        : SECONDARY
        );

        tabProgress.setTextColor(
                page == 1
                        ? BLUE
                        : SECONDARY
        );

        tabFinished.setTextColor(
                page == 2
                        ? BLUE
                        : SECONDARY
        );


        if (page == 1) {

            refreshProgressPage();
        }

        if (page == 2) {

            refreshFinishedPage();
        }
    }


    // =========================
    // QUEUE
    // =========================

    private void loadQueue() {

        LinkedHashSet<String> unique =
                new LinkedHashSet<>();


        String[] lines =
                input.getText()
                        .toString()
                        .split("\\r?\\n");


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


        statuses.clear();

        percentages.clear();

        captions.clear();


        for (int i = 0;
             i < urls.size();
             i++) {

            statuses.add("Menunggu");

            percentages.add(0);

            captions.add("");
        }


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


        refreshAllLists();

        updateStats();


        if (urls.isEmpty()) {

            Toast.makeText(
                    this,
                    "Tidak ada URL TikTok.",
                    Toast.LENGTH_SHORT
            ).show();

        } else {

            Toast.makeText(
                    this,
                    urls.size() +
                            " link dimasukkan ke antrean.",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }


    // =========================
    // QUEUE CARD
    // =========================

    private void refreshAllLists() {

        refreshDownloadPage();

        refreshProgressPage();

        refreshFinishedPage();
    }


    private void refreshDownloadPage() {

        if (downloadList == null) {
            return;
        }


        downloadList.removeAllViews();


        for (int i = 0;
             i < urls.size();
             i++) {

            downloadList.addView(
                    createDownloadCard(i)
            );
        }
    }


    private View createDownloadCard(
            int index) {

        LinearLayout card =
                roundedCard(
                        CARD,
                        18
                );


        card.setPadding(
                dp(12),
                dp(12),
                dp(12),
                dp(12)
        );


        LinearLayout row =
                new LinearLayout(this);

        row.setGravity(
                Gravity.CENTER_VERTICAL
        );


        TextView number =
                makeText(
                        String.valueOf(index + 1),
                        14,
                        TEXT
                );

        number.setGravity(
                Gravity.CENTER
        );

        number.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );


        GradientDrawable numberBg =
                new GradientDrawable();

        numberBg.setColor(
                Color.rgb(
                        241,
                        245,
                        249
                )
        );

        numberBg.setShape(
                GradientDrawable.OVAL
        );


        number.setBackground(
                numberBg
        );


        row.addView(
                number,
                new LinearLayout.LayoutParams(
                        dp(38),
                        dp(38)
                )
        );


        addSpaceHorizontal(
                row,
                10
        );


        LinearLayout info =
                new LinearLayout(this);

        info.setOrientation(
                LinearLayout.VERTICAL
        );


        TextView url =
                makeText(
                        urls.get(index),
                        13,
                        TEXT
                );

        url.setMaxLines(2);


        info.addView(url);


        TextView status =
                makeText(
                        getStatus(index),
                        12,
                        statusColor(
                                getStatus(index)
                        )
                );


        info.addView(status);


        row.addView(
                info,
                new LinearLayout.LayoutParams(
                        0,
                        -2,
                        1
                )
        );


        TextView menu =
                makeText(
                        "⋮",
                        25,
                        SECONDARY
                );

        menu.setGravity(
                Gravity.CENTER
        );


        row.addView(
                menu,
                new LinearLayout.LayoutParams(
                        dp(35),
                        dp(45)
                )
        );


        card.addView(row);


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

        card.setLayoutParams(params);


        card.setOnClickListener(
                v -> {

                    if (!processing &&
                            index < urls.size()) {

                        startSingle(index);
                    }
                }
        );


        return card;
    }


    // =========================
    // PROGRESS PAGE
    // =========================

    private void refreshProgressPage() {

        if (progressList == null) {
            return;
        }


        progressList.removeAllViews();


        int active = 0;


        for (int i = 0;
             i < urls.size();
             i++) {

            String status =
                    getStatus(i);


            if (status.startsWith(
                    "Download "
            ) ||
                    status.equals(
                            "Download berjalan"
                    ) ||
                    status.equals(
                            "Download dimulai"
                    ) ||
                    status.equals(
                            "MP4 HD dipilih"
                    ) ||
                    status.equals(
                            "Caption ditemukan"
                    ) ||
                    status.equals(
                            "Memproses"
                    ) ||
                    status.contains(
                            "Menunggu hasil"
                    )) {

                active++;


                progressList.addView(
                        createProgressCard(i)
                );
            }
        }


        if (active == 0) {

            TextView empty =
                    makeText(
                            "Tidak ada download yang sedang berjalan.",
                            14,
                            SECONDARY
                    );

            empty.setGravity(
                    Gravity.CENTER
            );

            empty.setPadding(
                    0,
                    dp(50),
                    0,
                    0
            );


            progressList.addView(
                    empty
            );
        }


        progressSubtitle.setText(
                active +
                        " video sedang diproses"
        );
    }


    private View createProgressCard(
            int index) {

        LinearLayout card =
                roundedCard(
                        CARD,
                        18
                );


        card.setPadding(
                dp(14),
                dp(14),
                dp(14),
                dp(14)
        );


        TextView title =
                makeText(
                        "TikTok #" +
                                (index + 1),
                        16,
                        TEXT
                );

        title.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );


        card.addView(title);


        TextView url =
                makeText(
                        urls.get(index),
                        12,
                        SECONDARY
                );

        url.setMaxLines(2);


        card.addView(url);


        addSpace(
                card,
                8
        );


        ProgressBar progressBar =
                new ProgressBar(
                        this,
                        null,
                        android.R.attr.progressBarStyleHorizontal
                );


        progressBar.setMax(100);


        int percent =
                index < percentages.size()
                        ? percentages.get(index)
                        : 0;


        progressBar.setProgress(
                percent
        );


        card.addView(
                progressBar,
                new LinearLayout.LayoutParams(
                        -1,
                        dp(8)
                )
        );


        LinearLayout progressRow =
                new LinearLayout(this);

        progressRow.setGravity(
                Gravity.CENTER_VERTICAL
        );


        TextView status =
                makeText(
                        getStatus(index),
                        12,
                        SECONDARY
                );


        TextView percentage =
                makeText(
                        percent + "%",
                        13,
                        BLUE
                );

        percentage.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );


        progressRow.addView(
                status,
                new LinearLayout.LayoutParams(
                        0,
                        -2,
                        1
                )
        );


        progressRow.addView(
                percentage
        );


        card.addView(
                progressRow
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

        card.setLayoutParams(params);


        return card;
    }


    // =========================
    // FINISHED PAGE
    // =========================

    private void refreshFinishedPage() {

        if (finishedList == null) {
            return;
        }


        finishedList.removeAllViews();


        int finished = 0;


        for (int i = 0;
             i < urls.size();
             i++) {

            if ("Selesai".equals(
                    getStatus(i)
            )) {

                finished++;


                finishedList.addView(
                        createFinishedCard(i)
                );
            }
        }


        if (finished == 0) {

            TextView empty =
                    makeText(
                            "Belum ada video yang selesai.",
                            14,
                            SECONDARY
                    );

            empty.setGravity(
                    Gravity.CENTER
            );

            empty.setPadding(
                    0,
                    dp(50),
                    0,
                    0
            );


            finishedList.addView(
                    empty
            );
        }


        finishedSubtitle.setText(
                finished +
                        " video berhasil diunduh"
        );
    }


    private View createFinishedCard(
            int index) {

        LinearLayout card =
                roundedCard(
                        CARD,
                        18
                );


        card.setPadding(
                dp(14),
                dp(14),
                dp(14),
                dp(14)
        );


        LinearLayout row =
                new LinearLayout(this);

        row.setGravity(
                Gravity.CENTER_VERTICAL
        );


        TextView icon =
                makeText(
                        "✓",
                        22,
                        GREEN
                );

        icon.setGravity(
                Gravity.CENTER
        );


        GradientDrawable iconBg =
                new GradientDrawable();

        iconBg.setColor(
                Color.rgb(
                        220,
                        252,
                        231
                )
        );

        iconBg.setShape(
                GradientDrawable.OVAL
        );


        icon.setBackground(
                iconBg
        );


        row.addView(
                icon,
                new LinearLayout.LayoutParams(
                        dp(45),
                        dp(45)
                )
        );


        addSpaceHorizontal(
                row,
                12
        );


        LinearLayout info =
                new LinearLayout(this);

        info.setOrientation(
                LinearLayout.VERTICAL
        );


        TextView title =
                makeText(
                        "TikTok #" +
                                (index + 1),
                        15,
                        TEXT
                );

        title.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );


        info.addView(title);


        TextView done =
                makeText(
                        "✓ Selesai",
                        12,
                        GREEN
                );


        info.addView(done);


        TextView saved =
                makeText(
                        "Video + caption tersimpan",
                        12,
                        SECONDARY
                );


        info.addView(saved);


        row.addView(
                info,
                new LinearLayout.LayoutParams(
                        0,
                        -2,
                        1
                )
        );


        TextView menu =
                makeText(
                        "⋮",
                        24,
                        SECONDARY
                );

        menu.setGravity(
                Gravity.CENTER
        );


        row.addView(
                menu,
                new LinearLayout.LayoutParams(
                        dp(30),
                        dp(45)
                )
        );


        card.addView(row);


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

        card.setLayoutParams(params);


        return card;
    }


    // =========================
    // STATISTICS
    // =========================

    private void updateStats() {

        int total =
                urls.size();

        int processingTotal = 0;

        int successTotal = 0;

        int failedTotal = 0;


        for (int i = 0;
             i < urls.size();
             i++) {

            String s =
                    getStatus(i);


            if ("Selesai".equals(s)) {

                successTotal++;

            } else if (
                    s.startsWith("Gagal")
            ) {

                failedTotal++;

            } else if (
                    !s.equals("Menunggu")
            ) {

                processingTotal++;
            }
        }


        if (downloadCount != null) {

            downloadCount.setText(
                    String.valueOf(total)
            );
        }


        if (processingCount != null) {

            processingCount.setText(
                    String.valueOf(
                            processingTotal
                    )
            );
        }


        if (successCount != null) {

            successCount.setText(
                    String.valueOf(
                            successTotal
                    )
            );
        }


        if (failedCount != null) {

            failedCount.setText(
                    String.valueOf(
                            failedTotal
                    )
            );
        }
    }


    private void setItemStatus(
            int index,
            String status) {

        if (index < 0 ||
                index >= statuses.size()) {

            return;
        }


        statuses.set(
                index,
                status
        );


        updateStats();

        refreshDownloadPage();

        refreshProgressPage();

        refreshFinishedPage();
    }


    private String getStatus(
            int index) {

        if (index < 0 ||
                index >= statuses.size()) {

            return "Menunggu";
        }


        return statuses.get(index);
    }


    private int statusColor(
            String status) {

        if (status.equals("Selesai")) {

            return GREEN;
        }


        if (status.startsWith("Gagal")) {

            return RED;
        }


        if (status.startsWith("Download")) {

            return BLUE;
        }


        return SECONDARY;
    }


    // =========================
    // START
    // =========================

    private void startSingle(
            int index) {

        if (index < 0 ||
                index >= urls.size()) {

            return;
        }


        if (processing) {

            Toast.makeText(
                    this,
                    "Sedang memproses link #" +
                            (currentIndex + 1),
                    Toast.LENGTH_SHORT
            ).show();

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


        setItemStatus(
                index,
                "Memproses"
        );


        webView.setVisibility(
                View.GONE
        );


        webView.loadUrl(
                SAVE_URL
        );


        showPage(0);
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


        if (processing) {

            Toast.makeText(
                    this,
                    "Antrean sedang berjalan.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }


        int first =
                findFirstUnfinished();


        if (first == -1) {

            Toast.makeText(
                    this,
                    "Semua link sudah selesai.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }


        startSingle(first);
    }


    private int findFirstUnfinished() {

        for (int i = 0;
             i < urls.size();
             i++) {

            if (!"Selesai".equals(
                    getStatus(i)
            )) {

                return i;
            }
        }


        return -1;
    }


    // =========================
    // WEBVIEW
    // =========================

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

                            return;
                        }


                        if (!isSaveTikTokPage(url)) {

                            return;
                        }


                        if (!submitClicked) {

                            handler.postDelayed(
                                    () ->
                                            waitForSaveTikTokInput(
                                                    urls.get(currentIndex),
                                                    0
                                            ),
                                    1000
                            );
                        }
                    }


                    @Override
                    public void onReceivedError(
                            WebView view,
                            WebResourceRequest request,
                            WebResourceError error) {

                        if (request.isForMainFrame()) {

                            if (currentIndex >= 0) {

                                setItemStatus(
                                        currentIndex,
                                        "Gagal memuat halaman"
                                );
                            }

                            processing = false;
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
            String url) {

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


    // =========================
    // FIND INPUT
    // =========================

    private void waitForSaveTikTokInput(
            String tiktokUrl,
            int attempt) {

        if (!processing ||
                currentIndex < 0 ||
                submitClicked) {

            return;
        }


        if (attempt >= MAX_INPUT_CHECKS) {

            setItemStatus(
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
                            result.contains("FOUND")) {

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


    // =========================
    // SUBMIT URL
    // =========================

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

                        "if(field.value!==" +
                        safeUrl +
                        ")" +
                        "return 'VALUE_FAILED';" +

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
                            result.contains("OK")) {

                        submitClicked = true;


                        setItemStatus(
                                currentIndex,
                                "Menunggu hasil"
                        );


                        handler.postDelayed(
                                () ->
                                        findMp4HdButton(0),
                                2000
                        );

                    } else {

                        submitClicked = false;


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


    // =========================
    // CAPTION + MP4 HD
    // =========================

    private void findMp4HdButton(
            int attempt) {

        if (!processing ||
                currentIndex < 0 ||
                downloadStarted) {

            return;
        }


        if (attempt >= MAX_MP4_HD_CHECKS) {

            setItemStatus(
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
                        "caption:caption" +
                        "});" +

                        "})()";


        webView.evaluateJavascript(
                js,
                result -> {

                    if (result == null ||
                            !result.contains(
                                    "\\\"state\\\":\\\"FOUND\\\""
                            )) {

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

                            setItemStatus(
                                    currentIndex,
                                    "Caption kosong"
                            );


                            processing = false;

                            return;
                        }


                        captions.set(
                                currentIndex,
                                currentCaption
                        );


                        setItemStatus(
                                currentIndex,
                                "Caption ditemukan"
                        );


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


                                        setItemStatus(
                                                currentIndex,
                                                "MP4 HD dipilih"
                                        );

                                    } else {

                                        setItemStatus(
                                                currentIndex,
                                                "Gagal klik MP4 HD"
                                        );


                                        processing = false;
                                    }
                                }
                        );


                    } catch (Exception e) {

                        setItemStatus(
                                currentIndex,
                                "Gagal membaca caption"
                        );


                        processing = false;
                    }
                }
        );
    }


    // =========================
    // DOWNLOAD EVENT
    // =========================

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


        boolean html =
                lowerMime.contains(
                        "text/html"
                ) ||
                        lowerMime.contains(
                                "application/xhtml"
                        );


        if (html) {

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


        boolean video =
                lowerMime.startsWith("video/") ||
                        lowerMime.contains("mp4") ||
                        lowerDisposition.contains(".mp4") ||
                        lowerUrl.contains(".mp4");


        if (isBin &&
                submitClicked) {

            video = true;
        }


        if (!video) {

            return;
        }


        handledDownloadUrls.add(
                normalizedUrl
        );


        downloadStarted = true;


        setItemStatus(
                currentIndex,
                "Download dimulai"
        );


        enqueueDownload(
                normalizedUrl,
                userAgent,
                mimeType,
                contentDisposition
        );
    }


    // =========================
    // ENQUEUE DOWNLOAD
    // =========================

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
                            Uri.parse(url)
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
                            folderNumber
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


            request.setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    folder + "/video.mp4"
            );


            currentDownloadId =
                    manager.enqueue(request);


            if (currentDownloadId < 0) {

                throw new Exception(
                        "DownloadManager menolak request"
                );
            }


            setItemStatus(
                    currentIndex,
                    "Download 0%"
            );


            percentages.set(
                    currentIndex,
                    0
            );


            saveCaptionFile(
                    currentIndex
            );


            Toast.makeText(
                    this,
                    "Download dimulai #" +
                            folderNumber,
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


            setItemStatus(
                    currentIndex,
                    "Gagal memulai download"
            );
        }
    }


    // =========================
    // DOWNLOAD MONITOR V8
    // =========================

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


        if (attempt >= MAX_DOWNLOAD_CHECKS) {

            setItemStatus(
                    completedIndex,
                    "Download belum selesai"
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
                        manager.query(query)
        ) {


            if (cursor != null &&
                    cursor.moveToFirst()) {


                int statusColumn =
                        cursor.getColumnIndex(
                                DownloadManager.COLUMN_STATUS
                        );


                if (statusColumn >= 0) {

                    int status =
                            cursor.getInt(
                                    statusColumn
                            );


                    if (status ==
                            DownloadManager.STATUS_SUCCESSFUL) {


                        percentages.set(
                                completedIndex,
                                100
                        );


                        setItemStatus(
                                completedIndex,
                                "Selesai"
                        );


                        processing = false;

                        downloadStarted = false;

                        currentDownloadId = -1L;


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


                        setItemStatus(
                                completedIndex,
                                "Gagal (" +
                                        reason +
                                        ")"
                        );


                        processing = false;

                        downloadStarted = false;

                        currentDownloadId = -1L;


                        return;
                    }


                    int downloadedColumn =
                            cursor.getColumnIndex(
                                    DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR
                            );


                    int totalColumn =
                            cursor.getColumnIndex(
                                    DownloadManager.COLUMN_TOTAL_SIZE_BYTES
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
                                                downloaded *
                                                        100L /
                                                        total
                                        )
                                );


                        percentages.set(
                                completedIndex,
                                percent
                        );


                        setItemStatus(
                                completedIndex,
                                "Download " +
                                        percent +
                                        "%"
                        );


                    } else {

                        setItemStatus(
                                completedIndex,
                                "Download berjalan"
                        );
                    }
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
                DOWNLOAD_CHECK_INTERVAL
        );
    }


    // =========================
    // NEXT ITEM
    // =========================

    private void finishCurrentAndNext(
            int completedIndex) {

        processing = false;

        submitClicked = false;

        downloadStarted = false;

        currentDownloadId = -1L;

        currentCaption = "";


        int next =
                completedIndex + 1;


        while (next < urls.size() &&
                "Selesai".equals(
                        getStatus(next)
                )) {

            next++;
        }


        if (next < urls.size()) {

            startSingle(next);

        } else {

            currentIndex = -1;


            updateStats();

            refreshAllLists();


            showPage(2);


            Toast.makeText(
                    this,
                    "Semua antrean sudah selesai.",
                    Toast.LENGTH_LONG
            ).show();
        }
    }


    // =========================
    // CAPTION FILE
    // =========================

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


            if (android.os.Build.VERSION.SDK_INT >=
                    android.os.Build.VERSION_CODES.Q) {


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
                                android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
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
                                Environment.getExternalStoragePublicDirectory(
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
                                new java.io.FileOutputStream(file)
                ) {

                    out.write(
                            content.getBytes("UTF-8")
                    );
                }
            }


        } catch (Exception ignored) {
        }
    }


    // =========================
    // CLEAR
    // =========================

    private void clearQueue() {

        handler.removeCallbacksAndMessages(
                null
        );


        urls.clear();

        statuses.clear();

        percentages.clear();

        captions.clear();


        currentIndex = -1;

        processing = false;

        submitClicked = false;

        downloadStarted = false;

        currentCaption = "";

        currentDownloadId = -1L;


        handledDownloadUrls.clear();


        input.setText("");


        refreshAllLists();

        updateStats();


        showPage(0);


        webView.stopLoading();

        webView.setVisibility(
                View.GONE
        );
    }


    // =========================
    // UI HELPERS
    // =========================

    private TextView makeText(
            String value,
            float size,
            int color) {

        TextView view =
                new TextView(this);

        view.setText(value);

        view.setTextSize(size);

        view.setTextColor(color);

        view.setGravity(
                Gravity.CENTER_VERTICAL
        );


        return view;
    }


    private LinearLayout roundedCard(
            int color,
            int radius) {

        LinearLayout layout =
                new LinearLayout(this);


        GradientDrawable bg =
                new GradientDrawable();


        bg.setColor(color);

        bg.setCornerRadius(
                dp(radius)
        );


        layout.setBackground(bg);


        return layout;
    }


    private Button modernButton(
            String text,
            boolean primary) {

        Button button =
                new Button(this);


        button.setText(text);

        button.setAllCaps(false);

        button.setTextSize(13);

        button.setTextColor(
                primary
                        ? Color.WHITE
                        : TEXT
        );


        GradientDrawable bg =
                new GradientDrawable();


        bg.setColor(
                primary
                        ? BLUE
                        : Color.WHITE
        );


        bg.setCornerRadius(
                dp(14)
        );


        button.setBackground(bg);


        return button;
    }


    private Button blueButton(
            String text) {

        return modernButton(
                text,
                true
        );
    }


    private TextView statCard(
            String number,
            String label,
            int color) {

        LinearLayout card =
                roundedCard(
                        CARD,
                        15
                );


        card.setOrientation(
                LinearLayout.VERTICAL
        );

        card.setGravity(
                Gravity.CENTER
        );


        TextView numberView =
                makeText(
                        number,
                        20,
                        color
                );

        numberView.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        numberView.setGravity(
                Gravity.CENTER
        );


        TextView labelView =
                makeText(
                        label,
                        11,
                        SECONDARY
                );

        labelView.setGravity(
                Gravity.CENTER
        );


        card.addView(numberView);

        card.addView(labelView);


        return numberView;
    }


    private TextView miniStat(
            String number,
            String label,
            int color) {

        LinearLayout box =
                roundedCard(
                        Color.TRANSPARENT,
                        0
                );

        box.setOrientation(
                LinearLayout.VERTICAL
        );

        box.setGravity(
                Gravity.CENTER
        );


        TextView numberView =
                makeText(
                        number,
                        20,
                        color
                );

        numberView.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        numberView.setGravity(
                Gravity.CENTER
        );


        TextView labelView =
                makeText(
                        label,
                        10,
                        SECONDARY
                );

        labelView.setGravity(
                Gravity.CENTER
        );


        box.addView(numberView);

        box.addView(labelView);


        return numberView;
    }


    private void addStat(
            LinearLayout parent,
            TextView numberView) {

        View card =
                (View) numberView.getParent();


        if (card != null) {

            ((android.view.ViewGroup) card)
                    .removeView(numberView);

            parent.addView(
                    card,
                    new LinearLayout.LayoutParams(
                            0,
                            dp(75),
                            1
                    )
            );
        }
    }


    private void addSpace(
            LinearLayout parent,
            int height) {

        Space space =
                new Space(this);


        parent.addView(
                space,
                new LinearLayout.LayoutParams(
                        1,
                        dp(height)
                )
        );
    }


    private void addSpaceHorizontal(
            LinearLayout parent,
            int width) {

        Space space =
                new Space(this);


        parent.addView(
                space,
                new LinearLayout.LayoutParams(
                        dp(width),
                        1
                )
        );
    }


    private int dp(int value) {

        return Math.round(
                value *
                        getResources()
                                .getDisplayMetrics()
                                .density
        );
    }


    // =========================
    // STORAGE
    // =========================

    private void requestStorageIfNeeded() {

        if (Build.VERSION.SDK_INT >= 23 &&
                Build.VERSION.SDK_INT <= 28 &&
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


    // =========================
    // BACK
    // =========================

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
