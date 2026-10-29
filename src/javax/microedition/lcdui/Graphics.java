package javax.microedition.lcdui;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

public class Graphics {
    public static final int HCENTER = 1;
    public static final int VCENTER = 2;
    public static final int LEFT = 4;
    public static final int RIGHT = 8;
    public static final int TOP = 16;
    public static final int BOTTOM = 32;
    public static final int BASELINE = 64;
    public static final int SOLID = 0;
    public static final int DOTTED = 1;

    private final Bitmap target;
    private final android.graphics.Canvas canvas;
    private final Paint fill = new Paint();
    private final Paint stroke = new Paint();
    private final Paint bitmapPaint = new Paint();
    private final RectF rectF = new RectF();
    private final Matrix matrix = new Matrix();
    private int saveCount;
    NokiaGraphics nokia;

    private int tx, ty;
    private int clipX, clipY, clipW, clipH; // absolute (untranslated) coordinates
    private int color;
    private int strokeStyle = SOLID;
    private Font font = Font.getDefaultFont();

    public Graphics(Bitmap target) {
        this.target = target;
        this.canvas = new android.graphics.Canvas(target);
        fill.setStyle(Paint.Style.FILL);
        fill.setAntiAlias(false);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(1f);
        stroke.setAntiAlias(false);
        bitmapPaint.setFilterBitmap(false);
        bitmapPaint.setAntiAlias(false);
        clipW = target.getWidth();
        clipH = target.getHeight();
        saveCount = canvas.save();
        setColor(0);
    }

    // ---- state ----

    public void translate(int x, int y) {
        tx += x;
        ty += y;
    }

    public int getTranslateX() {
        return tx;
    }

    public int getTranslateY() {
        return ty;
    }

    public void setColor(int rgb) {
        color = rgb & 0xFFFFFF;
        fill.setColor(0xFF000000 | color);
        stroke.setColor(0xFF000000 | color);
    }

    public void setColor(int r, int g, int b) {
        setColor(((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF));
    }

    public int getColor() {
        return color;
    }

    public int getRedComponent() {
        return (color >> 16) & 0xFF;
    }

    public int getGreenComponent() {
        return (color >> 8) & 0xFF;
    }

    public int getBlueComponent() {
        return color & 0xFF;
    }

    public void setGrayScale(int v) {
        setColor(v, v, v);
    }

    public int getGrayScale() {
        return (getRedComponent() + getGreenComponent() + getBlueComponent()) / 3;
    }

    public int getDisplayColor(int c) {
        return c & 0xFFFFFF;
    }

    public void setFont(Font f) {
        font = f == null ? Font.getDefaultFont() : f;
    }

    public Font getFont() {
        return font;
    }

    public void setStrokeStyle(int style) {
        strokeStyle = style;
    }

    public int getStrokeStyle() {
        return strokeStyle;
    }

    // ---- clipping ----

    public int getClipX() {
        return clipX - tx;
    }

    public int getClipY() {
        return clipY - ty;
    }

    public int getClipWidth() {
        return clipW;
    }

    public int getClipHeight() {
        return clipH;
    }

    public void setClip(int x, int y, int w, int h) {
        setAbsClip(x + tx, y + ty, w, h, 0, 0, target.getWidth(), target.getHeight());
    }

    public void clipRect(int x, int y, int w, int h) {
        setAbsClip(x + tx, y + ty, w, h, clipX, clipY, clipW, clipH);
    }

    private void setAbsClip(int x, int y, int w, int h, int bx, int by, int bw, int bh) {
        int x1 = Math.max(x, bx);
        int y1 = Math.max(y, by);
        int x2 = Math.min(x + w, bx + bw);
        int y2 = Math.min(y + h, by + bh);
        clipX = x1;
        clipY = y1;
        clipW = Math.max(0, x2 - x1);
        clipH = Math.max(0, y2 - y1);
        canvas.restoreToCount(saveCount);
        saveCount = canvas.save();
        canvas.clipRect(clipX, clipY, clipX + clipW, clipY + clipH);
    }

    // ---- primitives ----

    public void fillRect(int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) {
            return;
        }
        x += tx;
        y += ty;
        canvas.drawRect(x, y, x + w, y + h, fill);
    }

    public void drawRect(int x, int y, int w, int h) {
        if (w < 0 || h < 0) {
            return;
        }
        fillRect(x, y, w + 1, 1);
        fillRect(x, y + h, w + 1, 1);
        fillRect(x, y, 1, h + 1);
        fillRect(x + w, y, 1, h + 1);
    }

    public void drawLine(int x1, int y1, int x2, int y2) {
        if (y1 == y2) {
            fillRect(Math.min(x1, x2), y1, Math.abs(x2 - x1) + 1, 1);
        } else if (x1 == x2) {
            fillRect(x1, Math.min(y1, y2), 1, Math.abs(y2 - y1) + 1);
        } else {
            canvas.drawLine(x1 + tx + 0.5f, y1 + ty + 0.5f, x2 + tx + 0.5f, y2 + ty + 0.5f, stroke);
            fillRect(x2, y2, 1, 1);
        }
    }

    public void fillArc(int x, int y, int w, int h, int startAngle, int arcAngle) {
        rectF.set(x + tx, y + ty, x + tx + w, y + ty + h);
        // MIDP angles run counter-clockwise; Android's run clockwise.
        canvas.drawArc(rectF, -startAngle, -arcAngle, true, fill);
    }

    public void drawArc(int x, int y, int w, int h, int startAngle, int arcAngle) {
        rectF.set(x + tx + 0.5f, y + ty + 0.5f, x + tx + w + 0.5f, y + ty + h + 0.5f);
        canvas.drawArc(rectF, -startAngle, -arcAngle, false, stroke);
    }

    public void fillRoundRect(int x, int y, int w, int h, int aw, int ah) {
        rectF.set(x + tx, y + ty, x + tx + w, y + ty + h);
        canvas.drawRoundRect(rectF, aw / 2f, ah / 2f, fill);
    }

    public void drawRoundRect(int x, int y, int w, int h, int aw, int ah) {
        rectF.set(x + tx + 0.5f, y + ty + 0.5f, x + tx + w + 0.5f, y + ty + h + 0.5f);
        canvas.drawRoundRect(rectF, aw / 2f, ah / 2f, stroke);
    }

    public void fillTriangle(int x1, int y1, int x2, int y2, int x3, int y3) {
        fillTriangle(x1, y1, x2, y2, x3, y3, fill);
    }

    void fillTriangle(int x1, int y1, int x2, int y2, int x3, int y3, Paint p) {
        Path path = new Path();
        path.moveTo(x1 + tx, y1 + ty);
        path.lineTo(x2 + tx, y2 + ty);
        path.lineTo(x3 + tx, y3 + ty);
        path.close();
        canvas.drawPath(path, p);
    }

    void drawPath(Path path, Paint p) {
        path.offset(tx, ty);
        canvas.drawPath(path, p);
    }

    // ---- text ----

    public void drawString(String s, int x, int y, int anchor) {
        if (s == null) {
            return;
        }
        Paint p = font.paint;
        p.setColor(0xFF000000 | color);
        if ((anchor & HCENTER) != 0) {
            x -= font.stringWidth(s) / 2;
        } else if ((anchor & RIGHT) != 0) {
            x -= font.stringWidth(s);
        }
        int baseline;
        if ((anchor & BOTTOM) != 0) {
            baseline = y - font.descent;
        } else if ((anchor & BASELINE) != 0) {
            baseline = y;
        } else if ((anchor & VCENTER) != 0) {
            baseline = y - font.getHeight() / 2 + font.ascent;
        } else {
            baseline = y + font.ascent;
        }
        canvas.drawText(s, x + tx, baseline + ty, p);
    }

    public void drawSubstring(String s, int offset, int len, int x, int y, int anchor) {
        drawString(s.substring(offset, offset + len), x, y, anchor);
    }

    public void drawChar(char c, int x, int y, int anchor) {
        drawString(String.valueOf(c), x, y, anchor);
    }

    public void drawChars(char[] data, int offset, int len, int x, int y, int anchor) {
        drawString(new String(data, offset, len), x, y, anchor);
    }

    // ---- images ----

    public void drawImage(Image img, int x, int y, int anchor) {
        if (img == null) {
            throw new NullPointerException();
        }
        Bitmap b = img.bitmap;
        int w = b.getWidth();
        int h = b.getHeight();
        if ((anchor & HCENTER) != 0) {
            x -= w / 2;
        } else if ((anchor & RIGHT) != 0) {
            x -= w;
        }
        if ((anchor & VCENTER) != 0) {
            y -= h / 2;
        } else if ((anchor & BOTTOM) != 0) {
            y -= h;
        }
        canvas.drawBitmap(b, x + tx, y + ty, bitmapPaint);
    }

    public void drawBitmap(Bitmap b, int x, int y) {
        canvas.drawBitmap(b, x + tx, y + ty, bitmapPaint);
    }

    // MIDP 2 Sprite transform constants, as used by drawRegion.
    private static final boolean[] TRANS_MIRROR = {false, true, true, false, true, false, false, true};
    private static final int[] TRANS_ROT = {0, 180, 0, 180, 270, 90, 270, 90};

    public void drawRegion(Image src, int sx, int sy, int w, int h, int transform, int x, int y, int anchor) {
        drawTransformed(src.bitmap, sx, sy, w, h, TRANS_MIRROR[transform & 7], TRANS_ROT[transform & 7], x, y, anchor);
    }

    /**
     * Draws a region of a bitmap, optionally mirrored horizontally first and then rotated clockwise by a
     * multiple of 90 degrees, with the anchor applied to the transformed bounds.
     */
    void drawTransformed(Bitmap b, int sx, int sy, int sw, int sh, boolean mirror, int cwRot,
                         int x, int y, int anchor) {
        boolean swap = cwRot == 90 || cwRot == 270;
        int dw = swap ? sh : sw;
        int dh = swap ? sw : sh;
        if ((anchor & HCENTER) != 0) {
            x -= dw / 2;
        } else if ((anchor & RIGHT) != 0) {
            x -= dw;
        }
        if ((anchor & VCENTER) != 0) {
            y -= dh / 2;
        } else if ((anchor & BOTTOM) != 0) {
            y -= dh;
        }
        int dx = x + tx;
        int dy = y + ty;
        boolean full = sx == 0 && sy == 0 && sw == b.getWidth() && sh == b.getHeight();
        if (!mirror && cwRot == 0) {
            if (full) {
                canvas.drawBitmap(b, dx, dy, bitmapPaint);
            } else {
                canvas.save();
                canvas.clipRect(dx, dy, dx + sw, dy + sh);
                canvas.drawBitmap(b, dx - sx, dy - sy, bitmapPaint);
                canvas.restore();
            }
            return;
        }
        Matrix m = matrix;
        m.setTranslate(-sx, -sy);
        if (mirror) {
            m.postScale(-1f, 1f);
            m.postTranslate(sw, 0);
        }
        if (cwRot != 0) {
            m.postRotate(cwRot);
            switch (cwRot) {
                case 90:
                    m.postTranslate(sh, 0);
                    break;
                case 180:
                    m.postTranslate(sw, sh);
                    break;
                case 270:
                    m.postTranslate(0, sw);
                    break;
            }
        }
        m.postTranslate(dx, dy);
        canvas.save();
        canvas.clipRect(dx, dy, dx + dw, dy + dh);
        canvas.drawBitmap(b, m, bitmapPaint);
        canvas.restore();
    }

    public void drawRGB(int[] rgb, int offset, int scanlength, int x, int y, int w, int h, boolean processAlpha) {
        if (w <= 0 || h <= 0) {
            return;
        }
        int[] px = new int[w * h];
        for (int row = 0; row < h; row++) {
            for (int col = 0; col < w; col++) {
                int v = rgb[offset + row * scanlength + col];
                px[row * w + col] = processAlpha ? v : (v | 0xFF000000);
            }
        }
        Bitmap tmp = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
        canvas.drawBitmap(tmp, x + tx, y + ty, bitmapPaint);
        tmp.recycle();
    }

    public void copyArea(int sx, int sy, int w, int h, int x, int y, int anchor) {
        Bitmap region = Bitmap.createBitmap(target, sx + tx, sy + ty, w, h);
        drawImage(new Image(region, false), x, y, anchor);
    }
}
