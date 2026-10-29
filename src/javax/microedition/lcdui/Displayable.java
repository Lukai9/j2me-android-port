package javax.microedition.lcdui;

import j2me.touchport.Host;

public abstract class Displayable {
    public int getWidth() {
        return Host.screenWidth;
    }

    public int getHeight() {
        return Host.screenHeight;
    }

    public boolean isShown() {
        return Host.isCurrent(this);
    }

    protected void showNotify() {
    }

    protected void hideNotify() {
    }

    protected void sizeChanged(int w, int h) {
    }

    // Bridges for the host, since the callbacks above are protected.
    public final void hostShowNotify() {
        showNotify();
    }

    public final void hostHideNotify() {
        hideNotify();
    }
}
