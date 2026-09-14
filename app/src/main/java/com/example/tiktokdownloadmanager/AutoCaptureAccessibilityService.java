package com.example.tiktokdownloadmanager;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

public class AutoCaptureAccessibilityService extends AccessibilityService {

    private long lastEventTime = 0;
    private String lastText = "";

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {

        if (event == null) return;

        String packageName = String.valueOf(event.getPackageName());

        // Batasi percobaan hanya ketika TikTok sedang aktif
        if (!packageName.contains("tiktok")) {
            return;
        }

        // Hindari event yang terlalu berulang
        long now = System.currentTimeMillis();

        if (now - lastEventTime < 500) {
            return;
        }

        lastEventTime = now;

        AccessibilityNodeInfo root = getRootInActiveWindow();

        if (root == null) return;

        String text = findCopyText(root);

        if (text == null || text.isEmpty()) {
            return;
        }

        if (text.equals(lastText)) {
            return;
        }

        lastText = text;

        /*
         * Untuk tahap pertama kita hanya mendeteksi apakah
         * Accessibility bisa melihat teks "Tautan disalin".
         *
         * Belum mengambil clipboard.
         */
        Toast.makeText(
                this,
                "TikTok terdeteksi: " + text,
                Toast.LENGTH_SHORT
        ).show();
    }

    private String findCopyText(AccessibilityNodeInfo node) {

        if (node == null) return null;

        CharSequence text = node.getText();

        if (text != null) {

            String value = text.toString().trim();

            if (value.contains("Tautan disalin")
                    || value.contains("Link copied")
                    || value.contains("tautan disalin")
                    || value.contains("link copied")) {

                return value;
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {

            AccessibilityNodeInfo child = node.getChild(i);

            String result = findCopyText(child);

            if (result != null) {
                return result;
            }
        }

        return null;
    }

    @Override
    public void onInterrupt() {
        // Tidak ada tindakan khusus
    }
}
