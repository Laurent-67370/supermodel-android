package io.github.laurent67370.supermodel;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import org.libsdl.app.SDLActivity;

/**
 * Supermodel itself: SDL's activity running SDL_main of libmain.so (built
 * from supermodel-ios), told its folder through the environment
 * (android/android_main.cpp of supermodel-ios).
 */
public class SupermodelActivity extends SDLActivity {
    /** The touch controls, over the game */
    private TouchControls controls;
    /** A game is running (not the list of the games) */
    private boolean inGame;
    /** A game controller was used since the screen was last touched */
    private boolean padInUse;

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
        controls = new TouchControls(this);
        controls.setVisibility(View.GONE);
        addContentView(controls, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    /**
     * The window's title, which SDL gives the activity: "SuperSetup" for the
     * list of the games, "Supermodel - <game>" for a game (Main.cpp)
     */
    @Override
    protected void onTitleChanged(CharSequence title, int color) {
        super.onTitleChanged(title, color);
        boolean game = title != null && title.toString().startsWith("Supermodel - ");
        if (game != inGame) {
            inGame = game;
            showControls();
        }
    }

    /** The touch controls in a game, unless a game controller is played */
    private void showControls() {
        if (controls == null) {
            return;
        }
        boolean shown = inGame && !padInUse;
        if (!shown) {
            controls.releaseAll();
        }
        controls.setVisibility(shown ? View.VISIBLE : View.GONE);
    }

    private static boolean fromPad(int source) {
        return (source & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                || (source & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
    }

    // A game controller hides the touch controls; touching the screen brings
    // them back
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (!padInUse && fromPad(event.getSource()) && event.getKeyCode() != KeyEvent.KEYCODE_BACK) {
            padInUse = true;
            showControls();
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (!padInUse && fromPad(event.getSource())) {
            padInUse = true;
            showControls();
        }
        return super.dispatchGenericMotionEvent(event);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (padInUse && event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            padInUse = false;
            showControls();
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    protected void onPause() {
        if (controls != null) {
            controls.releaseAll();
        }
        super.onPause();
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
