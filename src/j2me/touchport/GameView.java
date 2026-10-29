package j2me.touchport;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

/** Shows the 240x320 game screen scaled up, with an on-screen Nokia keypad (D-pad, OK, softkeys). */
final class GameView extends View {
    // Key slots; each maps to a Nokia key code.
    private static final int K_UP = 0, K_DOWN = 1, K_LEFT = 2, K_RIGHT = 3, K_FIRE = 4, K_SOFT_L = 5, K_SOFT_R = 6;
    private static final int[] KEY_CODES = {-1, -2, -3, -4, -5, -6, -7};
    private static final int CTRL_NONE = 0, CTRL_DPAD = 1, CTRL_FIRE = 2, CTRL_SOFT_L = 3, CTRL_SOFT_R = 4, CTRL_QUIT = 5;
    private static final int MAX_POINTERS = 10;

    private final Bitmap front = Bitmap.createBitmap(Host.screenWidth, Host.screenHeight, Bitmap.Config.ARGB_8888);
    private final Canvas frontCanvas = new Canvas(front);
    private final Paint screenPaint = new Paint();
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect screenRect = new Rect();
    private final RectF tmp = new RectF();
    private final Path arrow = new Path();

    private float dpadX, dpadY, dpadR;
    private float fireX, fireY, fireR;
    private final RectF softL = new RectF();
    private final RectF softR = new RectF();
    private float quitX, quitY, quitR;
    private boolean overlay;

    private final int[] pointerIds = new int[MAX_POINTERS];
    private final int[] pointerCtrl = new int[MAX_POINTERS];
    private final int[] pointerMask = new int[MAX_POINTERS];
    private int pressedMask;

    GameView(Context ctx) {
        super(ctx);
        front.eraseColor(0xFF000000);
        screenPaint.setFilterBitmap(false);
        strokePaint.setStyle(Paint.Style.STROKE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
        for (int i = 0; i < MAX_POINTERS; i++) {
            pointerIds[i] = -1;
        }
        setFocusable(true);
        setFocusableInTouchMode(true);
        setHapticFeedbackEnabled(true);
    }

    void present(Bitmap frame) {
        synchronized (front) {
            frontCanvas.drawBitmap(frame, 0, 0, null);
        }
        postInvalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        float density = getResources().getDisplayMetrics().density;
        boolean portrait = h >= w;
        float maxW = w;
        float maxH = portrait ? h * 0.60f : h;
        float scale = Math.min(maxW / Host.screenWidth, maxH / Host.screenHeight);
        // Whole-number scaling keeps the pixel art crisp; only fall back to smoothing when small.
        if (scale >= 2f) {
            scale = (float) Math.floor(scale);
            screenPaint.setFilterBitmap(false);
        } else {
            screenPaint.setFilterBitmap(true);
        }
        int sw = Math.round(Host.screenWidth * scale);
        int sh = Math.round(Host.screenHeight * scale);
        float pad = 12 * density;

        if (portrait) {
            int left = (w - sw) / 2;
            int top = (int) Math.min(pad * 0.5f, Math.max(0, h - sh) / 2f);
            screenRect.set(left, top, left + sw, top + sh);
            float areaTop = screenRect.bottom;
            float areaH = h - areaTop;
            overlay = areaH < 150 * density;
            if (overlay) {
                areaTop = h * 0.55f;
                areaH = h - areaTop;
            }
            float softH = Math.max(34 * density, Math.min(48 * density, areaH * 0.14f));
            float softW = w * 0.30f;
            float softTop = areaTop + pad * 0.8f;
            softL.set(pad, softTop, pad + softW, softTop + softH);
            softR.set(w - pad - softW, softTop, w - pad, softTop + softH);
            float restTop = softL.bottom + pad;
            float restH = h - restTop - pad;
            dpadR = Math.max(40 * density, Math.min(w * 0.25f, restH * 0.46f));
            dpadX = pad + dpadR + w * 0.02f;
            dpadY = restTop + restH / 2f;
            fireR = dpadR * 0.58f;
            fireX = w - pad - fireR - w * 0.06f;
            fireY = dpadY;
            quitR = softH * 0.36f;
            quitX = w / 2f;
            quitY = softL.centerY();
        } else {
            int left = (w - sw) / 2;
            int top = (h - sh) / 2;
            screenRect.set(left, top, left + sw, top + sh);
            float side = left;
            overlay = side < 120 * density;
            if (overlay) {
                side = w * 0.28f;
            }
            float softH = 40 * density;
            float softW = Math.min(side - 2 * pad, 130 * density);
            softL.set(pad, pad * 1.5f, pad + softW, pad * 1.5f + softH);
            softR.set(w - pad - softW, pad * 1.5f, w - pad, pad * 1.5f + softH);
            dpadR = Math.min((side - 2 * pad) / 2f, h * 0.30f);
            dpadX = side / 2f;
            dpadY = h * 0.62f;
            fireR = dpadR * 0.62f;
            fireX = w - side / 2f;
            fireY = h * 0.62f;
            quitR = softH * 0.36f;
            quitX = softR.centerX();
            quitY = softR.bottom + pad + quitR;
        }
        textPaint.setTextSize(Math.max(12 * density, softL.height() * 0.42f));
    }

    // ---- drawing ----

    @Override
    protected void onDraw(Canvas c) {
        c.drawColor(0xFF101114);
        synchronized (front) {
            c.drawBitmap(front, null, screenRect, screenPaint);
        }
        int alpha = overlay ? 0x55 : 0xFF;
        drawSoftKey(c, softL, (pressedMask & (1 << K_SOFT_L)) != 0, alpha);
        drawSoftKey(c, softR, (pressedMask & (1 << K_SOFT_R)) != 0, alpha);
        drawDpad(c, alpha);
        drawFire(c, alpha);
        drawQuit(c, alpha);
    }

    private void drawQuit(Canvas c, int alpha) {
        fillPaint.setColor(withAlpha(0x2A2E36, alpha));
        c.drawCircle(quitX, quitY, quitR, fillPaint);
        strokePaint.setStrokeWidth(Math.max(2f, quitR * 0.14f));
        strokePaint.setColor(withAlpha(0x8A909C, alpha));
        float s = quitR * 0.38f;
        c.drawLine(quitX - s, quitY - s, quitX + s, quitY + s, strokePaint);
        c.drawLine(quitX - s, quitY + s, quitX + s, quitY - s, strokePaint);
    }

    private int withAlpha(int rgb, int alpha) {
        return (alpha << 24) | (rgb & 0xFFFFFF);
    }

    private void drawSoftKey(Canvas c, RectF r, boolean down, int alpha) {
        float rad = r.height() / 2f;
        fillPaint.setColor(withAlpha(down ? 0x5A6273 : 0x2A2E36, alpha));
        c.drawRoundRect(r, rad, rad, fillPaint);
        strokePaint.setStrokeWidth(2f);
        strokePaint.setColor(withAlpha(0x4A505C, alpha));
        c.drawRoundRect(r, rad, rad, strokePaint);
        // A bar like the softkey on a Nokia handset.
        float bw = r.width() * 0.32f;
        float bh = Math.max(4f, r.height() * 0.12f);
        tmp.set(r.centerX() - bw / 2, r.centerY() - bh / 2, r.centerX() + bw / 2, r.centerY() + bh / 2);
        fillPaint.setColor(withAlpha(0xC8CDD6, alpha));
        c.drawRoundRect(tmp, bh / 2, bh / 2, fillPaint);
    }

    private void drawDpad(Canvas c, int alpha) {
        fillPaint.setColor(withAlpha(0x22252C, alpha));
        c.drawCircle(dpadX, dpadY, dpadR, fillPaint);
        strokePaint.setStrokeWidth(3f);
        strokePaint.setColor(withAlpha(0x3C414C, alpha));
        c.drawCircle(dpadX, dpadY, dpadR, strokePaint);

        // Highlight the pressed direction(s).
        int dirs = pressedMask & 0xF;
        if (dirs != 0) {
            fillPaint.setColor(withAlpha(0x3F4656, alpha));
            tmp.set(dpadX - dpadR, dpadY - dpadR, dpadX + dpadR, dpadY + dpadR);
            if ((dirs & (1 << K_RIGHT)) != 0) c.drawArc(tmp, -45, 90, true, fillPaint);
            if ((dirs & (1 << K_DOWN)) != 0) c.drawArc(tmp, 45, 90, true, fillPaint);
            if ((dirs & (1 << K_LEFT)) != 0) c.drawArc(tmp, 135, 90, true, fillPaint);
            if ((dirs & (1 << K_UP)) != 0) c.drawArc(tmp, 225, 90, true, fillPaint);
        }
        fillPaint.setColor(withAlpha(0x2C3038, alpha));
        c.drawCircle(dpadX, dpadY, dpadR * 0.22f, fillPaint);

        drawArrow(c, 0, -1, (dirs & (1 << K_UP)) != 0, alpha);
        drawArrow(c, 0, 1, (dirs & (1 << K_DOWN)) != 0, alpha);
        drawArrow(c, -1, 0, (dirs & (1 << K_LEFT)) != 0, alpha);
        drawArrow(c, 1, 0, (dirs & (1 << K_RIGHT)) != 0, alpha);
    }

    private void drawArrow(Canvas c, int dx, int dy, boolean down, int alpha) {
        float d = dpadR * 0.62f;
        float s = dpadR * 0.17f;
        float cx = dpadX + dx * d;
        float cy = dpadY + dy * d;
        arrow.reset();
        // Tip points outward along (dx, dy).
        arrow.moveTo(cx + dx * s, cy + dy * s);
        arrow.lineTo(cx - dx * s * 0.6f + dy * s, cy - dy * s * 0.6f + dx * s);
        arrow.lineTo(cx - dx * s * 0.6f - dy * s, cy - dy * s * 0.6f - dx * s);
        arrow.close();
        fillPaint.setColor(withAlpha(down ? 0xFFFFFF : 0xAAB0BC, alpha));
        c.drawPath(arrow, fillPaint);
    }

    private void drawFire(Canvas c, int alpha) {
        boolean down = (pressedMask & (1 << K_FIRE)) != 0;
        fillPaint.setColor(withAlpha(down ? 0x3E8E5A : 0x2E6B45, alpha));
        c.drawCircle(fireX, fireY, fireR, fillPaint);
        strokePaint.setStrokeWidth(3f);
        strokePaint.setColor(withAlpha(down ? 0x7FD69C : 0x4C9A68, alpha));
        c.drawCircle(fireX, fireY, fireR, strokePaint);
        textPaint.setColor(withAlpha(0xFFFFFF, alpha));
        float ts = textPaint.getTextSize();
        textPaint.setTextSize(fireR * 0.48f);
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        c.drawText("OK", fireX, fireY - (fm.ascent + fm.descent) / 2f, textPaint);
        textPaint.setTextSize(ts);
    }

    // ---- touch ----

    private int controlAt(float x, float y) {
        float dx = x - dpadX, dy = y - dpadY;
        if (dx * dx + dy * dy <= sq(dpadR * 1.3f)) {
            return CTRL_DPAD;
        }
        dx = x - fireX;
        dy = y - fireY;
        if (dx * dx + dy * dy <= sq(fireR * 1.35f)) {
            return CTRL_FIRE;
        }
        float m = softL.height() * 0.35f;
        if (x >= softL.left - m && x <= softL.right + m && y >= softL.top - m && y <= softL.bottom + m) {
            return CTRL_SOFT_L;
        }
        if (x >= softR.left - m && x <= softR.right + m && y >= softR.top - m && y <= softR.bottom + m) {
            return CTRL_SOFT_R;
        }
        dx = x - quitX;
        dy = y - quitY;
        if (dx * dx + dy * dy <= sq(quitR * 1.4f)) {
            return CTRL_QUIT;
        }
        return CTRL_NONE;
    }

    private static float sq(float v) {
        return v * v;
    }

    private int maskFor(int ctrl, float x, float y) {
        switch (ctrl) {
            case CTRL_DPAD: {
                float dx = x - dpadX, dy = y - dpadY;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                if (dist < dpadR * 0.18f) {
                    return 0;
                }
                // sin(22.5 deg): gives eight equal 45-degree sectors including diagonals.
                float t = 0.3827f * dist;
                int m = 0;
                if (dy < -t) m |= 1 << K_UP;
                if (dy > t) m |= 1 << K_DOWN;
                if (dx < -t) m |= 1 << K_LEFT;
                if (dx > t) m |= 1 << K_RIGHT;
                return m;
            }
            case CTRL_FIRE: {
                float dx = x - fireX, dy = y - fireY;
                return dx * dx + dy * dy <= sq(fireR * 1.6f) ? 1 << K_FIRE : 0;
            }
            case CTRL_SOFT_L:
                return controlAt(x, y) == CTRL_SOFT_L ? 1 << K_SOFT_L : 0;
            case CTRL_SOFT_R:
                return controlAt(x, y) == CTRL_SOFT_R ? 1 << K_SOFT_R : 0;
            default:
                return 0;
        }
    }

    private int slotFor(int id, boolean create) {
        for (int i = 0; i < MAX_POINTERS; i++) {
            if (pointerIds[i] == id) {
                return i;
            }
        }
        if (create) {
            for (int i = 0; i < MAX_POINTERS; i++) {
                if (pointerIds[i] == -1) {
                    pointerIds[i] = id;
                    return i;
                }
            }
        }
        return -1;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int idx = e.getActionIndex();
                int slot = slotFor(e.getPointerId(idx), true);
                if (slot >= 0) {
                    float x = e.getX(idx), y = e.getY(idx);
                    pointerCtrl[slot] = controlAt(x, y);
                    pointerMask[slot] = maskFor(pointerCtrl[slot], x, y);
                }
                break;
            }
            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < e.getPointerCount(); i++) {
                    int slot = slotFor(e.getPointerId(i), false);
                    if (slot >= 0) {
                        pointerMask[slot] = maskFor(pointerCtrl[slot], e.getX(i), e.getY(i));
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                int idx = e.getActionIndex();
                int slot = slotFor(e.getPointerId(idx), false);
                if (slot >= 0) {
                    if (pointerCtrl[slot] == CTRL_QUIT && controlAt(e.getX(idx), e.getY(idx)) == CTRL_QUIT
                            && Host.activity != null) {
                        Host.activity.confirmQuit();
                    }
                    pointerIds[slot] = -1;
                    pointerMask[slot] = 0;
                    pointerCtrl[slot] = CTRL_NONE;
                }
                break;
            }
            case MotionEvent.ACTION_CANCEL:
                for (int i = 0; i < MAX_POINTERS; i++) {
                    pointerIds[i] = -1;
                    pointerMask[i] = 0;
                    pointerCtrl[i] = CTRL_NONE;
                }
                break;
            default:
                return true;
        }
        updatePressed();
        return true;
    }

    private void updatePressed() {
        int mask = 0;
        for (int i = 0; i < MAX_POINTERS; i++) {
            mask |= pointerMask[i];
        }
        int changed = mask ^ pressedMask;
        if (changed == 0) {
            return;
        }
        // Releases first so a slide from one direction to another arrives in order.
        for (int k = 0; k < KEY_CODES.length; k++) {
            if ((changed & (1 << k)) != 0 && (mask & (1 << k)) == 0) {
                Host.keyReleased(KEY_CODES[k]);
            }
        }
        boolean newPress = false;
        for (int k = 0; k < KEY_CODES.length; k++) {
            if ((changed & (1 << k)) != 0 && (mask & (1 << k)) != 0) {
                Host.keyPressed(KEY_CODES[k]);
                newPress = true;
            }
        }
        if (newPress) {
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        }
        pressedMask = mask;
        invalidate();
    }
}
