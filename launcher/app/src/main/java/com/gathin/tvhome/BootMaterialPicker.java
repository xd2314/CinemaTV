package com.gathin.tvhome;

import android.content.ActivityNotFoundException;
import android.content.Intent;

/** Import-only compatibility adapter. Never probes packages, requests Root, or changes assets. */
final class BootMaterialPicker {
    interface Starter { void start(Intent intent, int requestCode); }
    static final int DOCUMENT_REQUEST = 7101, CONTENT_REQUEST = 7103;
    enum Opened {
        DOCUMENT(DOCUMENT_REQUEST), CONTENT(CONTENT_REQUEST), NONE(-1);
        final int requestCode;
        Opened(int requestCode) { this.requestCode = requestCode; }
    }
    private final Starter starter;

    BootMaterialPicker(Starter starter) { this.starter = starter; }

    static Intent intent(BootAssetStore.Target target, String action) {
        boolean video = target == BootAssetStore.Target.VIDEO;
        Intent intent = new Intent(action).addCategory(Intent.CATEGORY_OPENABLE)
            .setType(video ? "video/mp4" : "image/*")
            .putExtra(Intent.EXTRA_LOCAL_ONLY, true)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (!video) intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/png", "image/jpeg"});
        return intent;
    }

    Opened open(BootAssetStore.Target target, boolean otherApp) {
        if (!otherApp && start(intent(target, Intent.ACTION_OPEN_DOCUMENT), DOCUMENT_REQUEST)) return Opened.DOCUMENT;
        if (start(Intent.createChooser(intent(target, Intent.ACTION_GET_CONTENT), "選擇檔案 App"), CONTENT_REQUEST)) return Opened.CONTENT;
        return Opened.NONE;
    }

    private boolean start(Intent intent, int requestCode) {
        try { starter.start(intent, requestCode); return true; }
        catch (ActivityNotFoundException | SecurityException unavailable) { return false; }
    }
}
