package javax.microedition.lcdui;

import android.graphics.Bitmap;

import j2me.touchport.Host;

public abstract class Canvas extends Displayable {
    public static final int UP = 1;
    public static final int DOWN = 6;
    public static final int LEFT = 2;
    public static final int RIGHT = 5;
    public static final int FIRE = 8;
    public static final int GAME_A = 9;
    public static final int GAME_B = 10;
    public static final int GAME_C = 11;
    public static final int GAME_D = 12;

    public static final int KEY_NUM0 = 48;
    public static final int KEY_NUM1 = 49;
    public static final int KEY_NUM2 = 50;
    public static final int KEY_NUM3 = 51;
    public static final int KEY_NUM4 = 52;
    public static final int KEY_NUM5 = 53;
    public static final int KEY_NUM6 = 54;
    public static final int KEY_NUM7 = 55;
    public static final int KEY_NUM8 = 56;
    public static final int KEY_NUM9 = 57;
    public static final int KEY_STAR = 42;
    public static final int KEY_POUND = 35;

    // Nokia native key codes.
    public static final int NOKIA_UP = -1;
    public static final int NOKIA_DOWN = -2;
    public static final int NOKIA_LEFT = -3;
    public static final int NOKIA_RIGHT = -4;
    public static final int NOKIA_FIRE = -5;
    public static final int NOKIA_SOFT_LEFT = -6;
    public static final int NOKIA_SOFT_RIGHT = -7;

    private Bitmap screen;

    protected Canvas() {
    }

    protected abstract void paint(Graphics g);

    public int getGameAction(int keyCode) {
        switch (keyCode) {
            case NOKIA_UP:
            case KEY_NUM2:
                return UP;
            case NOKIA_DOWN:
            case KEY_NUM8:
                return DOWN;
            case NOKIA_LEFT:
            case KEY_NUM4:
                return LEFT;
            case NOKIA_RIGHT:
            case KEY_NUM6:
                return RIGHT;
            case NOKIA_FIRE:
            case KEY_NUM5:
                return FIRE;
            case KEY_NUM1:
                return GAME_A;
            case KEY_NUM3:
                return GAME_B;
            case KEY_NUM7:
                return GAME_C;
            case KEY_NUM9:
                return GAME_D;
            default:
                return 0;
        }
    }

    public int getKeyCode(int gameAction) {
        switch (gameAction) {
            case UP:
                return NOKIA_UP;
            case DOWN:
                return NOKIA_DOWN;
            case LEFT:
                return NOKIA_LEFT;
            case RIGHT:
                return NOKIA_RIGHT;
            case FIRE:
                return NOKIA_FIRE;
            case GAME_A:
                return KEY_NUM1;
            case GAME_B:
                return KEY_NUM3;
            case GAME_C:
                return KEY_NUM7;
            case GAME_D:
                return KEY_NUM9;
            default:
                return 0;
        }
    }

    public String getKeyName(int keyCode) {
        return String.valueOf(keyCode);
    }

    protected void keyPressed(int keyCode) {
    }

    protected void keyReleased(int keyCode) {
    }

    protected void keyRepeated(int keyCode) {
    }

    protected void pointerPressed(int x, int y) {
    }

    protected void pointerReleased(int x, int y) {
    }

    protected void pointerDragged(int x, int y) {
    }

    public boolean hasPointerEvents() {
        return false;
    }

    public boolean hasPointerMotionEvents() {
        return false;
    }

    public boolean hasRepeatEvents() {
        return false;
    }

    public boolean isDoubleBuffered() {
        return true;
    }

    public void setFullScreenMode(boolean mode) {
    }

    public final void repaint() {
        Host.requestRepaint(this);
    }

    public final void repaint(int x, int y, int w, int h) {
        Host.requestRepaint(this);
    }

    public final void serviceRepaints() {
        Host.serviceRepaints(this);
    }

    // Called on the event thread by the host.
    public void hostPaint() {
        if (screen == null) {
            screen = Bitmap.createBitmap(Host.screenWidth, Host.screenHeight, Bitmap.Config.ARGB_8888);
        }
        paint(new Graphics(screen));
        Host.present(screen);
    }

    public final void hostKeyPressed(int keyCode) {
        keyPressed(keyCode);
    }

    public final void hostKeyReleased(int keyCode) {
        keyReleased(keyCode);
    }
}
