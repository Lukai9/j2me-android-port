package j2me.touchport;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Vibrator;
import android.util.Log;

import java.io.File;
import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.LinkedBlockingQueue;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Displayable;
import javax.microedition.midlet.MIDlet;

/** Glue between the MIDP API implementation and the Android activity. */
public final class Host {
    private static final String TAG = "J2ME";

    // Virtual phone screen; set from the game's settings before the MIDlet is created.
    public static int screenWidth = 240;
    public static int screenHeight = 320;

    public static Context context;
    public static MIDlet midlet;
    static GameActivity activity;
    static GameView view;

    // The imported game: its folder, class loader and JAD/manifest properties.
    public static File gameDir;
    static ClassLoader gameLoader;
    public static Properties appProperties = new Properties();

    private static volatile Displayable current;
    private static volatile boolean paused;
    private static volatile boolean repaintPending;
    private static int keyStates;
    private static int latchedKeyStates;

    private static final LinkedBlockingQueue<Runnable> events = new LinkedBlockingQueue<Runnable>();
    private static Thread eventThread;

    private Host() {
    }

    static synchronized void startEventThread() {
        if (eventThread != null) {
            return;
        }
        eventThread = new Thread(new Runnable() {
            public void run() {
                while (true) {
                    try {
                        events.take().run();
                    } catch (InterruptedException e) {
                        return;
                    } catch (LinkageError e) {
                        // The game called an API this runtime doesn't implement.
                        fatal(e);
                    } catch (Throwable t) {
                        Log.e(TAG, "Event error", t);
                    }
                }
            }
        }, "midp-events");
        eventThread.start();
    }

    public static void post(Runnable r) {
        events.add(r);
    }

    /** Opens a resource from the game jar; names are absolute, as with Class.getResourceAsStream("/x"). */
    public static InputStream getResourceAsStream(String name) {
        if (name.startsWith("/")) {
            name = name.substring(1);
        }
        ClassLoader l = gameLoader;
        return l == null ? null : l.getResourceAsStream(name);
    }

    /** Records why the game died so the launcher can show it, then ends the game process. */
    static void fatal(Throwable t) {
        Log.e(TAG, "Game stopped", t);
        Games.writeLastError(context, t);
        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(1);
    }

    public static void setCurrent(Displayable d) {
        current = d;
        if (d instanceof Canvas) {
            requestRepaint((Canvas) d);
        }
    }

    public static boolean isCurrent(Displayable d) {
        return current == d;
    }

    public static void present(Bitmap frame) {
        GameView v = view;
        if (v != null) {
            v.present(frame);
        }
    }

    public static void requestRepaint(final Canvas c) {
        if (repaintPending) {
            return;
        }
        repaintPending = true;
        post(new Runnable() {
            public void run() {
                repaintPending = false;
                if (current == c) {
                    c.hostPaint();
                }
            }
        });
    }

    public static void serviceRepaints(Canvas c) {
        if (repaintPending && Thread.currentThread() == eventThread) {
            repaintPending = false;
            c.hostPaint();
        }
    }

    // ---- input ----

    private static int gameActionBit(int keyCode) {
        Displayable d = current;
        if (d instanceof Canvas) {
            int a = ((Canvas) d).getGameAction(keyCode);
            return a == 0 ? 0 : 1 << a;
        }
        return 0;
    }

    public static void keyPressed(final int keyCode) {
        synchronized (Host.class) {
            int bit = gameActionBit(keyCode);
            keyStates |= bit;
            latchedKeyStates |= bit;
        }
        post(new Runnable() {
            public void run() {
                Displayable d = current;
                if (d instanceof Canvas) {
                    ((Canvas) d).hostKeyPressed(keyCode);
                }
            }
        });
    }

    public static void keyReleased(final int keyCode) {
        synchronized (Host.class) {
            keyStates &= ~gameActionBit(keyCode);
        }
        post(new Runnable() {
            public void run() {
                Displayable d = current;
                if (d instanceof Canvas) {
                    ((Canvas) d).hostKeyReleased(keyCode);
                }
            }
        });
    }

    public static synchronized int getKeyStates() {
        int s = keyStates | latchedKeyStates;
        latchedKeyStates = 0;
        return s;
    }

    // ---- lifecycle ----

    public static boolean isPaused() {
        return paused;
    }

    static void onPause() {
        paused = true;
        AudioPlayer.pauseAll();
        post(new Runnable() {
            public void run() {
                Displayable d = current;
                if (d != null) {
                    d.hostHideNotify();
                }
                // Anything the game left playing gets paused too.
                AudioPlayer.pauseAll();
            }
        });
    }

    static void onResume() {
        paused = false;
        post(new Runnable() {
            public void run() {
                AudioPlayer.resumeAll();
                Displayable d = current;
                if (d != null) {
                    d.hostShowNotify();
                    if (d instanceof Canvas) {
                        requestRepaint((Canvas) d);
                    }
                }
            }
        });
    }

    public static void exit() {
        final GameActivity a = activity;
        if (a != null) {
            a.runOnUiThread(new Runnable() {
                public void run() {
                    a.finishAndKill();
                }
            });
        } else {
            System.exit(0);
        }
    }

    public static boolean openUrl(String url) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(i);
        } catch (Exception ignored) {
        }
        return false;
    }

    public static void vibrate(int ms) {
        try {
            Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) {
                if (ms > 0) {
                    v.vibrate(ms);
                } else {
                    v.cancel();
                }
            }
        } catch (Exception ignored) {
        }
    }
}
