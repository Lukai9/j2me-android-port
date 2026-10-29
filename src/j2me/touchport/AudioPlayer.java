package j2me.touchport;

import android.media.MediaPlayer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.microedition.media.Control;
import javax.microedition.media.MediaException;
import javax.microedition.media.Player;
import javax.microedition.media.PlayerListener;
import javax.microedition.media.control.VolumeControl;

/** MMAPI Player backed by android.media.MediaPlayer (which plays MIDI via Sonivox). */
public final class AudioPlayer implements Player, VolumeControl {
    private static final List<AudioPlayer> all = new ArrayList<AudioPlayer>();

    private final byte[] data;
    private final String type;
    private final List<PlayerListener> listeners = new ArrayList<PlayerListener>();
    private MediaPlayer mp;
    private int state = UNREALIZED;
    private int loopCount = 1;
    private int loopsLeft;
    private int level = 100;
    private boolean muted;
    private boolean pausedByHost;

    public AudioPlayer(byte[] data, String type) {
        this.data = data;
        this.type = type;
        synchronized (all) {
            all.add(this);
        }
    }

    private File cacheFile() throws Exception {
        String ext = type != null && type.indexOf("wav") >= 0 ? ".wav"
                : type != null && type.indexOf("mpeg") >= 0 ? ".mp3"
                : type != null && type.indexOf("amr") >= 0 ? ".amr" : ".mid";
        File f = new File(Host.context.getCacheDir(), "snd_" + Integer.toHexString(Arrays.hashCode(data)) + "_" + data.length + ext);
        if (!f.exists() || f.length() != data.length) {
            FileOutputStream out = new FileOutputStream(f);
            out.write(data);
            out.close();
        }
        return f;
    }

    public synchronized void realize() throws MediaException {
        if (state == CLOSED) {
            throw new IllegalStateException();
        }
        if (state >= REALIZED) {
            return;
        }
        try {
            mp = new MediaPlayer();
            FileInputStream in = new FileInputStream(cacheFile());
            mp.setDataSource(in.getFD());
            in.close();
            mp.prepare();
            mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                public void onCompletion(MediaPlayer m) {
                    onEnd();
                }
            });
            applyVolume();
        } catch (Exception e) {
            if (mp != null) {
                mp.release();
                mp = null;
            }
            throw new MediaException(String.valueOf(e));
        }
        state = REALIZED;
    }

    private void onEnd() {
        synchronized (this) {
            if (mp == null) {
                return;
            }
            if (loopCount > 1 && --loopsLeft > 0) {
                mp.seekTo(0);
                mp.start();
                return;
            }
            if (state == STARTED) {
                state = PREFETCHED;
            }
            mp.seekTo(0);
        }
        fire(PlayerListener.END_OF_MEDIA, Long.valueOf(getDuration()));
    }

    public synchronized void prefetch() throws MediaException {
        realize();
        if (state < PREFETCHED) {
            state = PREFETCHED;
        }
    }

    public void start() throws MediaException {
        synchronized (this) {
            prefetch();
            if (state == STARTED) {
                return;
            }
            mp.setLooping(loopCount == -1);
            loopsLeft = loopCount;
            pausedByHost = false;
            if (Host.isPaused()) {
                // Started while the app is in the background: play once we come back.
                pausedByHost = true;
            } else {
                mp.start();
            }
            state = STARTED;
        }
        fire(PlayerListener.STARTED, Long.valueOf(getMediaTime()));
    }

    public void stop() {
        synchronized (this) {
            if (state != STARTED) {
                return;
            }
            pausedByHost = false;
            if (mp.isPlaying()) {
                mp.pause();
            }
            state = PREFETCHED;
        }
        fire(PlayerListener.STOPPED, Long.valueOf(getMediaTime()));
    }

    public synchronized void deallocate() {
        if (state == STARTED) {
            stop();
        }
        if (state == PREFETCHED) {
            state = REALIZED;
        }
    }

    public void close() {
        synchronized (this) {
            if (state == CLOSED) {
                return;
            }
            if (mp != null) {
                mp.release();
                mp = null;
            }
            state = CLOSED;
        }
        synchronized (all) {
            all.remove(this);
        }
        fire(PlayerListener.CLOSED, null);
    }

    public synchronized long setMediaTime(long now) throws MediaException {
        if (mp == null) {
            throw new IllegalStateException();
        }
        int ms = (int) Math.max(0, now / 1000);
        mp.seekTo(ms);
        return ms * 1000L;
    }

    public synchronized long getMediaTime() {
        if (mp == null) {
            return TIME_UNKNOWN;
        }
        return mp.getCurrentPosition() * 1000L;
    }

    public synchronized long getDuration() {
        if (mp == null) {
            return TIME_UNKNOWN;
        }
        int d = mp.getDuration();
        return d < 0 ? TIME_UNKNOWN : d * 1000L;
    }

    public synchronized int getState() {
        return state;
    }

    public String getContentType() {
        return type;
    }

    public synchronized void setLoopCount(int count) {
        loopCount = count == 0 ? 1 : count;
    }

    public synchronized void addPlayerListener(PlayerListener l) {
        if (l != null && !listeners.contains(l)) {
            listeners.add(l);
        }
    }

    public synchronized void removePlayerListener(PlayerListener l) {
        listeners.remove(l);
    }

    private void fire(String event, Object data) {
        PlayerListener[] ls;
        synchronized (this) {
            ls = listeners.toArray(new PlayerListener[0]);
        }
        for (PlayerListener l : ls) {
            try {
                l.playerUpdate(this, event, data);
            } catch (Throwable ignored) {
            }
        }
    }

    // ---- Controllable / VolumeControl ----

    public Control[] getControls() {
        return new Control[]{this};
    }

    public Control getControl(String controlType) {
        if (controlType != null && controlType.endsWith("VolumeControl")) {
            return this;
        }
        return null;
    }

    private void applyVolume() {
        if (mp != null) {
            float v = muted ? 0f : level / 100f;
            mp.setVolume(v, v);
        }
    }

    public synchronized void setMute(boolean m) {
        muted = m;
        applyVolume();
    }

    public synchronized boolean isMuted() {
        return muted;
    }

    public synchronized int setLevel(int l) {
        level = Math.max(0, Math.min(100, l));
        applyVolume();
        return level;
    }

    public synchronized int getLevel() {
        return level;
    }

    // ---- app lifecycle ----

    static void pauseAll() {
        for (AudioPlayer p : snapshot()) {
            synchronized (p) {
                if (p.state == STARTED && p.mp != null && p.mp.isPlaying()) {
                    p.mp.pause();
                    p.pausedByHost = true;
                }
            }
        }
    }

    static void resumeAll() {
        for (AudioPlayer p : snapshot()) {
            synchronized (p) {
                if (p.pausedByHost && p.state == STARTED && p.mp != null) {
                    p.mp.start();
                }
                p.pausedByHost = false;
            }
        }
    }

    private static AudioPlayer[] snapshot() {
        synchronized (all) {
            return all.toArray(new AudioPlayer[0]);
        }
    }
}
