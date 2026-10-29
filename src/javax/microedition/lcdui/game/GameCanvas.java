package javax.microedition.lcdui.game;

import android.graphics.Bitmap;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Graphics;

import j2me.touchport.Host;

public abstract class GameCanvas extends Canvas {
    public static final int UP_PRESSED = 1 << UP;
    public static final int DOWN_PRESSED = 1 << DOWN;
    public static final int LEFT_PRESSED = 1 << LEFT;
    public static final int RIGHT_PRESSED = 1 << RIGHT;
    public static final int FIRE_PRESSED = 1 << FIRE;
    public static final int GAME_A_PRESSED = 1 << GAME_A;
    public static final int GAME_B_PRESSED = 1 << GAME_B;
    public static final int GAME_C_PRESSED = 1 << GAME_C;
    public static final int GAME_D_PRESSED = 1 << GAME_D;

    private final Bitmap buffer;

    protected GameCanvas(boolean suppressKeyEvents) {
        buffer = Bitmap.createBitmap(Host.screenWidth, Host.screenHeight, Bitmap.Config.ARGB_8888);
        buffer.eraseColor(0xFFFFFFFF);
    }

    protected Graphics getGraphics() {
        return new Graphics(buffer);
    }

    public int getKeyStates() {
        return Host.getKeyStates();
    }

    public void paint(Graphics g) {
        g.drawBitmap(buffer, 0, 0);
    }

    public void hostPaint() {
        Host.present(buffer);
    }

    public void flushGraphics() {
        Host.present(buffer);
    }

    public void flushGraphics(int x, int y, int w, int h) {
        Host.present(buffer);
    }
}
