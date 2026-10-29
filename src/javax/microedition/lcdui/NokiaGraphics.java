package javax.microedition.lcdui;

import android.graphics.Paint;
import android.graphics.Path;

import com.nokia.mid.ui.DirectGraphics;

/** Nokia UI API DirectGraphics, bound to one MIDP Graphics. */
public final class NokiaGraphics implements DirectGraphics {
    private final Graphics g;
    private final Paint argbFill = new Paint();
    private final Paint argbStroke = new Paint();
    private int argb;

    private NokiaGraphics(Graphics g) {
        this.g = g;
        argbFill.setStyle(Paint.Style.FILL);
        argbStroke.setStyle(Paint.Style.STROKE);
        argbStroke.setStrokeWidth(1f);
    }

    public static NokiaGraphics of(Graphics g) {
        if (g.nokia == null) {
            g.nokia = new NokiaGraphics(g);
        }
        return g.nokia;
    }

    public void drawImage(Image img, int x, int y, int anchor, int manipulation) {
        if (img == null) {
            throw new NullPointerException();
        }
        // Nokia rotates counter-clockwise first and flips afterwards; express that as
        // "mirror horizontally, then rotate clockwise".
        int ccw = ((manipulation & 0x0FFF) % 360 + 360) % 360;
        boolean h = (manipulation & FLIP_HORIZONTAL) != 0;
        boolean v = (manipulation & FLIP_VERTICAL) != 0;
        boolean mirror;
        int cw;
        if (h && v) {
            mirror = false;
            cw = (180 - ccw + 360) % 360;
        } else if (h) {
            mirror = true;
            cw = ccw;
        } else if (v) {
            mirror = true;
            cw = (180 + ccw) % 360;
        } else {
            mirror = false;
            cw = (360 - ccw) % 360;
        }
        g.drawTransformed(img.bitmap, 0, 0, img.getWidth(), img.getHeight(), mirror, cw, x, y, anchor);
    }

    public void fillTriangle(int x1, int y1, int x2, int y2, int x3, int y3, int argbColor) {
        argbFill.setColor(argbColor);
        g.fillTriangle(x1, y1, x2, y2, x3, y3, argbFill);
    }

    public void drawTriangle(int x1, int y1, int x2, int y2, int x3, int y3, int argbColor) {
        drawPolygon(new int[]{x1, x2, x3}, 0, new int[]{y1, y2, y3}, 0, 3, argbColor);
    }

    public void fillPolygon(int[] xs, int xOff, int[] ys, int yOff, int n, int argbColor) {
        argbFill.setColor(argbColor);
        g.drawPath(polygon(xs, xOff, ys, yOff, n, 0f), argbFill);
    }

    public void drawPolygon(int[] xs, int xOff, int[] ys, int yOff, int n, int argbColor) {
        argbStroke.setColor(argbColor);
        g.drawPath(polygon(xs, xOff, ys, yOff, n, 0.5f), argbStroke);
    }

    private static Path polygon(int[] xs, int xOff, int[] ys, int yOff, int n, float bias) {
        Path p = new Path();
        for (int i = 0; i < n; i++) {
            if (i == 0) {
                p.moveTo(xs[xOff] + bias, ys[yOff] + bias);
            } else {
                p.lineTo(xs[xOff + i] + bias, ys[yOff + i] + bias);
            }
        }
        p.close();
        return p;
    }

    public void setARGBColor(int argbColor) {
        argb = argbColor;
        g.setColor(argbColor & 0xFFFFFF);
    }

    public int getAlphaComponent() {
        return (argb >>> 24) & 0xFF;
    }

    public int getNativePixelFormat() {
        return TYPE_INT_8888_ARGB;
    }
}
