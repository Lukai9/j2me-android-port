package javax.microedition.media;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import j2me.touchport.AudioPlayer;

public final class Manager {
    public static final String TONE_DEVICE_LOCATOR = "device://tone";
    public static final String MIDI_DEVICE_LOCATOR = "device://midi";

    private Manager() {
    }

    public static Player createPlayer(InputStream in, String type) throws IOException, MediaException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        return new AudioPlayer(out.toByteArray(), type);
    }

    public static Player createPlayer(String locator) throws IOException, MediaException {
        throw new MediaException("Locator not supported: " + locator);
    }

    public static String[] getSupportedContentTypes(String protocol) {
        return new String[]{"audio/midi", "audio/x-wav", "audio/mpeg", "audio/amr"};
    }

    public static String[] getSupportedProtocols(String contentType) {
        return new String[0];
    }

    public static void playTone(int note, int duration, int volume) {
    }
}
