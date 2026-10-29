package javax.microedition.lcdui;

import android.graphics.Paint;
import android.graphics.Typeface;

public final class Font {
    public static final int FACE_SYSTEM = 0;
    public static final int FACE_MONOSPACE = 32;
    public static final int FACE_PROPORTIONAL = 64;
    public static final int STYLE_PLAIN = 0;
    public static final int STYLE_BOLD = 1;
    public static final int STYLE_ITALIC = 2;
    public static final int STYLE_UNDERLINED = 4;
    public static final int SIZE_SMALL = 8;
    public static final int SIZE_MEDIUM = 0;
    public static final int SIZE_LARGE = 16;
    public static final int FONT_STATIC_TEXT = 0;
    public static final int FONT_INPUT_TEXT = 1;

    // Pixel sizes roughly matching a 240x320 Series 40 handset.
    private static final float PX_SMALL = 14f;
    private static final float PX_MEDIUM = 16f;
    private static final float PX_LARGE = 19f;

    private static final Font[] cache = new Font[128];
    private static Font defaultFont;

    private final int face, style, size;
    final Paint paint;
    final int ascent; // positive distance from top to baseline
    final int descent;
    private final int height;

    private Font(int face, int style, int size) {
        this.face = face;
        this.style = style;
        this.size = size;
        Typeface base = face == FACE_MONOSPACE ? Typeface.MONOSPACE : Typeface.SANS_SERIF;
        int tfStyle = ((style & STYLE_BOLD) != 0 ? Typeface.BOLD : 0) | ((style & STYLE_ITALIC) != 0 ? Typeface.ITALIC : 0);
        paint = new Paint();
        paint.setAntiAlias(true);
        paint.setSubpixelText(true);
        paint.setTypeface(Typeface.create(base, tfStyle));
        paint.setTextSize(size == SIZE_SMALL ? PX_SMALL : size == SIZE_LARGE ? PX_LARGE : PX_MEDIUM);
        paint.setUnderlineText((style & STYLE_UNDERLINED) != 0);
        Paint.FontMetricsInt fm = paint.getFontMetricsInt();
        ascent = -fm.ascent;
        descent = fm.descent;
        height = ascent + descent;
    }

    public static Font getFont(int face, int style, int size) {
        int key = ((face >> 5) & 3) << 5 | (style & 7) << 2 | ((size >> 3) & 3);
        synchronized (cache) {
            Font f = cache[key];
            if (f == null) {
                f = new Font(face, style, size);
                cache[key] = f;
            }
            return f;
        }
    }

    public static Font getFont(int fontSpecifier) {
        return getDefaultFont();
    }

    public static Font getDefaultFont() {
        if (defaultFont == null) {
            defaultFont = getFont(FACE_SYSTEM, STYLE_PLAIN, SIZE_MEDIUM);
        }
        return defaultFont;
    }

    public int getFace() {
        return face;
    }

    public int getStyle() {
        return style;
    }

    public int getSize() {
        return size;
    }

    public boolean isPlain() {
        return style == STYLE_PLAIN;
    }

    public boolean isBold() {
        return (style & STYLE_BOLD) != 0;
    }

    public boolean isItalic() {
        return (style & STYLE_ITALIC) != 0;
    }

    public boolean isUnderlined() {
        return (style & STYLE_UNDERLINED) != 0;
    }

    public int getHeight() {
        return height;
    }

    public int getBaselinePosition() {
        return ascent;
    }

    public int charWidth(char c) {
        return Math.round(paint.measureText(String.valueOf(c)));
    }

    public int charsWidth(char[] ch, int offset, int length) {
        return Math.round(paint.measureText(ch, offset, length));
    }

    public int stringWidth(String s) {
        return Math.round(paint.measureText(s));
    }

    public int substringWidth(String s, int offset, int len) {
        return Math.round(paint.measureText(s, offset, offset + len));
    }
}
