package com.example.tiktokdownloadmanager;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;

import java.util.*;

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
    public void onCreate(Bundle b) {

        super.onCreate(b);

        buildUi();

        requestStorageIfNeeded();
    }

    private void buildUi() {

        ScrollView sc =
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

        LinearLayout.LayoutParams cp =
                new LinearLayout.LayoutParams(
                        0,
                        62,
                        1
                );

        cp.setMargins(
                10,
                0,
                0,
                0
        );

        row.addView(clear, cp);

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
                        "rate limit, dan mekanisme keamanan " +
                        "tidak dilewati. Jika situs meminta " +
                        "tindakan manual, selesaikan secara manual.",
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
                View.GONE
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

        sc.addView(root);

        setContentView(sc);
    }

    private void configureWebView() {

        WebSettings s =
                webView.getSettings();

        s.setJavaScriptEnabled(true);

        s.setDomStorageEnabled(true);

        s.setDatabaseEnabled(true);

        s.setAllowFileAccess(false);

        s.setAllowContentAccess(true);

        s.setSupportMultipleWindows(false);

        s.setJavaScriptCanOpenWindowsAutomatically(
                false
        );

        s.setUserAgentString(
                "Mozilla/5.0 (Linux; Android 12) " +
                "AppleWebKit/537.36 " +
                "(KHTML, like Gecko) " +
                "Chrome/140.0 Mobile Safari/537.36"
        );

        webView.addJavascriptInterface(
                new JsBridge(),
                "AndroidBridge"
        );

        webView.setWebViewClient(
                new WebViewClient() {

                    @Override
                    public boolean shouldOverrideUrlLoading(
                            WebView v,
                            WebResourceRequest r
                    ) {
                        return false;
                    }

                    @Override
                    public void onPageFinished(
                            WebView v,
                            String url
                    ) {

                        if (
                                processing &&
                                submitClicked
                        ) {

                            webStatus.setText(
                                    "WebView: memantau hasil video..."
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
                            WebView v,
                            WebResourceRequest r,
                            WebResourceError e
                    ) {

                        if (
                                r.isForMainFrame()
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

        webView.setDownloadListener(
                (
                        url,
                        userAgent,
                        contentDisposition,
                        mimeType,
                        contentLength
                ) -> {

                    enqueueDownload(
                            url,
                            userAgent,
                            mimeType,
                            contentDisposition
                    );
                }
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

    private void loadQueue() {

        LinkedHashSet<String> set =
                new LinkedHashSet<>();

        String raw =
                input.getText()
                        .toString();

        for (
                String x :
                raw.split("\\r?\\n")
        ) {

            x = x.trim();

            if (
                    x.startsWith("http://") ||
                    x.startsWith("https://")
            ) {

                set.add(x);
            }
        }

        urls.clear();

        urls.addAll(set);

        currentIndex = -1;

        processing = false;

        submitClicked = false;

        downloadStarted = false;

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
            int i,
            String url
    ) {

        LinearLayout c =
                new LinearLayout(this);

        c.setOrientation(
                LinearLayout.VERTICAL
        );

        c.setPadding(
                14,
                14,
                14,
                14
        );

        TextView st =
                text(
                        "#" +
                        (i + 1) +
                        "  Menunggu",
                        16
                );

        TextView link =
                text(
                        url,
                        12
                );

        link.setMaxLines(3);

        Button p =
                button(
                        "Proses Link Ini"
                );

        p.setOnClickListener(
                v -> startSingle(i)
        );

        c.addView(st);

        c.addView(link);

        c.addView(p);

        c.setTag(st);

        queue.addView(c);
    }

    private void startSingle(
            int i
    ) {

        if (
                i < 0 ||
                i >= urls.size()
        ) {
            return;
        }

        currentIndex = i;

        processing = true;

        submitClicked = false;

        downloadStarted = false;

        resultChecks = 0;

        webStatus.setText(
                "WebView: membuka SaveTikTok..."
        );

        setStatus(
                i,
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

        String u =
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

        String js =
                "(function(){"

                + "var u='" + u + "';"

                + "var els=[].slice.call("
                + "document.querySelectorAll("
                + "'input,textarea'"
                + ")"
                + ");"

                + "var el=els.find(function(e){"

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

                + "if(!el&&els.length)"
                + "el=els[0];"

                + "if(!el){"

                + "AndroidBridge.status("
                + "'Kolom URL tidak ditemukan'"
                + ");"

                + "return 'NO_INPUT';"

                + "}"

                + "var proto="
                + "Object.getPrototypeOf(el);"

                + "var d="
                + "Object.getOwnPropertyDescriptor("
                + "proto,"
                + "'value'"
                + ");"

                + "if(d&&d.set){"
                + "d.set.call(el,u);"
                + "}else{"
                + "el.value=u;"
                + "}"

                + "el.dispatchEvent("
                + "new Event("
                + "'input',"
                + "{bubbles:true}"
                + ")"
                + ");"

                + "el.dispatchEvent("
                + "new Event("
                + "'change',"
                + "{bubbles:true}"
                + ")"
                + ");"

                + "el.focus();"

                + "var btns=[].slice.call("
                + "document.querySelectorAll("
                + "'button,input[type=submit],"
                + "input[type=button],a'"
                + ")"
                + ");"

                + "var b=btns.find(function(x){"

                + "var t=("
                + "x.innerText||"
                + "x.value||"
                + "x.textContent||''"
                + ").toLowerCase()"
                + ".trim();"

                + "return /unduh|download|"
                + "tải xuống|descarga/.test(t);"

                + "});"

                + "if(b){"

                + "b.click();"

                + "AndroidBridge.status("
                + "'URL dimasukkan dan tombol Unduh ditekan'"
                + ");"

                + "return 'OK';"

                + "}"

                + "AndroidBridge.status("
                + "'URL dimasukkan; tombol Unduh tidak ditemukan'"
                + ");"

                + "return 'INPUT_ONLY';"

                + "})()";

        webView.evaluateJavascript(
                js,
                value -> {

                    submitClicked = true;

                    resultChecks = 0;

                    webStatus.setText(
                            "WebView: menunggu hasil video..."
                    );

                    installResultObserver();

                    handler.postDelayed(
                            this::scanResultPage,
                            1000
                    );
                }
        );
    }

    private void installResultObserver() {

        String js =
                "(function(){"

                + "if(window.__tdmObserver)"
                + "return 'EXISTS';"

                + "window.__tdmObserver="
                + "new MutationObserver(function(){"

                + "if(window.AndroidBridge){"
                + "AndroidBridge.status("
                + "'Perubahan hasil terdeteksi'"
                + ");"
                + "}"

                + "});"

                + "window.__tdmObserver.observe("
                + "document.body,"
                + "{childList:true,"
                + "subtree:true,"
                + "attributes:true}"
                + ");"

                + "return 'INSTALLED';"

                + "})()";

        webView.evaluateJavascript(
                js,
                value -> {
                }
        );
    }

    private class JsBridge {

        @JavascriptInterface
        public void status(
                final String s
        ) {

            runOnUiThread(
                    () -> {

                        if (
                                processing &&
                                !downloadStarted
                        ) {

                            webStatus.setText(
                                    "WebView: " + s
                            );
                        }
                    }
            );
        }

        @JavascriptInterface
        public void downloadLink(
                final String url
        ) {

            runOnUiThread(
                    () -> {

                        if (
                                !processing ||
                                downloadStarted ||
                                url == null ||
                                url.length() < 8
                        ) {

                            return;
                        }

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

    private void scanResultPage() {

        if (
                !processing ||
                currentIndex < 0 ||
                downloadStarted
        ) {
            return;
        }

        if (
                resultChecks++ >=
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

        webStatus.setText(
                "WebView: mencari tombol MP4/HD... " +
                resultChecks +
                "/" +
                MAX_RESULT_CHECKS
        );

        String js =
                "(function(){"

                + "function txt(e){"

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

                + "function absolute(h){"

                + "try{"
                + "return new URL(h,"
                + "location.href).href;"
                + "}catch(e){"
                + "return '';"
                + "}"

                + "}"

                + "function bad(h){"

                + "return /facebook|instagram|"
                + "twitter|telegram|whatsapp|"
                + "pinterest/.test("
                + "h.toLowerCase()"
                + ");"

                + "}"

                + "function score(e){"

                + "var t=txt(e);"

                + "var p=e.parentElement"
                + "?txt(e.parentElement):'';"

                + "var g=t+' '+p;"

                + "var s=0;"

                + "if(/\\bmp4\\b/.test(g))s+=100;"

                + "if(/\\bhd\\b/.test(g))s+=80;"

                + "if(/\\b4k\\b/.test(g))s+=90;"

                + "if(/video/.test(g))s+=40;"

                + "if(/download|unduh/.test(g))s+=30;"

                + "if(/mp3|audio|music/.test(g))s-=100;"

                + "return s;"

                + "}"

                + "var candidates=[];"

                + "var as=[].slice.call("
                + "document.querySelectorAll("
                + "'a[href]'"
                + ")"
                + ");"

                + "as.forEach(function(e){"

                + "var raw=e.getAttribute('href')||'';"

                + "var h=absolute(raw);"

                + "var t=txt(e);"

                + "if(!h||bad(h))return;"

                + "if(/^https?:/i.test(h)){"

                + "var s=score(e);"

                + "if(e.hasAttribute('download'))"
                + "s+=150;"

                + "if(/\\.mp4(?:$|[?#])/i.test(h))"
                + "s+=180;"

                + "if(/blob:/i.test(h))"
                + "s-=1000;"

                + "if(s>0){"

                + "candidates.push({"
                + "e:e,"
                + "h:h,"
                + "s:s"
                + "});"

                + "}"

                + "}"

                + "});"

                + "candidates.sort(function(a,b){"
                + "return b.s-a.s;"
                + "});"

                + "if(candidates.length){"

                + "var best="
                + "candidates[0];"

                + "AndroidBridge.status("
                + "'Link video hasil ditemukan'"
                + ");"

                + "AndroidBridge.downloadLink("
                + "best.h"
                + ");"

                + "return 'LINK';"

                + "}"

                + "var buttons=[].slice.call("
                + "document.querySelectorAll("
                + "'button,input[type=button],"
                + "input[type=submit],"
                + "[role=button]'"
                + ")"
                + ");"

                + "var btns=[];"

                + "buttons.forEach(function(e){"

                + "var t=txt(e);"

                + "var p=e.parentElement"
                + "?txt(e.parentElement):'';"

                + "var g=t+' '+p;"

                + "if(/mp3|audio|music/.test(g))"
                + "return;"

                + "var s=score(e);"

                + "if(s>=70){"

                + "btns.push({e:e,s:s});"

                + "}"

                + "});"

                + "btns.sort(function(a,b){"
                + "return b.s-a.s;"
                + "});"

                + "if(btns.length){"

                + "btns[0].e.click();"

                + "AndroidBridge.status("
                + "'Tombol hasil MP4/HD ditekan'"
                + ");"

                + "return 'CLICK';"

                + "}"

                + "var body=txt(document.body);"

                + "if(/captcha|verify you are human|"
                + "verifikasi bahwa anda manusia/.test(body)){"

                + "AndroidBridge.status("
                + "'CAPTCHA/verifikasi perlu tindakan manual'"
                + ");"

                + "return 'CAPTCHA';"

                + "}"

                + "return 'WAIT';"

                + "})()";

        webView.evaluateJavascript(
                js,
                value -> {

                    if (
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

    private void enqueueDownload(
            String url,
            String userAgent,
            String mime,
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
                    !scheme.equalsIgnoreCase(
                            "http"
                    ) &&
                    !scheme.equalsIgnoreCase(
                            "https"
                    )
            ) {

                downloadStarted = false;

                webStatus.setText(
                        "WebView: format link download tidak didukung"
                );

                return;
            }

            DownloadManager dm =
                    (DownloadManager)
                            getSystemService(
                                    DOWNLOAD_SERVICE
                            );

            DownloadManager.Request r =
                    new DownloadManager.Request(
                            uri
                    );

            r.setTitle(
                    "TikTok " +
                    (currentIndex + 1)
            );

            r.setDescription(
                    "TikTok Download Manager"
            );

            r.setNotificationVisibility(
                    DownloadManager
                            .Request
                            .VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            );

            if (
                    mime != null &&
                    !mime.isEmpty()
            ) {

                r.setMimeType(mime);
            }

            if (
                    userAgent != null &&
                    !userAgent.isEmpty()
            ) {

                r.addRequestHeader(
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

                r.addRequestHeader(
                        "Cookie",
                        cookie
                );
            }

            r.addRequestHeader(
                    "Referer",
                    SAVE_URL
            );

            String filename =
                    URLUtil.guessFileName(
                            url,
                            contentDisposition,
                            mime
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
                    &&
                    (
                            mime == null ||
                            mime.toLowerCase(
                                    Locale.US
                            ).contains("video")
                    )
            ) {

                filename += ".mp4";
            }

            r.setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    filename
            );

            dm.enqueue(r);

            int done =
                    currentIndex;

            setStatus(
                    done,
                    "Download dimulai"
            );

            progress.setText(
                    "Progress: " +
                    (done + 1) +
                    " / " +
                    urls.size()
            );

            handler.postDelayed(
                    () -> {

                        processing = false;

                        submitClicked = false;

                        downloadStarted = false;

                        int next =
                                done + 1;

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

        queue.removeAllViews();

        progress.setText(
                "Progress: 0 / 0"
        );

        webStatus.setText(
                "WebView: siap"
        );
    }

    private void setStatus(
            int i,
            String s
    ) {

        if (
                i < 0 ||
                i >= queue.getChildCount()
        ) {
            return;
        }

        Object t =
                queue
                        .getChildAt(i)
                        .getTag();

        if (
                t instanceof TextView
        ) {

            ((TextView)t).setText(
                    "#" +
                    (i + 1) +
                    "  " +
                    s
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
            String s,
            float z
    ) {

        TextView v =
                new TextView(this);

        v.setText(s);

        v.setTextSize(z);

        v.setTextColor(
                Color.rgb(
                        17,
                        24,
                        39
                )
        );

        v.setPadding(
                0,
                9,
                0,
                9
        );

        return v;
    }

    private Button button(
            String s
    ) {

        Button b =
                new Button(this);

        b.setText(s);

        b.setAllCaps(false);

        return b;
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
