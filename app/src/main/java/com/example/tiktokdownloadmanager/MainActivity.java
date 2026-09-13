package com.example.tiktokdownloadmanager;

import android.app.*;
import android.os.*;
import android.graphics.Color;
import android.content.*;
import android.net.Uri;
import android.webkit.*;
import android.view.*;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {

    LinearLayout root, queue;
    EditText input;
    TextView progress, webStatus;
    WebView web;

    ArrayList<String> urls = new ArrayList<>();
    int current = -1;
    boolean running = false;

    static final String SAVE = "https://savetiktok.to/id";
    static final int WAIT_PAGE_MS = 2500;
    static final int WAIT_RESULT_MS = 4500;

    TextView tv(String s, float z) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(z);
        v.setTextColor(Color.rgb(17,24,39));
        v.setPadding(0,8,0,8);
        return v;
    }

    Button btn(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextSize(14);
        b.setAllCaps(false);
        return b;
    }

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
        buildUi();
        setupWebView();
    }

    void buildUi() {
        ScrollView sc = new ScrollView(this);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(22,22,22,22);
        root.setBackgroundColor(Color.rgb(244,245,247));

        root.addView(tv("TikTok Download Manager",27));
        root.addView(tv("Otomatis • WebView + antrean",15));

        input = new EditText(this);
        input.setHint("Tempel link TikTok, satu per baris");
        input.setGravity(Gravity.TOP);
        input.setMinLines(5);

        root.addView(
            input,
            new LinearLayout.LayoutParams(-1,220)
        );

        LinearLayout r1 = new LinearLayout(this);

        Button load = btn("Muat Antrean");
        Button clear = btn("Bersihkan");

        r1.addView(
            load,
            new LinearLayout.LayoutParams(0,60,1)
        );

        LinearLayout.LayoutParams cp =
            new LinearLayout.LayoutParams(0,60,1);

        cp.setMargins(8,0,0,0);
        r1.addView(clear,cp);

        root.addView(r1);

        LinearLayout r2 = new LinearLayout(this);

        Button start = btn("▶ Mulai Semua");
        Button stop = btn("■ Berhenti");

        r2.addView(
            start,
            new LinearLayout.LayoutParams(0,60,1)
        );

        LinearLayout.LayoutParams sp =
            new LinearLayout.LayoutParams(0,60,1);

        sp.setMargins(8,0,0,0);
        r2.addView(stop,sp);

        root.addView(r2);

        progress = tv("Progress: 0 / 0",17);
        root.addView(progress);

        webStatus = tv("WebView: siap",13);
        root.addView(webStatus);

        queue = new LinearLayout(this);
        queue.setOrientation(LinearLayout.VERTICAL);

        root.addView(queue);

        root.addView(
            tv(
                "Catatan: aplikasi mengotomatisasi interaksi normal " +
                "pada halaman SaveTikTok. CAPTCHA, login, rate limit, " +
                "dan mekanisme keamanan tidak dilewati dan tetap " +
                "memerlukan tindakan manual.",
                13
            )
        );

        load.setOnClickListener(v -> loadQueue());

        clear.setOnClickListener(v -> {
            stopRun();
            input.setText("");
            urls.clear();
            queue.removeAllViews();
            updateProgress();
        });

        start.setOnClickListener(v -> startRun());

        stop.setOnClickListener(v -> stopRun());

        sc.addView(root);
        setContentView(sc);
    }

    void setupWebView() {

        web = new WebView(this);
        web.setVisibility(View.GONE);

        root.addView(
            web,
            new LinearLayout.LayoutParams(1,1)
        );

        WebSettings s = web.getSettings();

        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);

        s.setSupportMultipleWindows(false);

        s.setUserAgentString(
            s.getUserAgentString() +
            " TikTokDownloadManager/2.0"
        );

        web.addJavascriptInterface(
            new JsBridge(),
            "AndroidDM"
        );

        web.setWebViewClient(new WebViewClient() {

            @Override
            public void onPageFinished(
                WebView view,
                String url
            ) {

                webStatus.setText(
                    "WebView: halaman selesai dimuat"
                );

                if (running && current >= 0) {

                    new Handler().postDelayed(
                        () -> injectCurrentUrl(),
                        WAIT_PAGE_MS
                    );
                }
            }
        });

        web.setDownloadListener(
            (url,userAgent,contentDisposition,
             mimeType,contentLength) -> {

                DownloadManager.Request req =
                    new DownloadManager.Request(
                        Uri.parse(url)
                    );

                req.setMimeType(mimeType);

                req.addRequestHeader(
                    "User-Agent",
                    userAgent
                );

                req.setTitle(
                    "TikTok Download Manager"
                );

                req.setDescription(
                    "Mengunduh hasil SaveTikTok"
                );

                req.setNotificationVisibility(
                    DownloadManager.Request
                        .VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                );

                req.setDestinationInExternalPublicDir(
                    android.os.Environment
                        .DIRECTORY_DOWNLOADS,
                    "TikTok_" +
                    System.currentTimeMillis() +
                    guessExt(mimeType)
                );

                ((DownloadManager)
                    getSystemService(DOWNLOAD_SERVICE))
                    .enqueue(req);

                markCurrent("Selesai");
                advanceSoon();
            }
        );
    }

    String guessExt(String mime) {

        if (mime == null)
            return ".mp4";

        if (mime.contains("audio"))
            return ".mp3";

        if (mime.contains("zip"))
            return ".zip";

        return ".mp4";
    }

    void loadQueue() {

        LinkedHashSet<String> set =
            new LinkedHashSet<>();

        String raw =
            input.getText().toString();

        for (String x :
             raw.split("\\r?\\n")) {

            x = x.trim();

            if (x.startsWith("http://") ||
                x.startsWith("https://")) {

                set.add(x);
            }
        }

        stopRun();

        urls.clear();
        urls.addAll(set);

        renderQueue();
        updateProgress();
    }

    void renderQueue() {

        queue.removeAllViews();

        for (int i=0; i<urls.size(); i++) {

            LinearLayout card =
                new LinearLayout(this);

            card.setOrientation(
                LinearLayout.VERTICAL
            );

            card.setPadding(
                14,12,14,12
            );

            TextView st =
                tv("#"+(i+1)+"  Menunggu",16);

            TextView link =
                tv(urls.get(i),12);

            link.setMaxLines(3);

            Button one =
                btn("Proses Link Ini");

            final int idx = i;

            one.setOnClickListener(v -> {

                if (!urls.isEmpty()) {

                    current = idx;
                    running = true;

                    web.setVisibility(
                        View.GONE
                    );

                    web.loadUrl(SAVE);
                }
            });

            card.addView(st);
            card.addView(link);
            card.addView(one);

            queue.addView(card);
        }
    }

    void startRun() {

        if (urls.isEmpty())
            loadQueue();

        if (urls.isEmpty()) {

            Toast.makeText(
                this,
                "Masukkan minimal 1 link TikTok",
                Toast.LENGTH_SHORT
            ).show();

            return;
        }

        running = true;

        current = firstPending();

        if (current < 0) {

            Toast.makeText(
                this,
                "Semua link sudah diproses",
                Toast.LENGTH_SHORT
            ).show();

            running = false;
            return;
        }

        webStatus.setText(
            "WebView: membuka SaveTikTok..."
        );

        web.loadUrl(SAVE);
    }

    int firstPending() {

        if (current >= 0 &&
            current < urls.size()) {

            return current;
        }

        return 0;
    }

    void stopRun() {

        running = false;

        if (web != null)
            web.stopLoading();

        if (webStatus != null)
            webStatus.setText(
                "WebView: dihentikan"
            );
    }

    void injectCurrentUrl() {

        if (!running ||
            current < 0 ||
            current >= urls.size())
            return;

        String escaped =
            TextUtilsCompat.jsQuote(
                urls.get(current)
            );

        String js =
            "(function(){" +

            "var u="+escaped+";" +

            "var inputs=[...document.querySelectorAll('input,textarea')];" +

            "var el=inputs.find(x=>" +
            "/url|link|tiktok/i.test(" +
            "(x.placeholder||'')+' '+" +
            "(x.name||'')+' '+" +
            "(x.ariaLabel||'')))||inputs[0];" +

            "if(!el){" +
            "AndroidDM.status(" +
            "'Kolom URL tidak ditemukan');" +
            "return;" +
            "}" +

            "el.focus();" +

            "el.value=u;" +

            "el.dispatchEvent(" +
            "new Event('input',{bubbles:true}));" +

            "el.dispatchEvent(" +
            "new Event('change',{bubbles:true}));" +

            "AndroidDM.status(" +
            "'URL dimasukkan');" +

            "setTimeout(function(){" +

            "var bs=[...document.querySelectorAll(" +
            "'button,input[type=submit],a')];" +

            "var b=bs.find(x=>" +
            "/download|unduh|submit|search|proses|generate|convert/i.test(" +
            "(x.innerText||x.value||" +
            "x.getAttribute('aria-label')||'')));" +

            "if(b){" +
            "b.click();" +
            "AndroidDM.status(" +
            "'Tombol proses diklik');" +
            "}else{" +
            "AndroidDM.status(" +
            "'Tombol proses tidak ditemukan');" +
            "}" +

            "},900);" +

            "setTimeout(function(){" +
            "AndroidDM.checkResult();" +
            "},"+WAIT_RESULT_MS+");" +

            "})()";

        web.evaluateJavascript(
            js,
            null
        );

        markCurrent("Memproses");
        updateProgress();
    }

    void checkResult() {

        if (!running)
            return;

        String js =
            "(function(){" +

            "var text=" +
            "(document.body.innerText||'')" +
            ".toLowerCase();" +

            "var links=[...document.querySelectorAll('a')]" +
            ".map(x=>x.href||'');" +

            "var dl=links.some(x=>" +
            "/download|\\.mp4|\\.mp3|\\.zip/i.test(x));" +

            "var buttons=[...document.querySelectorAll('button,a')]" +
            ".some(x=>" +
            "/download|unduh/i.test(x.innerText||''));" +

            "AndroidDM.result(" +
            "dl||buttons||" +
            "/ready|success|berhasil|siap diunduh/.test(text)" +
            ");" +

            "})()";

        web.evaluateJavascript(
            js,
            null
        );
    }

    void markCurrent(String status) {

        if (current < 0 ||
            current >= queue.getChildCount())
            return;

        View card =
            queue.getChildAt(current);

        if (card instanceof LinearLayout &&
            ((LinearLayout)card).getChildCount() > 0) {

            View v =
                ((LinearLayout)card).getChildAt(0);

            if (v instanceof TextView) {

                ((TextView)v).setText(
                    "#"+(current+1)+
                    "  "+status
                );
            }
        }

        updateProgress();
    }

    void advanceSoon() {

        new Handler().postDelayed(
            () -> {

                if (!running)
                    return;

                current++;

                if (current >= urls.size()) {

                    running = false;

                    webStatus.setText(
                        "WebView: semua antrean selesai"
                    );

                    updateProgress();

                    Toast.makeText(
                        this,
                        "Antrean selesai",
                        Toast.LENGTH_SHORT
                    ).show();

                    return;
                }

                web.loadUrl(SAVE);

            },
            1500
        );
    }

    void updateProgress() {

        int done = 0;

        for (int i=0;
             i<queue.getChildCount();
             i++) {

            View c =
                queue.getChildAt(i);

            if (c instanceof LinearLayout &&
                ((LinearLayout)c).getChildCount()>0) {

                View v =
                    ((LinearLayout)c).getChildAt(0);

                if (v instanceof TextView &&
                    ((TextView)v).getText()
                    .toString()
                    .contains("Selesai")) {

                    done++;
                }
            }
        }

        progress.setText(
            "Progress: "+
            done+
            " / "+
            urls.size()
        );
    }

    class JsBridge {

        @JavascriptInterface
        public void status(String s) {

            runOnUiThread(() ->
                webStatus.setText(
                    "WebView: "+s
                )
            );
        }

        @JavascriptInterface
        public void result(boolean ok) {

            runOnUiThread(() -> {

                if (!running)
                    return;

                if (ok) {

                    webStatus.setText(
                        "WebView: hasil terdeteksi"
                    );

                    markCurrent(
                        "Menunggu download"
                    );

                } else {

                    webStatus.setText(
                        "WebView: hasil belum terdeteksi"
                    );

                    markCurrent(
                        "Perlu pemeriksaan"
                    );

                    running = false;
                }
            });
        }
    }

    static class TextUtilsCompat {

        static String jsQuote(String s) {

            return "'" +
                s.replace("\\","\\\\")
                 .replace("'","\\'")
                 .replace("\n","\\n")
                 .replace("\r","\\r") +
                "'";
        }
    }
}
