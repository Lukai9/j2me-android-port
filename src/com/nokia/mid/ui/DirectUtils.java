package com.nokia.mid.ui;

import android.graphics.Bitmap;

import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.NokiaGraphics;

public class DirectUtils {
    public static DirectGraphics getDirectGraphics(Graphics g) {
        return NokiaGraphics.of(g);
    }

    public static Image createImage(byte[] data, int offset, int length) {
        return Image.createImage(data, offset, length);
    }

    public static Image createImage(int w, int h, int argb) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        b.eraseColor(argb);
        return new Image(b, true);
    }
}
