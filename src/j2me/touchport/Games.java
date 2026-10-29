package j2me.touchport;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import com.android.dx.command.dexer.DxContext;
import com.android.dx.command.dexer.Main;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * The imported-game library. Each game lives in files/games/&lt;id&gt;/:
 * game.zip (classes.dex + the jar's resources), info.properties, midlet.properties, icon.png, rms/.
 */
final class Games {
    static final String GAME_ZIP = "game.zip";
    private static final String INFO = "info.properties";
    private static final String MIDLET_PROPS = "midlet.properties";
    private static final String ICON = "icon.png";
    private static final String LAST_ERROR = "last_error.txt";

    static final class Game {
        final File dir;
        final Properties info;

        Game(File dir, Properties info) {
            this.dir = dir;
            this.info = info;
        }

        String id() {
            return dir.getName();
        }

        String name() {
            return info.getProperty("name", "Unknown game");
        }

        String vendor() {
            return info.getProperty("vendor", "");
        }

        String version() {
            return info.getProperty("version", "");
        }

        String mainClass() {
            return info.getProperty("main");
        }

        int width() {
            return Integer.parseInt(info.getProperty("width", "240"));
        }

        int height() {
            return Integer.parseInt(info.getProperty("height", "320"));
        }

        File icon() {
            File f = new File(dir, ICON);
            return f.exists() ? f : null;
        }

        void setScreenSize(int w, int h) throws IOException {
            info.setProperty("width", String.valueOf(w));
            info.setProperty("height", String.valueOf(h));
            saveProps(info, new File(dir, INFO));
        }

        Properties midletProperties() {
            return loadProps(new File(dir, MIDLET_PROPS));
        }
    }

    private Games() {
    }

    private static File root(Context c) {
        File d = new File(c.getFilesDir(), "games");
        d.mkdirs();
        return d;
    }

    static List<Game> list(Context c) {
        List<Game> games = new ArrayList<Game>();
        File[] dirs = root(c).listFiles();
        if (dirs != null) {
            for (File d : dirs) {
                Game g = get(d);
                if (g != null) {
                    games.add(g);
                }
            }
        }
        java.util.Collections.sort(games, new Comparator<Game>() {
            public int compare(Game a, Game b) {
                return a.name().compareToIgnoreCase(b.name());
            }
        });
        return games;
    }

    static Game get(Context c, String id) {
        return get(new File(root(c), id));
    }

    private static Game get(File dir) {
        File info = new File(dir, INFO);
        if (!info.exists() || !new File(dir, GAME_ZIP).exists()) {
            return null;
        }
        return new Game(dir, loadProps(info));
    }

    static void delete(Game g) {
        deleteTree(g.dir);
    }

    // ---- import ----

    /** Converts a J2ME jar into a playable game. Runs on a background thread; returns the new or existing game. */
    static Game importJar(Context c, Uri uri) throws Exception {
        byte[] jar = readAll(c.getContentResolver().openInputStream(uri));
        String id = sha1(jar).substring(0, 16);
        Game existing = get(c, id);
        if (existing != null) {
            return existing;
        }

        // Read the manifest and sort the entries into classes and resources.
        Properties manifest = null;
        boolean hasClasses = false;
        ZipInputStream zin = new ZipInputStream(new java.io.ByteArrayInputStream(jar));
        for (ZipEntry e; (e = zin.getNextEntry()) != null; ) {
            if (e.getName().equalsIgnoreCase("META-INF/MANIFEST.MF")) {
                manifest = parseManifest(readAllNoClose(zin));
            } else if (e.getName().endsWith(".class")) {
                hasClasses = true;
            }
        }
        zin.close();
        if (manifest == null || !hasClasses) {
            throw new IOException("This doesn't look like a J2ME game (.jar with a MIDlet manifest).");
        }
        String midlet1 = manifest.getProperty("MIDlet-1");
        if (midlet1 == null) {
            throw new IOException("The jar has no MIDlet-1 entry, so it isn't a J2ME game.");
        }
        String[] parts = midlet1.split(",");
        if (parts.length < 3 || parts[2].trim().length() == 0) {
            throw new IOException("Can't find the game's main class in MIDlet-1: " + midlet1);
        }
        String mainClass = parts[2].trim();
        String iconPath = manifest.getProperty("MIDlet-Icon", parts[1]).trim();
        if (iconPath.startsWith("/")) {
            iconPath = iconPath.substring(1);
        }

        File tmp = new File(c.getCacheDir(), "import-" + id);
        deleteTree(tmp);
        tmp.mkdirs();
        try {
            File jarFile = new File(tmp, "game.jar");
            write(jarFile, jar);

            // dx turns the Java bytecode into an Android dex file, on the phone.
            File dexFile = new File(tmp, "classes.dex");
            ByteArrayOutputStream log = new ByteArrayOutputStream();
            PrintStream ps = new PrintStream(log);
            DxContext ctx = new DxContext(ps, ps);
            Main.Arguments args = new Main.Arguments(ctx);
            // --core-library: some games bundle classes in javax.*; ours take precedence anyway.
            args.parseFlags(new String[]{"--core-library", "--output=" + dexFile.getPath()});
            args.fileNames = new String[]{jarFile.getPath()};
            Main.clearInternTables();
            int rc = new Main(ctx).runDx(args);
            ps.flush();
            if (rc != 0 || !dexFile.exists()) {
                throw new IOException("Converting the game's code failed:\n" + log.toString());
            }

            // game.zip = classes.dex + every non-class file, so getResourceAsStream finds them.
            File staging = new File(tmp, "out");
            staging.mkdirs();
            File zip = new File(staging, GAME_ZIP);
            byte[] icon = null;
            ZipOutputStream zout = new ZipOutputStream(new FileOutputStream(zip));
            zout.putNextEntry(new ZipEntry("classes.dex"));
            copy(new FileInputStream(dexFile), zout);
            zout.closeEntry();
            zin = new ZipInputStream(new java.io.ByteArrayInputStream(jar));
            for (ZipEntry e; (e = zin.getNextEntry()) != null; ) {
                String n = e.getName();
                if (e.isDirectory() || n.endsWith(".class") || n.equals("classes.dex")) {
                    continue;
                }
                byte[] data = readAllNoClose(zin);
                if (n.equals(iconPath)) {
                    icon = data;
                }
                zout.putNextEntry(new ZipEntry(n));
                zout.write(data);
                zout.closeEntry();
            }
            zin.close();
            zout.close();

            int[] size = guessScreenSize(manifest, displayName(c, uri));
            Properties info = new Properties();
            info.setProperty("name", manifest.getProperty("MIDlet-Name", parts[0].trim()));
            info.setProperty("vendor", manifest.getProperty("MIDlet-Vendor", ""));
            info.setProperty("version", manifest.getProperty("MIDlet-Version", ""));
            info.setProperty("main", mainClass);
            info.setProperty("width", String.valueOf(size[0]));
            info.setProperty("height", String.valueOf(size[1]));
            saveProps(info, new File(staging, INFO));
            saveProps(manifest, new File(staging, MIDLET_PROPS));
            if (icon != null) {
                write(new File(staging, ICON), icon);
            }

            // Android 14+ only loads dex files that are read-only.
            zip.setReadOnly();
            File dest = new File(root(c), id);
            if (!staging.renameTo(dest)) {
                throw new IOException("Couldn't save the game.");
            }
            return get(dest);
        } finally {
            deleteTree(tmp);
        }
    }

    private static int[] guessScreenSize(Properties manifest, String fileName) {
        String s = manifest.getProperty("Nokia-MIDlet-Original-Display-Size");
        if (s != null) {
            String[] wh = s.split(",");
            try {
                return new int[]{Integer.parseInt(wh[0].trim()), Integer.parseInt(wh[1].trim())};
            } catch (Exception ignored) {
            }
        }
        if (fileName != null) {
            Matcher m = Pattern.compile("(\\d{3})[xX](\\d{3})").matcher(fileName);
            if (m.find()) {
                return new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))};
            }
        }
        return new int[]{240, 320};
    }

    private static String displayName(Context c, Uri uri) {
        Cursor cur = null;
        try {
            cur = c.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cur != null && cur.moveToFirst()) {
                return cur.getString(0);
            }
        } catch (Exception ignored) {
        } finally {
            if (cur != null) {
                cur.close();
            }
        }
        return uri.getLastPathSegment();
    }

    /** Parses a jar manifest ("Key: value", continuation lines start with a space). */
    private static Properties parseManifest(byte[] data) {
        Properties p = new Properties();
        String text;
        try {
            text = new String(data, "UTF-8");
        } catch (Exception e) {
            text = new String(data);
        }
        String key = null;
        StringBuilder value = new StringBuilder();
        for (String line : text.split("\r\n|\r|\n")) {
            if (line.startsWith(" ") && key != null) {
                value.append(line.substring(1));
                continue;
            }
            if (key != null) {
                p.setProperty(key, value.toString().trim());
                key = null;
            }
            int colon = line.indexOf(':');
            if (colon > 0) {
                key = line.substring(0, colon).trim();
                value.setLength(0);
                value.append(line.substring(colon + 1));
            }
        }
        if (key != null) {
            p.setProperty(key, value.toString().trim());
        }
        return p;
    }

    // ---- crash reporting between the game and launcher processes ----

    static void writeLastError(Context c, Throwable t) {
        try {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            write(new File(c.getFilesDir(), LAST_ERROR), sw.toString().getBytes("UTF-8"));
        } catch (Exception ignored) {
        }
    }

    /** Returns and clears the last game error, or null. */
    static String takeLastError(Context c) {
        File f = new File(c.getFilesDir(), LAST_ERROR);
        if (!f.exists()) {
            return null;
        }
        try {
            return new String(readAll(new FileInputStream(f)), "UTF-8");
        } catch (Exception e) {
            return null;
        } finally {
            f.delete();
        }
    }

    // ---- small IO helpers ----

    static Properties loadProps(File f) {
        Properties p = new Properties();
        try {
            InputStream in = new FileInputStream(f);
            p.load(in);
            in.close();
        } catch (Exception ignored) {
        }
        return p;
    }

    private static void saveProps(Properties p, File f) throws IOException {
        OutputStream out = new FileOutputStream(f);
        p.store(out, null);
        out.close();
    }

    private static byte[] readAll(InputStream in) throws IOException {
        try {
            return readAllNoClose(in);
        } finally {
            in.close();
        }
    }

    private static byte[] readAllNoClose(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        for (int n; (n = in.read(buf)) > 0; ) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[8192];
        for (int n; (n = in.read(buf)) > 0; ) {
            out.write(buf, 0, n);
        }
        in.close();
    }

    private static void write(File f, byte[] data) throws IOException {
        FileOutputStream out = new FileOutputStream(f);
        out.write(data);
        out.close();
    }

    private static String sha1(byte[] data) throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-1").digest(data);
        StringBuilder sb = new StringBuilder();
        for (byte b : d) {
            sb.append(String.format("%02x", b & 0xFF));
        }
        return sb.toString();
    }

    private static void deleteTree(File f) {
        File[] kids = f.listFiles();
        if (kids != null) {
            for (File k : kids) {
                deleteTree(k);
            }
        }
        f.setWritable(true);
        f.delete();
    }

    static final int[][] SCREEN_SIZES = {
            {128, 128}, {128, 160}, {176, 208}, {176, 220}, {208, 208},
            {240, 320}, {320, 240}, {352, 416}, {360, 640}, {640, 360},
    };

    static String sizeLabel(int w, int h) {
        return w + " × " + h;
    }

    static boolean isKnownSize(int w, int h) {
        for (int[] s : SCREEN_SIZES) {
            if (s[0] == w && s[1] == h) {
                return true;
            }
        }
        return false;
    }

    static int[][] sizesFor(Game g) {
        if (isKnownSize(g.width(), g.height())) {
            return SCREEN_SIZES;
        }
        int[][] out = Arrays.copyOf(SCREEN_SIZES, SCREEN_SIZES.length + 1);
        out[SCREEN_SIZES.length] = new int[]{g.width(), g.height()};
        return out;
    }
}
