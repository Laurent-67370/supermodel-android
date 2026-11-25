package io.github.laurent67370.supermodel;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.SparseArray;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Touch controls over the game: a stick (the arrow keys) on the left, four
 * buttons on the right, Coin and Start at the top. They press the keys of
 * Supermodel's default keyboard bindings, as a keyboard would (SDL's own key
 * events), so they work in every game with the bindings it has:
 *
 *   stick   the arrows        (fighting: move; driving: steer, Up
 *                              accelerates, Down brakes)
 *   1 2 3 4 the keys A S D F  (Virtua Fighter 3: punch, kick, guard,
 *                              escape; driving: view change, gears...)
 *   COIN    3                 START 1
 *   MENU    the app's menu (SupermodelActivity): back to the list of the
 *           games, saved states, pause, test menu...
 *
 * A finger can slide from a button to another. Each press is held long
 * enough for Supermodel to see it (Keys). A touch that falls on none of
 * them goes on to the game (SDL). When the controls are hidden (a game
 * controller played, or hidden from the menu), MENU alone stays.
 */
public class TouchControls extends View {
    private static final int UP = KeyEvent.KEYCODE_DPAD_UP;
    private static final int DOWN = KeyEvent.KEYCODE_DPAD_DOWN;
    private static final int LEFT = KeyEvent.KEYCODE_DPAD_LEFT;
    private static final int RIGHT = KeyEvent.KEYCODE_DPAD_RIGHT;

    /** A round or rounded button pressing one key */
    private static final class Button {
        final String label;
        final int key;
        final boolean small;
        float x, y, radius;

        Button(String label, int key, boolean small) {
            this.label = label;
            this.key = key;
            this.small = small;
        }

        boolean contains(float px, float py) {
            float dx = px - x, dy = py - y;
            // A little bigger than drawn: easier to hit without looking
            float r = small ? radius * 1.3f : radius * 1.15f;
            return small ? Math.abs(dx) <= r * 1.8f && Math.abs(dy) <= r : dx * dx + dy * dy <= r * r;
        }
    }

    /** What MENU opens */
    interface Listener {
        void onMenu();
    }

    private final List<Button> buttons = new ArrayList<>();
    private final Button menu = new Button("MENU", 0, true);
    private final Keys keys = new Keys();
    private Listener listener;
    /** MENU alone */
    private boolean minimal;
    private float stickX, stickY, stickRadius;
    /** What each finger holds: a Button, or this (the stick) */
    private final SparseArray<Object> held = new SparseArray<>();
    /** Where the finger on the stick is, for the drawing */
    private float knobX, knobY;
    private boolean knobShown;
    private final Set<Integer> pressed = new HashSet<>();

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float dp;

    public TouchControls(Context context) {
        super(context);
        dp = context.getResources().getDisplayMetrics().density;
        buttons.add(new Button("1", KeyEvent.KEYCODE_A, false));
        buttons.add(new Button("2", KeyEvent.KEYCODE_S, false));
        buttons.add(new Button("3", KeyEvent.KEYCODE_D, false));
        buttons.add(new Button("4", KeyEvent.KEYCODE_F, false));
        buttons.add(new Button("COIN", KeyEvent.KEYCODE_3, true));
        buttons.add(new Button("START", KeyEvent.KEYCODE_1, true));
        buttons.add(menu);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(2 * dp);
        text.setTextAlign(Paint.Align.CENTER);
        text.setFakeBoldText(true);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        float side = Math.min(w, h);
        float margin = Math.max(24 * dp, side * 0.06f);

        // The stick, low on the left
        stickRadius = Math.min(side * 0.22f, 110 * dp);
        stickX = margin + stickRadius;
        stickY = h - margin - stickRadius;

        // 3 4 above 1 2, low on the right
        float r = Math.min(side * 0.085f, 42 * dp);
        float gap = r * 2.4f;
        float baseX = w - margin - r - gap;
        float baseY = h - margin - r;
        place(buttons.get(0), baseX, baseY, r);
        place(buttons.get(1), baseX + gap, baseY - gap * 0.35f, r);
        place(buttons.get(2), baseX - gap * 0.15f, baseY - gap, r);
        place(buttons.get(3), baseX + gap * 0.85f, baseY - gap * 1.35f, r);

        // COIN and START at the top, in the middle
        float s = Math.min(side * 0.04f, 20 * dp);
        place(buttons.get(4), w / 2f - s * 2.6f, margin * 0.5f + s, s);
        place(buttons.get(5), w / 2f + s * 2.6f, margin * 0.5f + s, s);
        // MENU in the top left corner, away from the stick
        place(menu, margin * 0.5f + s * 1.8f, margin * 0.5f + s, s);
        text.setTextSize(r * 0.9f);
    }

    private static void place(Button button, float x, float y, float radius) {
        button.x = x;
        button.y = y;
        button.radius = radius;
    }

    void setListener(Listener listener) {
        this.listener = listener;
    }

    /** MENU alone, or every control */
    void setMinimal(boolean minimal) {
        if (minimal != this.minimal) {
            this.minimal = minimal;
            releaseAll();
        }
    }

    /** The app's own keys (the menu's): a press and its release */
    void tap(int key) {
        keys.tap(key);
    }

    void chord(int modifier, int key) {
        keys.chord(modifier, key);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        int index = event.getActionIndex();
        int id = event.getPointerId(index);

        switch (action) {
        case MotionEvent.ACTION_DOWN:
        case MotionEvent.ACTION_POINTER_DOWN: {
            Object target = hit(event.getX(index), event.getY(index));
            if (target == null) {
                // Not on a control: the first finger goes on to the game
                if (action == MotionEvent.ACTION_DOWN) {
                    return false;
                }
                break;
            }
            if (action == MotionEvent.ACTION_DOWN && android.os.Build.VERSION.SDK_INT >= 30) {
                // The touches as they come, not gathered until the next
                // frame of the screen (up to 16 ms sooner)
                requestUnbufferedDispatch(event);
            }
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            if (target == menu) {
                if (listener != null) {
                    listener.onMenu();
                }
                break;
            }
            held.put(id, target);
            break;
        }
        case MotionEvent.ACTION_MOVE:
            // A finger on a button can slide onto another
            for (int i = 0; i < event.getPointerCount(); i++) {
                Object target = held.get(event.getPointerId(i));
                if (target instanceof Button) {
                    Button now = button(event.getX(i), event.getY(i));
                    if (now != null && now != target && now != menu) {
                        held.put(event.getPointerId(i), now);
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    }
                }
            }
            break;
        case MotionEvent.ACTION_UP:
        case MotionEvent.ACTION_POINTER_UP:
            held.remove(id);
            break;
        case MotionEvent.ACTION_CANCEL:
            held.clear();
            break;
        default:
            break;
        }
        update(event, action == MotionEvent.ACTION_CANCEL ? -1
                : action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP ? id : Integer.MIN_VALUE);
        return true;
    }

    private Object hit(float x, float y) {
        if (minimal) {
            return menu.contains(x, y) ? menu : null;
        }
        float dx = x - stickX, dy = y - stickY;
        if (dx * dx + dy * dy <= stickRadius * stickRadius * 1.5f) {
            return this;
        }
        return button(x, y);
    }

    private Button button(float x, float y) {
        for (Button button : buttons) {
            if (button.contains(x, y)) {
                return button;
            }
        }
        return null;
    }

    /** The keys the fingers hold now, pressed or released as they change */
    private void update(MotionEvent event, int gone) {
        Set<Integer> now = new HashSet<>();
        knobShown = false;
        if (gone != -1) {
            for (int i = 0; i < event.getPointerCount(); i++) {
                int id = event.getPointerId(i);
                Object target = held.get(id);
                if (target == null || id == gone) {
                    continue;
                }
                if (target == this) {
                    stick(event.getX(i), event.getY(i), now);
                } else {
                    now.add(((Button) target).key);
                }
            }
        }
        if (now.equals(pressed)) {
            if (knobShown) {
                invalidate();
            }
            return;
        }
        for (int key : pressed) {
            if (!now.contains(key)) {
                keys.release(key);
            }
        }
        for (int key : now) {
            if (!pressed.contains(key)) {
                keys.press(key);
            }
        }
        pressed.clear();
        pressed.addAll(now);
        invalidate();
    }

    /** Eight directions, past a small dead zone */
    private void stick(float x, float y, Set<Integer> keys) {
        float dx = x - stickX, dy = y - stickY;
        float length = (float) Math.hypot(dx, dy);
        float max = stickRadius * 0.6f;
        knobShown = true;
        knobX = stickX + (length > max ? dx / length * max : dx);
        knobY = stickY + (length > max ? dy / length * max : dy);
        if (length < stickRadius * 0.25f) {
            return;
        }
        double angle = Math.toDegrees(Math.atan2(-dy, dx));
        if (angle < 0) {
            angle += 360;
        }
        // Each direction over 135 degrees: the diagonals press two keys
        if (angle < 67.5 || angle > 292.5) keys.add(RIGHT);
        if (angle > 22.5 && angle < 157.5) keys.add(UP);
        if (angle > 112.5 && angle < 247.5) keys.add(LEFT);
        if (angle > 202.5 && angle < 337.5) keys.add(DOWN);
    }

    /** Everything let go (the controls hidden, the app in the background) */
    public void releaseAll() {
        held.clear();
        keys.releaseAll();
        pressed.clear();
        knobShown = false;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (minimal) {
            drawButton(canvas, menu);
            return;
        }
        // The stick: its ring, its arrows, the knob under the finger
        fill.setColor(Color.argb(40, 255, 255, 255));
        canvas.drawCircle(stickX, stickY, stickRadius, fill);
        stroke.setColor(Color.argb(110, 255, 255, 255));
        canvas.drawCircle(stickX, stickY, stickRadius, stroke);
        drawArrow(canvas, 0, -1, pressed.contains(UP));
        drawArrow(canvas, 0, 1, pressed.contains(DOWN));
        drawArrow(canvas, -1, 0, pressed.contains(LEFT));
        drawArrow(canvas, 1, 0, pressed.contains(RIGHT));
        fill.setColor(Color.argb(knobShown ? 140 : 70, 255, 255, 255));
        canvas.drawCircle(knobShown ? knobX : stickX, knobShown ? knobY : stickY, stickRadius * 0.38f, fill);

        for (Button button : buttons) {
            drawButton(canvas, button);
        }
    }

    private void drawButton(Canvas canvas, Button button) {
        {
            boolean down = button != menu && pressed.contains(button.key);
            fill.setColor(down ? Color.argb(150, 61, 139, 255) : Color.argb(60, 255, 255, 255));
            stroke.setColor(Color.argb(down ? 220 : 120, 255, 255, 255));
            if (button.small) {
                RectF box = new RectF(button.x - button.radius * 1.8f, button.y - button.radius,
                        button.x + button.radius * 1.8f, button.y + button.radius);
                canvas.drawRoundRect(box, button.radius, button.radius, fill);
                canvas.drawRoundRect(box, button.radius, button.radius, stroke);
                text.setTextSize(button.radius * 0.75f);
            } else {
                canvas.drawCircle(button.x, button.y, button.radius, fill);
                canvas.drawCircle(button.x, button.y, button.radius, stroke);
                text.setTextSize(button.radius * 0.9f);
            }
            text.setColor(Color.argb(down ? 255 : 190, 255, 255, 255));
            canvas.drawText(button.label, button.x, button.y - (text.descent() + text.ascent()) / 2, text);
        }
    }

    private void drawArrow(Canvas canvas, int dx, int dy, boolean down) {
        float d = stickRadius * 0.78f, s = stickRadius * 0.12f;
        float x = stickX + dx * d, y = stickY + dy * d;
        fill.setColor(Color.argb(down ? 230 : 120, 255, 255, 255));
        android.graphics.Path path = new android.graphics.Path();
        // A triangle pointing outwards
        path.moveTo(x + dx * s, y + dy * s);
        path.lineTo(x - dx * s + dy * s, y - dy * s + dx * s);
        path.lineTo(x - dx * s - dy * s, y - dy * s - dx * s);
        path.close();
        canvas.drawPath(path, fill);
    }
}
