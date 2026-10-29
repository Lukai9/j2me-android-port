package j2me.touchport;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** Game library: import .jar files, pick a game to play, change its screen size or delete it. */
public class LauncherActivity extends Activity {
    private static final int REQ_IMPORT = 1;

    private LinearLayout gameList;
    private TextView emptyText;
    private boolean importing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int pad = dp(16);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        emptyText = new TextView(this);
        emptyText.setText("No games yet.\n\nThis app doesn't include any games. Import the .jar file of a J2ME "
                + "phone game you own, and it will be converted to run here with touch controls.");
        emptyText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        emptyText.setPadding(0, pad, 0, pad);
        root.addView(emptyText);

        gameList = new LinearLayout(this);
        gameList.setOrientation(LinearLayout.VERTICAL);
        root.addView(gameList);

        Button importButton = new Button(this);
        importButton.setText("Import .jar game");
        importButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                pickJar();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = pad;
        root.addView(importButton, lp);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);

        handleIncoming(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIncoming(intent);
    }

    /** "Open with" / "Share" a .jar from a file manager. */
    private void handleIncoming(Intent intent) {
        Uri uri = null;
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            uri = intent.getData();
        } else if (Intent.ACTION_SEND.equals(intent.getAction())) {
            uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        }
        if (uri != null) {
            intent.setAction(Intent.ACTION_MAIN);
            importJar(uri);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        final String error = Games.takeLastError(this);
        if (error != null) {
            showError(error);
        }
    }

    private void refresh() {
        gameList.removeAllViews();
        List<Games.Game> games = Games.list(this);
        emptyText.setVisibility(games.isEmpty() ? View.VISIBLE : View.GONE);
        for (Games.Game g : games) {
            gameList.addView(row(g));
        }
    }

    private View row(final Games.Game g) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(12), dp(8), dp(12));
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
        row.setBackgroundResource(tv.resourceId);

        ImageView icon = new ImageView(this);
        Bitmap bmp = g.icon() == null ? null : BitmapFactory.decodeFile(g.icon().getPath());
        if (bmp != null) {
            BitmapDrawable d = new BitmapDrawable(getResources(), bmp);
            d.setFilterBitmap(false); // tiny pixel-art icons: scale without blurring
            icon.setImageDrawable(d);
        } else {
            icon.setImageResource(R.mipmap.ic_launcher);
        }
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(48), dp(48));
        ilp.rightMargin = dp(16);
        row.addView(icon, ilp);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView name = new TextView(this);
        name.setText(g.name());
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        texts.addView(name);
        TextView meta = new TextView(this);
        StringBuilder sb = new StringBuilder();
        if (g.vendor().length() > 0) {
            sb.append(g.vendor()).append(" · ");
        }
        if (g.version().length() > 0) {
            sb.append("v").append(g.version()).append(" · ");
        }
        sb.append(Games.sizeLabel(g.width(), g.height()));
        meta.setText(sb);
        meta.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        meta.setAlpha(0.7f);
        texts.addView(meta);
        row.addView(texts, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                play(g);
            }
        });
        row.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) {
                showGameMenu(g);
                return true;
            }
        });
        return row;
    }

    private void play(Games.Game g) {
        startActivity(new Intent(this, GameActivity.class).putExtra(GameActivity.EXTRA_GAME_ID, g.id()));
    }

    private void showGameMenu(final Games.Game g) {
        String[] items = {"Screen size (" + Games.sizeLabel(g.width(), g.height()) + ")", "Delete"};
        new AlertDialog.Builder(this)
                .setTitle(g.name())
                .setItems(items, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int which) {
                        if (which == 0) {
                            showSizePicker(g);
                        } else {
                            confirmDelete(g);
                        }
                    }
                })
                .show();
    }

    private void showSizePicker(final Games.Game g) {
        final int[][] sizes = Games.sizesFor(g);
        String[] labels = new String[sizes.length];
        int checked = -1;
        for (int i = 0; i < sizes.length; i++) {
            labels[i] = Games.sizeLabel(sizes[i][0], sizes[i][1]);
            if (sizes[i][0] == g.width() && sizes[i][1] == g.height()) {
                checked = i;
            }
        }
        new AlertDialog.Builder(this)
                .setTitle("Screen size")
                .setSingleChoiceItems(labels, checked, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int which) {
                        try {
                            g.setScreenSize(sizes[which][0], sizes[which][1]);
                        } catch (Exception e) {
                            Toast.makeText(LauncherActivity.this, "Couldn't save: " + e, Toast.LENGTH_LONG).show();
                        }
                        d.dismiss();
                        refresh();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void confirmDelete(final Games.Game g) {
        new AlertDialog.Builder(this)
                .setTitle("Delete " + g.name() + "?")
                .setMessage("This removes the game and its saved progress from this app. Your original .jar file isn't touched.")
                .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int which) {
                        Games.delete(g);
                        refresh();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // ---- import ----

    private void pickJar() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        // Providers label .jar files inconsistently, so accept anything and validate after.
        i.setType("*/*");
        startActivityForResult(i, REQ_IMPORT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_IMPORT && resultCode == RESULT_OK && data != null && data.getData() != null) {
            importJar(data.getData());
        }
    }

    @SuppressWarnings("deprecation")
    private void importJar(final Uri uri) {
        if (importing) {
            return;
        }
        importing = true;
        final ProgressDialog progress = ProgressDialog.show(this, null, "Converting game…", true, false);
        new Thread(new Runnable() {
            public void run() {
                Games.Game game = null;
                Exception error = null;
                try {
                    game = Games.importJar(LauncherActivity.this, uri);
                } catch (Exception e) {
                    error = e;
                }
                final Games.Game g = game;
                final Exception err = error;
                runOnUiThread(new Runnable() {
                    public void run() {
                        importing = false;
                        progress.dismiss();
                        refresh();
                        if (err != null) {
                            new AlertDialog.Builder(LauncherActivity.this)
                                    .setTitle("Couldn't import")
                                    .setMessage(err.getMessage() != null ? err.getMessage() : err.toString())
                                    .setPositiveButton(android.R.string.ok, null)
                                    .show();
                        } else {
                            play(g);
                        }
                    }
                });
            }
        }, "import").start();
    }

    // ---- errors from the game process ----

    private void showError(final String report) {
        String first = report.split("\n", 2)[0];
        boolean unsupported = first.contains("NoClassDefFoundError") || first.contains("NoSuchMethodError")
                || first.contains("NoSuchFieldError") || first.contains("AbstractMethodError")
                || first.contains("ClassNotFoundException");
        String msg = unsupported
                ? "This game uses a phone feature the app doesn't support yet:\n\n" + first
                : "The game stopped with an error:\n\n" + first;
        new AlertDialog.Builder(this)
                .setTitle("Game stopped")
                .setMessage(msg)
                .setPositiveButton(android.R.string.ok, null)
                .setNeutralButton("Copy details", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int which) {
                        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        cm.setPrimaryClip(ClipData.newPlainText("Game error", report));
                        Toast.makeText(LauncherActivity.this, "Copied", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
