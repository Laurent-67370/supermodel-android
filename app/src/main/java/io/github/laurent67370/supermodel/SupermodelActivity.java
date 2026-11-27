package io.github.laurent67370.supermodel;

import android.app.AlertDialog;
import android.content.Intent;
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
    /** The touch controls hidden from the menu (MENU alone) */
    private boolean touchHidden;
    /** The touch controls' sizes and opacities, chosen from the menu */
    private static final float[] SIZES = { 0.8f, 1.0f, 1.25f };
    private static final String[] SIZE_NAMES = { "petite", "normale", "grande" };
    private static final float[] OPACITIES = { 0.55f, 1.0f, 1.6f };
    private static final String[] OPACITY_NAMES = { "discrètes", "normales", "très visibles" };

    /**
     * The game that starts, from Supermodel (SupermodelMobileGame of
     * android/android_main.cpp in supermodel-ios), on its own thread: the
     * touch controls laid out for its kind (fighting, driving, gun...)
     */
    public void setGameControls(String game, int inputs) {
        runOnUiThread(() -> {
            if (controls != null) {
                controls.setLayout(TouchControls.layoutFor(inputs));
            }
        });
    }

    /** Supermodel quit from the menu: the list of the games again */
    private boolean backToList;

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
        touchHidden = prefs.getBoolean("touch_hidden", false);
        controls = new TouchControls(this);
        controls.setListener(this::showMenu);
        controls.setLook(SIZES[prefs.getInt("touch_size", 1)], OPACITIES[prefs.getInt("touch_opacity", 1)]);
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

    /**
     * The touch controls in a game; MENU alone while a game controller is
     * played or when they were hidden from the menu
     */
    private void showControls() {
        if (controls == null) {
            return;
        }
        if (!inGame) {
            controls.releaseAll();
        }
        controls.setMinimal(padInUse || touchHidden);
        controls.setVisibility(inGame ? View.VISIBLE : View.GONE);
    }

    /** The menu of MENU (and of Android's back gesture): Supermodel's keys */
    private void showMenu() {
        final String[] items = {
            "Retour à la liste des jeux",
            "Sauvegarder l'état",
            "Charger l'état",
            "Changer d'emplacement de sauvegarde",
            "Pause / reprendre",
            "Redémarrer le jeu",
            "Bouton Test (menu de la borne)",
            "Bouton Service",
            touchHidden ? "Afficher les commandes tactiles" : "Masquer les commandes tactiles",
            "Taille des commandes : " + SIZE_NAMES[prefs().getInt("touch_size", 1)],
            "Visibilité des commandes : " + OPACITY_NAMES[prefs().getInt("touch_opacity", 1)],
        };
        new AlertDialog.Builder(this)
                .setTitle("Supermodel")
                .setItems(items, (dialog, which) -> {
                    switch (which) {
                    case 0:
                        // Supermodel quits (Escape, its NVRAM saved), then
                        // the app starts again on the list (onDestroy)
                        backToList = true;
                        controls.tap(KeyEvent.KEYCODE_ESCAPE);
                        break;
                    case 1: controls.tap(KeyEvent.KEYCODE_F5); break;
                    case 2: controls.tap(KeyEvent.KEYCODE_F7); break;
                    case 3: controls.tap(KeyEvent.KEYCODE_F6); break;
                    case 4: controls.chord(KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_P); break;
                    case 5: controls.chord(KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_R); break;
                    case 6: controls.tap(KeyEvent.KEYCODE_6); break;
                    case 7: controls.tap(KeyEvent.KEYCODE_5); break;
                    case 8:
                        touchHidden = !touchHidden;
                        getSharedPreferences(SetupActivity.PREFS, MODE_PRIVATE).edit()
                                .putBoolean("touch_hidden", touchHidden).apply();
                        showControls();
                        break;
                    case 9:
                    case 10: {
                        // The next size, or opacity, in turn
                        String key = which == 9 ? "touch_size" : "touch_opacity";
                        int next = (prefs().getInt(key, 1) + 1) % 3;
                        prefs().edit().putInt(key, next).apply();
                        controls.setLook(SIZES[prefs().getInt("touch_size", 1)],
                                OPACITIES[prefs().getInt("touch_opacity", 1)]);
                        break;
                    }
                    default:
                        break;
                    }
                })
                .setOnDismissListener(dialog -> hideBars())
                .show();
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(SetupActivity.PREFS, MODE_PRIVATE);
    }

    /** Android's back gesture: the menu in a game, as on a cabinet's panel */
    @Override
    public void onBackPressed() {
        if (inGame) {
            showMenu();
            return;
        }
        super.onBackPressed();
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
            if (backToList) {
                // A new start of the app, on the list of the games, in a
                // new process (the old one ends below)
                Intent intent = new Intent(this, SetupActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
            }
            System.exit(0);
        }
    }
}
