package javax.microedition.lcdui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import j2me.touchport.Host;

public class Image {
    public final Bitmap bitmap;
    private final boolean mutable;

    public Image(Bitmap bitmap, boolean mutable) {
        this.bitmap = bitmap;
        this.mutable = mutable;
    }

    private static BitmapFactory.Options options() {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inScaled = false;
        o.inPreferredConfig = Bitmap.Config.ARGB_8888;
        return o;
    }

    public static Image createImage(int w, int h) {
        if (w <= 0 || h <= 0) {
            throw new IllegalArgumentException();
        }
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        b.eraseColor(0xFFFFFFFF);
        return new Image(b, true);
    }

    public static Image createImage(String name) throws IOException {
        InputStream in = Host.getResourceAsStream(name);
        if (in == null) {
            throw new IOException("Resource not found: " + name);
        }
        try {
            return createImage(in);
        } finally {
            in.close();
        }
    }

    public static Image createImage(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        byte[] data = out.toByteArray();
        return createImage(data, 0, data.length);
    }

    public static Image createImage(byte[] data, int offset, int length) {
        Bitmap b = BitmapFactory.decodeByteArray(data, offset, length, options());
        if (b == null) {
            throw new IllegalArgumentException("Cannot decode image");
        }
        return new Image(b, false);
    }

    public static Image createImage(Image src) {
        return src.mutable ? new Image(src.bitmap.copy(Bitmap.Config.ARGB_8888, false), false) : src;
    }

    public static Image createImage(Image src, int x, int y, int w, int h, int transform) {
        boolean swap = (transform & 4) != 0;
        Bitmap b = Bitmap.createBitmap(swap ? h : w, swap ? w : h, Bitmap.Config.ARGB_8888);
        new Graphics(b).drawRegion(src, x, y, w, h, transform, 0, 0, Graphics.TOP | Graphics.LEFT);
        return new Image(b, false);
    }

    public static Image createRGBImage(int[] rgb, int w, int h, boolean processAlpha) {
        int[] px = rgb;
        if (!processAlpha) {
            px = new int[w * h];
            for (int i = 0; i < px.length; i++) {
                px[i] = rgb[i] | 0xFF000000;
            }
        }
        Bitmap b = Bitmap.createBitmap(px, 0, w, w, h, Bitmap.Config.ARGB_8888);
        return new Image(b, false);
    }

    public Graphics getGraphics() {
        if (!mutable) {
            throw new IllegalStateException();
        }
        return new Graphics(bitmap);
    }

    public boolean isMutable() {
        return mutable;
    }

    public int getWidth() {
        return bitmap.getWidth();
    }

    public int getHeight() {
        return bitmap.getHeight();
    }

    public void getRGB(int[] rgb, int offset, int scanlength, int x, int y, int w, int h) {
        bitmap.getPixels(rgb, offset, scanlength, x, y, w, h);
    }
}
