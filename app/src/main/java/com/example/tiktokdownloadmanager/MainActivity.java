package com.example.tiktokdownloadmanager;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.view.Gravity;
import android.view.View;
import android.webkit.*;
import android.widget.*;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final String SAVE_URL =
            "https://savetiktok.to/id";

    private static final int REQ_STORAGE = 501;
    private static final int MAX_RESULT_CHECKS = 45;

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

    private int resultChecks = 0;

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        buildUi();
        requestStorageIfNeeded();
    }

    private void buildUi() {

        ScrollView scrollView =
                new ScrollView(this);

        LinearLayout root =
                new LinearLayout(this);

        root.setOrientation(
                LinearLayout.VERTICAL
        );

        root.setPadding(
                22, 22, 22, 22
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

        input.setGravity(Gravity.TOP);

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

        LinearLayout.LayoutParams clearParams =
                new LinearLayout.LayoutParams(
                        0,
                        62,
                        1
                );

        clearParams.setMargins(
                10, 0, 0, 0
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
                        "WebView: siap",
                        14
                );

        root.addView(webStatus);

        Button startAll =
                button("Mulai Semua");

        root.addView(startAll);

        root.addView(
                text(
                        "Aplikasi memproses URL melalui halaman " +
                        "SaveTikTok di WebView. CAPTCHA, login, " +
                        "rate limit, dan mekanisme keamanan tidak " +
                        "dilewati. Jika situs meminta tindakan " +
                        "manual, selesaikan secara manual.",
                        14
                )
        );

        queue =
                new LinearLayout(this);

        queue.setOrientation(
                LinearLayout.VERTICAL
        );

        root.addView(queue);

        webView =
                new WebView(this);

        webView.setVisibility(
                WebView.GONE
        );

        root.addView(
                webView,
                new LinearLayout.LayoutParams(
                        -1,
                        650
                )
        );

        configureWebView();

        load.setOnClickListener(
                v -> loadQueue()
        );

        clear.setOnClickListener(
                v -> clearQueue()
        );

        startAll.setOnClickListener(
                v -> startAll()
        );

        scrollView.addView(root);

        setContentView(scrollView);
    }

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

        /*
         * JS Bridge V5.1
         *
         * Bagian ini sengaja didefinisikan
         * secara eksplisit agar compiler
         * mengenali class JsBridge.
         */
        webView.addJavascriptInterface(
                new JsBridge(),
                "AndroidBridge"
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

                        if (
                                processing &&
                                submitClicked
                        ) {

                            webStatus.setText(
                                    "WebView: memantau hasil..."
                            );

                        } else {

                            webStatus.setText(
                                    "WebView: halaman siap"
                            );
                        }

                        if (
                                processing &&
                                currentIndex >= 0 &&
                                isSaveTikTokPage(url) &&
                                !submitClicked
                        ) {

                            handler.postDelayed(
                                    () -> injectTikTokUrl(
                                            urls.get(currentIndex)
                                    ),
                                    800
                            );
                        }
                    }

                    @Override
                    public void onReceivedError(
                            WebView view,
                            WebResourceRequest request,
                            WebResourceError error
                    ) {

                        if (
                                request.isForMainFrame()
                        ) {

                            webStatus.setText(
                                    "WebView: gagal memuat halaman"
                            );
                        }
                    }
                }
        );

        webView.setWebChromeClient(
                new WebChromeClient()
        );

        /*
         * DownloadListener hanya dipanggil
         * ketika WebView benar-benar menerima
         * proses download.
         */
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

    /*
     * ============================================================
     * JS BRIDGE
     * ============================================================
     *
     * Ini adalah bagian yang sebelumnya hilang
     * sehingga muncul:
     *
     * cannot find symbol
     * class JsBridge
     */
    private class JsBridge {

        @android.webkit.JavascriptInterface
        public void status(
                final String message
        ) {

            runOnUiThread(
                    () -> {

                        if (
                                processing &&
                                !downloadStarted
                        ) {

                            webStatus.setText(
                                    "WebView: " +
                                    message
                            );
                        }
                    }
            );
        }

        @android.webkit.JavascriptInterface
        public void downloadLink(
                final String url
        ) {

            runOnUiThread(
                    () -> {

                        if (
                                !processing ||
                                downloadStarted
                        ) {
                            return;
                        }

                        if (
                                url == null ||
                                url.trim().isEmpty()
                        ) {
                            return;
                        }

                        /*
                         * V5.1 tidak lagi mengambil
                         * sembarang href sebagai MP4.
                         *
                         * Fungsi ini hanya digunakan
                         * untuk link yang memang dikenali
                         * sebagai hasil video oleh JS.
                         */
                        downloadStarted = true;

                        webStatus.setText(
                                "WebView: link video ditemukan"
                        );

                        enqueueDownload(
                                url,
                                null,
                                "video/mp4",
                                null
                        );
                    }
            );
        }
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

        LinkedHashSet<String> uniqueUrls =
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
                    url.startsWith("http://") ||
                    url.startsWith("https://")
            ) {

                uniqueUrls.add(url);
            }
        }

        urls.clear();

        urls.addAll(uniqueUrls);

        currentIndex = -1;

        processing = false;
        submitClicked = false;
        downloadStarted = false;

        resultChecks = 0;

        queue.removeAllViews();

        for (
                int i = 0;
                i < urls.size();
                i++
        ) {

            addCard(
                    i,
                    urls.get(i)
            );
        }

        updateProgress();
    }

    private void addCard(
            int index,
            String url
    ) {

        LinearLayout card =
                new LinearLayout(this);

        card.setOrientation(
                LinearLayout.VERTICAL
        );

        card.setPadding(
                14, 14, 14, 14
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

    private void startSingle(
            int index
    ) {

        if (
                index < 0 ||
                index >= urls.size()
        ) {
            return;
        }

        currentIndex = index;

        processing = true;

        submitClicked = false;

        downloadStarted = false;

        resultChecks = 0;

        webStatus.setText(
                "WebView: membuka SaveTikTok..."
        );

        setStatus(
                index,
                "Memproses"
        );

        webView.setVisibility(
                View.VISIBLE
        );

        webView.loadUrl(
                SAVE_URL
        );
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

    private void injectTikTokUrl(
            String tiktokUrl
    ) {

        if (
                !processing ||
                currentIndex < 0 ||
                submitClicked
        ) {
            return;
        }

        String safeUrl =
                tiktokUrl
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
                                "\\r"
                        );

        String javascript =
                "(function(){"

                + "var u='" +
                safeUrl +
                "';"

                + "var fields=[].slice.call("
                + "document.querySelectorAll("
                + "'input,textarea'"
                + "));"

                + "var field=fields.find(function(e){"

                + "var p=("
                + "(e.placeholder||'')+' '"
                + "+(e.getAttribute('aria-label')||'')"
                + ").toLowerCase();"

                + "return e.type==='url'"
                + "||p.includes('link')"
                + "||p.includes('url')"
                + "||p.includes('tautan')"
                + "||p.includes('username');"

                + "});"

                + "if(!field&&fields.length)"
                + "field=fields[0];"

                + "if(!field){"

                + "AndroidBridge.status("
                + "'Kolom URL tidak ditemukan'"
                + ");"

                + "return 'NO_INPUT';"

                + "}"

                + "var proto="
                + "Object.getPrototypeOf(field);"

                + "var descriptor="
                + "Object.getOwnPropertyDescriptor("
                + "proto,"
                + "'value'"
                + ");"

                + "if(descriptor&&descriptor.set){"

                + "descriptor.set.call("
                + "field,"
                + "u"
                + ");"

                + "}else{"

                + "field.value=u;"

                + "}"

                + "field.dispatchEvent("
                + "new Event("
                + "'input',"
                + "{bubbles:true}"
                + ")"
                + ");"

                + "field.dispatchEvent("
                + "new Event("
                + "'change',"
                + "{bubbles:true}"
                + ")"
                + ");"

                + "field.focus();"

                + "var buttons=[].slice.call("
                + "document.querySelectorAll("
                + "'button,input[type=submit],"
                + "input[type=button],a'"
                + ")"
                + ");"

                + "var button=buttons.find("
                + "function(e){"

                + "var t=("
                + "e.innerText||"
                + "e.value||"
                + "e.textContent||''"
                + ").toLowerCase()"
                + ".trim();"

                + "return /unduh|download|"
                + "tải xuống|descarga/.test(t);"

                + "});"

                + "if(button){"

                + "button.click();"

                + "AndroidBridge.status("
                + "'URL dimasukkan dan tombol Unduh ditekan'"
                + ");"

                + "return 'OK';"

                + "}"

                + "AndroidBridge.status("
                + "'Tombol Unduh tidak ditemukan'"
                + ");"

                + "return 'NO_BUTTON';"

                + "})()";

        webView.evaluateJavascript(
                javascript,
                value -> {

                    submitClicked = true;

                    resultChecks = 0;

                    webStatus.setText(
                            "WebView: menunggu hasil video..."
                    );

                    handler.postDelayed(
                            this::scanResultPage,
                            1000
                    );
                }
        );
    }

    /*
     * ============================================================
     * SCAN HASIL
     * ============================================================
     *
     * V5.1 tidak mengambil sembarang href.
     * Kita mencari tombol hasil dan mengkliknya.
     */
    private void scanResultPage() {

        if (
                !processing ||
                currentIndex < 0 ||
                downloadStarted
        ) {
            return;
        }

        if (
                resultChecks >=
                MAX_RESULT_CHECKS
        ) {

            webStatus.setText(
                    "WebView: hasil belum ditemukan"
            );

            setStatus(
                    currentIndex,
                    "Hasil belum ditemukan / perlu tindakan manual"
            );

            return;
        }

        resultChecks++;

        webStatus.setText(
                "WebView: mencari MP4/HD... " +
                resultChecks +
                "/" +
                MAX_RESULT_CHECKS
        );

        String javascript =
                "(function(){"

                + "function text(e){"

                + "return (("
                + "e.innerText||"
                + "e.textContent||"
                + "e.value||"
                + "e.getAttribute('aria-label')||"
                + "e.getAttribute('title')||''"
                + ")+'')"
                + ".toLowerCase()"
                + ".replace(/\\s+/g,' ')"
                + ".trim();"

                + "}"

                + "function visible(e){"

                + "if(!e)return false;"

                + "var s=getComputedStyle(e);"

                + "return s.display!=='none'"
                + "&&s.visibility!=='hidden'"
                + "&&e.offsetWidth>0"
                + "&&e.offsetHeight>0;"

                + "}"

                + "function score(e){"

                + "var t=text(e);"

                + "var parent=e.parentElement"
                + "?text(e.parentElement):'';"

                + "var all=t+' '+parent;"

                + "var score=0;"

                + "if(/\\bmp4\\b/.test(all))"
                + "score+=120;"

                + "if(/\\bhd\\b/.test(all))"
                + "score+=100;"

                + "if(/\\b4k\\b/.test(all))"
                + "score+=90;"

                + "if(/video/.test(all))"
                + "score+=50;"

                + "if(/download|unduh|"
                + "tải xuống/.test(all))"
                + "score+=40;"

                + "if(/mp3|audio|music/.test(all))"
                + "score-=200;"

                + "if(/share|facebook|"
                + "instagram|twitter|"
                + "telegram|whatsapp/.test(all))"
                + "score-=200;"

                + "return score;"

                + "}"

                + "var elements=[].slice.call("
                + "document.querySelectorAll("
                + "'a,button,"
                + "input[type=button],"
                + "input[type=submit],"
                + "[role=button]'"
                + ")"
                + ");"

                + "var candidates=[];"

                + "elements.forEach(function(e){"

                + "if(!visible(e))return;"

                + "if(e.dataset&&"
                + "e.dataset.tdmClicked==='1')"
                + "return;"

                + "var s=score(e);"

                + "if(s>=100){"

                + "candidates.push({"
                + "element:e,"
                + "score:s"
                + "});"

                + "}"

                + "});"

                + "candidates.sort(function(a,b){"
                + "return b.score-a.score;"
                + "});"

                + "if(candidates.length){"

                + "var selected="
                + "candidates[0].element;"

                + "if(selected.dataset){"

                + "selected.dataset.tdmClicked='1';"

                + "}"

                + "selected.click();"

                + "AndroidBridge.status("
                + "'Tombol hasil MP4/HD ditekan'"
                + ");"

                + "return 'CLICK';"

                + "}"

                + "var mp4=elements.find("
                + "function(e){"

                + "if(!visible(e))return false;"

                + "if(e.dataset&&"
                + "e.dataset.tdmClicked==='1')"
                + "return false;"

                + "var t=text(e);"

                + "return /\\bmp4\\b/.test(t)"
                + "&&!/mp3|audio/.test(t);"

                + "});"

                + "if(mp4){"

                + "if(mp4.dataset){"

                + "mp4.dataset.tdmClicked='1';"

                + "}"

                + "mp4.click();"

                + "AndroidBridge.status("
                + "'Pilihan MP4 ditemukan'"
                + ");"

                + "return 'CLICK';"

                + "}"

                + "var quality=elements.find("
                + "function(e){"

                + "if(!visible(e))return false;"

                + "if(e.dataset&&"
                + "e.dataset.tdmClicked==='1')"
                + "return false;"

                + "var t=text(e);"

                + "return /\\bhd\\b|\\b4k\\b/.test(t)"
                + "&&!/mp3|audio/.test(t);"

                + "});"

                + "if(quality){"

                + "if(quality.dataset){"

                + "quality.dataset.tdmClicked='1';"

                + "}"

                + "quality.click();"

                + "AndroidBridge.status("
                + "'Pilihan kualitas HD/4K ditemukan'"
                + ");"

                + "return 'CLICK';"

                + "}"

                + "var body=text(document.body);"

                + "if(/captcha|"
                + "verify you are human|"
                + "verifikasi bahwa anda manusia/"
                + ".test(body)){"

                + "AndroidBridge.status("
                + "'CAPTCHA/verifikasi perlu tindakan manual'"
                + ");"

                + "return 'CAPTCHA';"

                + "}"

                + "return 'WAIT';"

                + "})()";

        webView.evaluateJavascript(
                javascript,
                value -> {

                    if (
                            !processing ||
                            downloadStarted
                    ) {
                        return;
                    }

                    handler.postDelayed(
                            this::scanResultPage,
                            1200
                    );
                }
        );
    }

    /*
     * ============================================================
     * DOWNLOAD LISTENER
     * ============================================================
     */

    private void handleWebDownload(
            String url,
            String userAgent,
            String contentDisposition,
            String mimeType,
            long contentLength
    ) {

        if (
                !processing ||
                downloadStarted
        ) {
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

        /*
         * Jangan simpan HTML sebagai MP4.
         */
        if (
                mime.contains("text/html") ||
                mime.contains("application/xhtml")
        ) {

            webStatus.setText(
                    "WebView: respons bukan video"
            );

            return;
        }

        boolean isVideo =
                mime.startsWith("video/") ||
                mime.contains("mp4") ||
                disposition.contains(".mp4");

        if (!isVideo) {

            webStatus.setText(
                    "WebView: file bukan video MP4"
            );

            return;
        }

        if (
                url == null ||
                url.trim().isEmpty()
        ) {
            return;
        }

        downloadStarted = true;

        webStatus.setText(
                "WebView: file video valid ditemukan"
        );

        enqueueDownload(
                url,
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

        if (
                url == null ||
                url.trim().isEmpty()
        ) {
            return;
        }

        try {

            Uri uri =
                    Uri.parse(url);

            String scheme =
                    uri.getScheme();

            if (
                    scheme == null ||
                    (
                            !scheme.equalsIgnoreCase(
                                    "http"
                            )
                            &&
                            !scheme.equalsIgnoreCase(
                                    "https"
                            )
                    )
            ) {

                downloadStarted = false;

                webStatus.setText(
                        "WebView: link download tidak valid"
                );

                return;
            }

            DownloadManager manager =
                    (DownloadManager)
                            getSystemService(
                                    DOWNLOAD_SERVICE
                            );

            DownloadManager.Request request =
                    new DownloadManager.Request(
                            uri
                    );

            request.setTitle(
                    "TikTok " +
                    (currentIndex + 1)
            );

            request.setDescription(
                    "TikTok Download Manager"
            );

            request.setNotificationVisibility(
                    DownloadManager
                            .Request
                            .VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            );

            if (
                    mimeType != null &&
                    !mimeType.isEmpty()
            ) {

                request.setMimeType(
                        mimeType
                );

            } else {

                request.setMimeType(
                        "video/mp4"
                );
            }

            if (
                    userAgent != null &&
                    !userAgent.isEmpty()
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
                    cookie != null &&
                    !cookie.isEmpty()
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

            String filename =
                    URLUtil.guessFileName(
                            url,
                            contentDisposition,
                            mimeType
                    );

            if (
                    filename == null ||
                    filename.trim().isEmpty() ||
                    filename.equals(
                            "downloadfile"
                    )
            ) {

                filename =
                        "tiktok_" +
                        System.currentTimeMillis() +
                        ".mp4";
            }

            if (
                    !filename
                            .toLowerCase(
                                    Locale.US
                            )
                            .endsWith(".mp4")
            ) {

                filename += ".mp4";
            }

            request.setDestinationInExternalPublicDir(
                    android.os.Environment
                            .DIRECTORY_DOWNLOADS,
                    filename
            );

            manager.enqueue(request);

            int completedIndex =
                    currentIndex;

            setStatus(
                    completedIndex,
                    "Download dimulai"
            );

            progress.setText(
                    "Progress: " +
                    (completedIndex + 1) +
                    " / " +
                    urls.size()
            );

            handler.postDelayed(
                    () -> {

                        processing = false;

                        submitClicked = false;

                        downloadStarted = false;

                        int next =
                                completedIndex + 1;

                        if (
                                next < urls.size()
                        ) {

                            startSingle(next);

                        } else {

                            webStatus.setText(
                                    "WebView: semua antrean selesai"
                            );

                            Toast.makeText(
                                    this,
                                    "Semua antrean sudah diproses.",
                                    Toast.LENGTH_LONG
                            ).show();
                        }

                    },
                    1800
            );

        } catch (Exception e) {

            downloadStarted = false;

            setStatus(
                    currentIndex,
                    "Gagal: " +
                    e.getMessage()
            );

            webStatus.setText(
                    "WebView: gagal memulai download"
            );

            processing = false;
        }
    }

    private void clearQueue() {

        urls.clear();

        currentIndex = -1;

        processing = false;

        submitClicked = false;

        downloadStarted = false;

        resultChecks = 0;

        queue.removeAllViews();

        progress.setText(
                "Progress: 0 / 0"
        );

        webStatus.setText(
                "WebView: siap"
        );
    }

    private void setStatus(
            int index,
            String status
    ) {

        if (
                index < 0 ||
                index >= queue.getChildCount()
        ) {
            return;
        }

        Object tag =
                queue
                        .getChildAt(index)
                        .getTag();

        if (
                tag instanceof TextView
        ) {

            ((TextView) tag).setText(
                    "#" +
                    (index + 1) +
                    "  " +
                    status
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

        if (
                Build.VERSION.SDK_INT >= 23 &&
                Build.VERSION.SDK_INT <= 28 &&
                checkSelfPermission(
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
