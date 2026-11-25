package io.github.laurent67370.supermodel;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;

import org.libsdl.app.SDLActivity;

/**
 * Supermodel itself: SDL's activity running SDL_main of libmain.so (built
 * from supermodel-ios), told its folder through the environment
 * (android/android_main.cpp of supermodel-ios).
 */
public class SupermodelActivity extends SDLActivity {
    @Override
    protected void onCreate(Bundle saved) {
        SharedPreferences prefs = getSharedPreferences(SetupActivity.PREFS, MODE_PRIVATE);
        String folder = getIntent().getStringExtra(SetupActivity.KEY_FOLDER);
        if (folder == null) {
            folder = prefs.getString(SetupActivity.KEY_FOLDER, getFilesDir().getPath());
        }
        try {
            Os.setenv("SUPERMODEL_HOME", folder, true);
            Os.setenv("HOME", folder, true);
        } catch (ErrnoException e) {
            Log.e("Supermodel", "setenv", e);
        }
        super.onCreate(saved);
        hideBars();
    }

    @Override
    protected String[] getLibraries() {
        return new String[] { "c++_shared", "SDL2", "SDL2_net", "main" };
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideBars();
    }

    @Override
    public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if (focus) {
            hideBars();
        }
    }

    /**
     * The whole screen, the bars hidden (they come back with a swipe), from
     * the menu on: SDL's own full screen, which SDL keeps (its onCreate shows
     * the bars; asked after it, this comes last)
     */
    private void hideBars() {
        setWindowStyle(true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Supermodel's native state is made for one run: the next start is a
        // new process
        if (isFinishing()) {
            System.exit(0);
        }
    }
}
