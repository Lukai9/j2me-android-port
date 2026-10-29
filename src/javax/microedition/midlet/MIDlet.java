package javax.microedition.midlet;

import javax.microedition.io.ConnectionNotFoundException;

import j2me.touchport.Host;

public abstract class MIDlet {
    protected MIDlet() {
        Host.midlet = this;
    }

    protected abstract void startApp() throws MIDletStateChangeException;

    protected abstract void pauseApp();

    protected abstract void destroyApp(boolean unconditional) throws MIDletStateChangeException;

    public final void callStartApp() throws MIDletStateChangeException {
        startApp();
    }

    public final void callPauseApp() {
        pauseApp();
    }

    public final void callDestroyApp(boolean unconditional) throws MIDletStateChangeException {
        destroyApp(unconditional);
    }

    public final String getAppProperty(String key) {
        String v = Host.appProperties.getProperty(key);
        return v == null ? null : v.trim();
    }

    public final void notifyDestroyed() {
        Host.exit();
    }

    public final void notifyPaused() {
    }

    public final void resumeRequest() {
    }

    public final boolean platformRequest(String url) throws ConnectionNotFoundException {
        return Host.openUrl(url);
    }

    public final int checkPermission(String permission) {
        return 1;
    }
}
