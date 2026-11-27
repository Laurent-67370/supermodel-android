package io.github.laurent67370.supermodel;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Path;
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
 * Touch controls over the game, laid out for the kind of game it is
 * (setLayout, from the game's controls in Supermodel's Games.xml). They press
 * the keys of Supermodel's default keyboard bindings, as a keyboard would
 * (SDL's own key events, through Keys), so they work with the bindings each
 * game has, and can be changed in the menu's Touches tab:
 *
 *   FIGHT   a stick (the arrows), P K G E (A S D F): punch, kick, guard,
 *           escape (Virtua Fighter 3, Fighting Vipers...)
 *   DRIVE   a wheel (left, right), GAZ (Up), FREIN (Down), VUE (A), and
 *           the gears up and down (Y, H) (Daytona USA 2, Scud Race...)
 *   GUN     no stick: a finger on the screen aims and fires (SDL's mouse,
 *           which the guns follow), RECHARGER (S, off screen)
 *   OTHER   a stick and 1 2 3 4 (A S D F)
 *
 * and always COIN (3), START (1) and MENU (the app's menu). A finger can
 * slide from a button to another; a touch on none of them goes on to the
 * game. When the controls are hidden (a game controller played, or hidden
 * from the menu), MENU alone stays. Their size and how much they show are
 * set from the menu (setLook).
 */
public class TouchControls extends View {
    static final int OTHER = 0, FIGHT = 1, DRIVE = 2, GUN = 3;

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
    private final Button coin = new Button("COIN", KeyEvent.KEYCODE_3, true);
    private final Button start = new Button("START", KeyEvent.KEYCODE_1, true);
    private final Button menu = new Button("MENU", 0, true);
    private final Keys keys = new Keys();
    private Listener listener;
    private int layout = OTHER;
    /** MENU alone */
    private boolean minimal;
    /** The size of the controls (1 = normal) and how much they show */
    private float size = 1.0f, opacity = 1.0f;

    private boolean hasStick;
    /** The wheel of DRIVE: left and right only */
    private boolean horizontalStick;
    private float stickX, stickY, stickRadius;
    /** What each finger holds: a Button, or this (the stick) */
    private final SparseArray<Object> held = new SparseArray<>();
    /** Where the finger on the stick is, for the drawing */
    private float knobX, knobY;
    private boolean knobShown;
    private final Set<Integer> pressed = new HashSet<>();

    private final android.graphics.Paint fill = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
    private final android.graphics.Paint stroke = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
    private final android.graphics.Paint text = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
    private final float dp;

    public TouchControls(Context context) {
        super(context);
        dp = context.getResources().getDisplayMetrics().density;
        stroke.setStyle(android.graphics.Paint.Style.STROKE);
        stroke.setStrokeWidth(2 * dp);
        text.setTextAlign(android.graphics.Paint.Align.CENTER);
        text.setFakeBoldText(true);
        setLayout(OTHER);
    }

    /** The kind of game: Supermodel's Game::INPUT_* flags (Game.h) */
    static int layoutFor(int inputs) {
        final int vehicle = 0x00000002, fighting = 0x00000010, harley = 0x00000400;
        final int gun1 = 0x00000800, analogGun1 = 0x00020000;
        if ((inputs & (gun1 | analogGun1)) != 0) {
            return GUN;
        }
        if ((inputs & (vehicle | harley)) != 0) {
            return DRIVE;
        }
        if ((inputs & fighting) != 0) {
            return FIGHT;
        }
        return OTHER;
    }

    void setLayout(int layout) {
        releaseAll();
        this.layout = layout;
        buttons.clear();
        switch (layout) {
        case FIGHT:
            buttons.add(new Button("P", KeyEvent.KEYCODE_A, false));
            buttons.add(new Button("K", KeyEvent.KEYCODE_S, false));
            buttons.add(new Button("G", KeyEvent.KEYCODE_D, false));
            buttons.add(new Button("E", KeyEvent.KEYCODE_F, false));
            break;
        case DRIVE:
            buttons.add(new Button("GAZ", UP, false));
            buttons.add(new Button("FREIN", DOWN, false));
            buttons.add(new Button("VUE", KeyEvent.KEYCODE_A, false));
            buttons.add(new Button("V+", KeyEvent.KEYCODE_Y, false));
            buttons.add(new Button("V-", KeyEvent.KEYCODE_H, false));
            break;
        case GUN:
            buttons.add(new Button("RECHARGER", KeyEvent.KEYCODE_S, false));
            break;
        default:
            buttons.add(new Button("1", KeyEvent.KEYCODE_A, false));
            buttons.add(new Button("2", KeyEvent.KEYCODE_S, false));
            buttons.add(new Button("3", KeyEvent.KEYCODE_D, false));
            buttons.add(new Button("4", KeyEvent.KEYCODE_F, false));
            break;
        }
        buttons.add(coin);
        buttons.add(start);
        buttons.add(menu);
        hasStick = layout != GUN;
        horizontalStick = layout == DRIVE;
        place(getWidth(), getHeight());
        invalidate();
    }

    /** Size (1 = normal) and opacity (1 = normal) of the controls */
    void setLook(float size, float opacity) {
        this.size = size;
        this.opacity = opacity;
        place(getWidth(), getHeight());
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        place(w, h);
    }

    private void place(int w, int h) {
        if (w == 0 || h == 0) {
            return;
        }
        float side = Math.min(w, h);
        float margin = Math.max(24 * dp, side * 0.06f);

        // The stick (or the wheel), low on the left
        stickRadius = Math.min(side * 0.22f, 110 * dp) * size;
        stickX = margin + stickRadius;
        stickY = h - margin - stickRadius;

        float r = Math.min(side * 0.085f, 42 * dp) * size;
        float gap = r * 2.4f;
        float baseX = w - margin - r - gap;
        float baseY = h - margin - r;
        switch (layout) {
        case DRIVE:
            // GAZ big in the corner, FREIN beside it with VUE above it, the
            // gears (V- then V+) above GAZ
            {
                float big = r * 1.3f, gear = r * 0.8f;
                Button gas = buttons.get(0), brake = buttons.get(1);
                place(gas, w - margin - big, h - margin - big, big);
                place(brake, gas.x - big - r - gap * 0.4f, h - margin - r, r);
                place(buttons.get(2), brake.x, brake.y - r - gap * 0.3f - r * 0.85f, r * 0.85f);
                place(buttons.get(4), gas.x, gas.y - big - gap * 0.25f - gear, gear);
                place(buttons.get(3), gas.x, buttons.get(4).y - gear * 2.4f, gear);
            }
            break;
        case GUN:
            buttons.add(new Button("RECHARGER", KeyEvent.KEYCODE_S, false));
            break;
        default:
            buttons.add(new Button("1", KeyEvent.KEYCODE_A, false));
            buttons.add(new Button("2", KeyEvent.KEYCODE_S, false));
            buttons.add(new Button("3", KeyEvent.KEYCODE_D, false));
            buttons.add(new Button("4", KeyEvent.KEYCODE_F, false));
            break;
        }
        buttons.add(coin);
        buttons.add(start);
        buttons.add(menu);
        hasStick = layout != GUN;
        horizontalStick = layout == DRIVE;
        place(getWidth(), getHeight());
        invalidate();
    }

    /** Size (1 = normal) and opacity (1 = normal) of the controls */
    void setLook(float size, float opacity) {
        this.size = size;
        this.opacity = opacity;
        place(getWidth(), getHeight());
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        place(w, h);
    }

    private void place(int w, int h) {
        if (w == 0 || h == 0) {
            return;
        }
        float side = Math.min(w, h);
        float margin = Math.max(24 * dp, side * 0.06f);

        // The stick (or the wheel), low on the left
        stickRadius = Math.min(side * 0.22f, 110 * dp) * size;
        stickX = margin + stickRadius;
        stickY = h - margin - stickRadius;

        float r = Math.min(side * 0.085f, 42 * dp) * size;
        float gap = r * 2.4f;
        float baseX = w - margin - r - gap;
        float baseY = h - margin - r;
        switch (layout) {
        case DRIVE:
            // GAZ big in the corner, FREIN beside it, VUE and the gears above
            place(buttons.get(0), w - margin - r * 1.3f, h - margin - r * 1.3f, r * 1.3f);
            place(buttons.get(1), w - margin - r * 1.3f - gap * 1.2f, h - margin - r, r);
            place(buttons.get(2), w - margin - r * 1.3f - gap * 1.2f, h - margin - r - gap * 1.1f, r * 0.85f);
            place(buttons.get(3), w - margin - r, h - margin - r * 2.6f - gap * 0.9f, r * 0.8f);
            place(buttons.get(4), w - margin - r, h - margin - r * 2.6f - gap * 0.9f + gap * 0.75f * 0 - gap * 0.0f + r * 0.0f + gap * 0.75f, r * 0.8f);
            // V- below V+, above GAZ: keep them apart
            buttons.get(4).y = buttons.get(3).y + r * 1.9f;
            buttons.get(3).y = buttons.get(4).y - r * 1.9f;
            if (buttons.get(4).y + r * 0.8f > buttons.get(0).y - r * 1.3f) {
                float up = buttons.get(4).y + r * 0.8f - (buttons.get(0).y - r * 1.3f) + r * 0.3f;
                buttons.get(3).y -= up;
                buttons.get(4).y -= up;
            }
            break;
        case GUN:
            place(buttons.get(0), w - margin - r * 1.2f, h - margin - r * 1.2f, r * 1.2f);
            break;
        default:
            // 1 2 low (P K), 3 4 above (G E)
            place(buttons.get(0), baseX, baseY, r);
            place(buttons.get(1), baseX + gap, baseY - gap * 0.35f, r);
            place(buttons.get(2), baseX - gap * 0.15f, baseY - gap, r);
            place(buttons.get(3), baseX + gap * 0.85f, baseY - gap * 1.35f, r);
            break;
        }

        // COIN and START at the top, in the middle; MENU in the top left
        float s = Math.min(side * 0.04f, 20 * dp) * Math.max(1.0f, size);
        place(coin, w / 2f - s * 2.6f, margin * 0.5f + s, s);
        place(start, w / 2f + s * 2.6f, margin * 0.5f + s, s);
        place(menu, margin * 0.5f + s * 1.8f, margin * 0.5f + s, s);
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
                // (a gun's aim)
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
        if (hasStick) {
            float dx = x - stickX, dy = y - stickY;
            if (dx * dx + dy * dy <= stickRadius * stickRadius * 1.5f) {
                return this;
            }
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

    /** Eight directions past a small dead zone; the wheel: left or right */
    private void stick(float x, float y, Set<Integer> keys) {
        float dx = x - stickX, dy = horizontalStick ? 0 : y - stickY;
        float length = (float) Math.hypot(dx, dy);
        float max = stickRadius * 0.6f;
        knobShown = true;
        knobX = stickX + (length > max ? dx / length * max : dx);
        knobY = stickY + (length > max ? dy / length * max : dy);
        if (length < stickRadius * 0.25f) {
            return;
        }
        if (horizontalStick) {
            keys.add(dx > 0 ? RIGHT : LEFT);
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

    private int alpha(int value) {
        return Math.max(0, Math.min(255, Math.round(value * opacity)));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (minimal) {
            drawButton(canvas, menu);
            return;
        }
        if (hasStick) {
            // The stick: its ring, its arrows, the knob under the finger
            fill.setColor(Color.argb(alpha(40), 255, 255, 255));
            canvas.drawCircle(stickX, stickY, stickRadius, fill);
            stroke.setColor(Color.argb(alpha(110), 255, 255, 255));
            canvas.drawCircle(stickX, stickY, stickRadius, stroke);
            if (!horizontalStick) {
                drawArrow(canvas, 0, -1, pressed.contains(UP));
                drawArrow(canvas, 0, 1, pressed.contains(DOWN));
            }
            drawArrow(canvas, -1, 0, pressed.contains(LEFT));
            drawArrow(canvas, 1, 0, pressed.contains(RIGHT));
            fill.setColor(Color.argb(alpha(knobShown ? 140 : 70), 255, 255, 255));
            canvas.drawCircle(knobShown ? knobX : stickX, knobShown ? knobY : stickY, stickRadius * 0.38f, fill);
        }
        for (Button button : buttons) {
            drawButton(canvas, button);
        }
    }

    private void drawButton(Canvas canvas, Button button) {
        boolean down = button != menu && pressed.contains(button.key);
        // Dark under the light: seen on a white picture as on a black one
        fill.setColor(down ? Color.argb(alpha(170), 61, 139, 255) : Color.argb(alpha(70), 40, 40, 40));
        stroke.setColor(Color.argb(alpha(down ? 230 : 150), 255, 255, 255));
        if (button.small) {
            RectF box = new RectF(button.x - button.radius * 1.8f, button.y - button.radius,
                    button.x + button.radius * 1.8f, button.y + button.radius);
            canvas.drawRoundRect(box, button.radius, button.radius, fill);
            canvas.drawRoundRect(box, button.radius, button.radius, stroke);
            text.setTextSize(button.radius * 0.75f);
        } else {
            canvas.drawCircle(button.x, button.y, button.radius, fill);
            canvas.drawCircle(button.x, button.y, button.radius, stroke);
            // Long words (FREIN, RECHARGER) smaller, to fit in
            float scale = button.label.length() <= 2 ? 0.9f : Math.min(0.9f, 2.6f / button.label.length());
            text.setTextSize(button.radius * scale);
        }
        text.setColor(Color.argb(alpha(down ? 255 : 210), 255, 255, 255));
        text.setShadowLayer(3 * dp, 0, 0, Color.argb(alpha(160), 0, 0, 0));
        canvas.drawText(button.label, button.x, button.y - (text.descent() + text.ascent()) / 2, text);
    }

    private void drawArrow(Canvas canvas, int dx, int dy, boolean down) {
        float d = stickRadius * 0.78f, s = stickRadius * 0.12f;
        float x = stickX + dx * d, y = stickY + dy * d;
        fill.setColor(Color.argb(alpha(down ? 230 : 120), 255, 255, 255));
        Path path = new Path();
        // A triangle pointing outwards
        path.moveTo(x + dx * s, y + dy * s);
        path.lineTo(x - dx * s + dy * s, y - dy * s + dx * s);
        path.lineTo(x - dx * s - dy * s, y - dy * s - dx * s);
        path.close();
        canvas.drawPath(path, fill);
    }
}
