package com.fanfold.app;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** One background thread shared by the app and the print service, so printer access is serialized. */
final class Jobs {
    private Jobs() {}

    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor();
    /** Rendering previews runs separately so a slow preview never blocks a print. */
    private static final ExecutorService RENDER = Executors.newSingleThreadExecutor();
    static final Handler MAIN = new Handler(Looper.getMainLooper());

    static void printer(Runnable r) { EXEC.execute(r); }

    static void render(Runnable r) { RENDER.execute(r); }

    static void main(Runnable r) { MAIN.post(r); }
}
