package javax.microedition.lcdui;

import javax.microedition.midlet.MIDlet;

import j2me.touchport.Host;

public class Display {
    private static Display instance;
    private Displayable current;

    public static synchronized Display getDisplay(MIDlet m) {
        if (instance == null) {
            instance = new Display();
        }
        return instance;
    }

    public Displayable getCurrent() {
        return current;
    }

    public void setCurrent(final Displayable next) {
        if (next == null || next == current) {
            return;
        }
        final Displayable prev = current;
        current = next;
        Host.setCurrent(next);
        Host.post(new Runnable() {
            public void run() {
                if (prev != null) {
                    prev.hideNotify();
                }
                next.showNotify();
            }
        });
    }

    public boolean vibrate(int millis) {
        Host.vibrate(millis);
        return true;
    }

    public boolean flashBacklight(int millis) {
        return true;
    }

    public boolean isColor() {
        return true;
    }

    public int numColors() {
        return 1 << 24;
    }

    public int numAlphaLevels() {
        return 256;
    }

    public void callSerially(Runnable r) {
        Host.post(r);
    }
}
