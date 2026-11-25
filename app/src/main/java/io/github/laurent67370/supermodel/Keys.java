package io.github.laurent67370.supermodel;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.SparseArray;

import org.libsdl.app.SDLActivity;

/**
 * The keys pressed for Supermodel, through SDL's key events, each press held
 * long enough to be seen.
 *
 * Supermodel reads the keyboard once a frame (every 16.7 ms): a quick tap,
 * pressed and released between two frames, was lost. Here each press stays
 * down, and each release stays up, at least HOLD_MS; a press made during a
 * release (or a release during a press) waits its turn rather than being
 * merged, so that fast tapping (punch, punch, punch) gives as many presses.
 */
final class Keys {
    /** Over two frames at 60 Hz */
    static final long HOLD_MS = 40;

    private static final class State {
        int held;       // fingers (or buttons) holding the key
        int presses;    // presses not yet sent
        boolean down;   // as SDL has it
        long changed;   // when SDL's state last changed
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SparseArray<State> states = new SparseArray<>();

    private State state(int key) {
        State s = states.get(key);
        if (s == null) {
            s = new State();
            states.put(key, s);
        }
        return s;
    }

    /** A finger on the key */
    void press(int key) {
        State s = state(key);
        s.held++;
        s.presses++;
        pump(key);
    }

    /** That finger off */
    void release(int key) {
        State s = state(key);
        if (s.held > 0) {
            s.held--;
        }
        pump(key);
    }

    /** A press and its release */
    void tap(int key) {
        state(key).presses++;
        pump(key);
    }

    /** Two keys together, as Alt+P (Supermodel's own keys), held a moment */
    void chord(int modifier, int key) {
        press(modifier);
        press(key);
        handler.postDelayed(() -> {
            release(key);
            release(modifier);
        }, HOLD_MS * 2);
    }

    private void pump(int key) {
        State s = state(key);
        long now = SystemClock.uptimeMillis();
        long wait = s.changed + HOLD_MS - now;
        boolean toDown = !s.down && s.presses > 0;
        boolean toUp = s.down && (s.held == 0 || s.presses > 0);

        if (!toDown && !toUp) {
            return;
        }
        if (wait > 0) {
            handler.postDelayed(() -> pump(key), wait);
            return;
        }
        if (toDown) {
            SDLActivity.onNativeKeyDown(key);
            s.down = true;
            s.presses--;
        } else {
            SDLActivity.onNativeKeyUp(key);
            s.down = false;
        }
        s.changed = now;
        // The next change, if one waits (a release, or another press)
        handler.postDelayed(() -> pump(key), HOLD_MS);
    }

    /** Everything let go at once (the app in the background) */
    void releaseAll() {
        handler.removeCallbacksAndMessages(null);
        for (int i = 0; i < states.size(); i++) {
            State s = states.valueAt(i);
            s.held = 0;
            s.presses = 0;
            if (s.down) {
                SDLActivity.onNativeKeyUp(states.keyAt(i));
                s.down = false;
                s.changed = SystemClock.uptimeMillis();
            }
        }
    }
}
