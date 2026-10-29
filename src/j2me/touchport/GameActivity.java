package j2me.touchport;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import java.io.File;
import java.util.Locale;

import dalvik.system.DexClassLoader;

import javax.microedition.lcdui.Canvas;
import javax.microedition.midlet.MIDlet;

/** Runs one imported game. Lives in its own process (":game") because MIDlets keep their state in statics. */
public class GameActivity extends Activity {
    static final String EXTRA_GAME_ID = "game";
    private static boolean started;
    private static String currentId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }

        Host.context = getApplicationContext();
        Host.activity = this;
        String id = getIntent().getStringExtra(EXTRA_GAME_ID);
        if (started && id != null && !id.equals(currentId)) {
            // A different game is still loaded in this process: start over in a fresh one.
            startActivity(new Intent(getIntent()));
            finishAndKill();
            return;
        }
        if (!started) {
            final Games.Game game = Games.get(this, id);
            if (game == null) {
                finish();
                return;
            }
            Host.gameDir = game.dir;
            Host.screenWidth = game.width();
            Host.screenHeight = game.height();
            Host.appProperties = game.midletProperties();
            Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                public void uncaughtException(Thread t, Throwable e) {
                    Host.fatal(e);
                }
            });
            started = true;
            currentId = id;
            startGame(game);
        }

        GameView view = new GameView(this);
        Host.view = view;
        setContentView(view);
        hideSystemUi();
    }

    private void startGame(final Games.Game game) {
        Locale loc = Locale.getDefault();
        System.setProperty("microedition.locale", loc.getLanguage() + "-" + loc.getCountry());
        System.setProperty("microedition.platform", "Nokia6300");
        System.setProperty("microedition.profiles", "MIDP-2.0");
        System.setProperty("microedition.configuration", "CLDC-1.1");

        File zip = new File(game.dir, Games.GAME_ZIP);
        // The game's classes resolve javax.microedition.* through our class loader (the parent).
        Host.gameLoader = new DexClassLoader(zip.getPath(), getCodeCacheDir().getPath(), null, getClassLoader());
        Host.startEventThread();
        Host.post(new Runnable() {
            public void run() {
                try {
                    MIDlet m = (MIDlet) Host.gameLoader.loadClass(game.mainClass()).newInstance();
                    m.callStartApp();
                } catch (Throwable t) {
                    Host.fatal(t);
                }
            }
        });
    }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUi();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Host.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Host.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isFinishing()) {
            Process.killProcess(Process.myPid());
        }
    }

    void finishAndKill() {
        finish();
        Process.killProcess(Process.myPid());
    }

    void confirmQuit() {
        Host.onPause();
        new AlertDialog.Builder(this)
                .setTitle("Quit game?")
                .setMessage("Progress the game hasn't saved yet will be lost.")
                .setPositiveButton("Quit", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int which) {
                        finishAndKill();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .setOnDismissListener(new DialogInterface.OnDismissListener() {
                    public void onDismiss(DialogInterface d) {
                        Host.onResume();
                        hideSystemUi();
                    }
                })
                .show();
    }

    private static int mapKey(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_W:
                return Canvas.NOKIA_UP;
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_S:
                return Canvas.NOKIA_DOWN;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_A:
                return Canvas.NOKIA_LEFT;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_D:
                return Canvas.NOKIA_RIGHT;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_SPACE:
            case KeyEvent.KEYCODE_BUTTON_A:
                return Canvas.NOKIA_FIRE;
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_Q:
            case KeyEvent.KEYCODE_BUTTON_START:
                return Canvas.NOKIA_SOFT_LEFT;
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_E:
            case KeyEvent.KEYCODE_BUTTON_B:
                return Canvas.NOKIA_SOFT_RIGHT;
            case KeyEvent.KEYCODE_STAR:
                return Canvas.KEY_STAR;
            case KeyEvent.KEYCODE_POUND:
                return Canvas.KEY_POUND;
            default:
                if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
                    return Canvas.KEY_NUM0 + (keyCode - KeyEvent.KEYCODE_0);
                }
                return 0;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        int k = mapKey(keyCode);
        if (k == 0) {
            return super.onKeyDown(keyCode, event);
        }
        if (event.getRepeatCount() == 0) {
            Host.keyPressed(k);
        }
        return true;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        int k = mapKey(keyCode);
        if (k == 0) {
            return super.onKeyUp(keyCode, event);
        }
        Host.keyReleased(k);
        return true;
    }
}
