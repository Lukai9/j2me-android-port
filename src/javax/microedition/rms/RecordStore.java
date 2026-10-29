package javax.microedition.rms;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import j2me.touchport.Host;

/** RMS record stores persisted as one file per store in the app's private storage. */
public class RecordStore {
    private static final Map<String, RecordStore> open = new HashMap<String, RecordStore>();

    private final String name;
    private final File file;
    private final TreeMap<Integer, byte[]> records = new TreeMap<Integer, byte[]>();
    private int nextId = 1;
    private int version;
    private long lastModified;
    private int openCount;

    private RecordStore(String name, File file) {
        this.name = name;
        this.file = file;
    }

    private static File dir() {
        File d = new File(Host.gameDir, "rms");
        d.mkdirs();
        return d;
    }

    private static File fileFor(String name) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            sb.append(Character.isLetterOrDigit(c) ? String.valueOf(c) : "_" + (int) c + "_");
        }
        return new File(dir(), sb + ".rms");
    }

    public static RecordStore openRecordStore(String name, boolean create) throws RecordStoreException {
        synchronized (open) {
            RecordStore rs = open.get(name);
            if (rs == null) {
                File f = fileFor(name);
                if (!f.exists() && !create) {
                    throw new RecordStoreNotFoundException(name);
                }
                rs = new RecordStore(name, f);
                if (f.exists()) {
                    rs.load();
                } else {
                    rs.save();
                }
                open.put(name, rs);
            }
            rs.openCount++;
            return rs;
        }
    }

    public static RecordStore openRecordStore(String name, boolean create, int authmode, boolean writable)
            throws RecordStoreException {
        return openRecordStore(name, create);
    }

    public static void deleteRecordStore(String name) throws RecordStoreException {
        synchronized (open) {
            open.remove(name);
            File f = fileFor(name);
            if (!f.delete()) {
                throw new RecordStoreNotFoundException(name);
            }
        }
    }

    public static String[] listRecordStores() {
        File[] files = dir().listFiles();
        if (files == null || files.length == 0) {
            return null;
        }
        String[] names = new String[files.length];
        for (int i = 0; i < files.length; i++) {
            try {
                DataInputStream in = new DataInputStream(new FileInputStream(files[i]));
                names[i] = in.readUTF();
                in.close();
            } catch (Exception e) {
                names[i] = files[i].getName();
            }
        }
        return names;
    }

    private void load() throws RecordStoreException {
        try {
            DataInputStream in = new DataInputStream(new FileInputStream(file));
            in.readUTF();
            nextId = in.readInt();
            version = in.readInt();
            lastModified = in.readLong();
            int n = in.readInt();
            for (int i = 0; i < n; i++) {
                int id = in.readInt();
                byte[] data = new byte[in.readInt()];
                in.readFully(data);
                records.put(id, data);
            }
            in.close();
        } catch (Exception e) {
            throw new RecordStoreException(String.valueOf(e));
        }
    }

    private void save() throws RecordStoreException {
        try {
            File tmp = new File(file.getPath() + ".tmp");
            DataOutputStream out = new DataOutputStream(new FileOutputStream(tmp));
            out.writeUTF(name);
            out.writeInt(nextId);
            out.writeInt(version);
            out.writeLong(lastModified);
            out.writeInt(records.size());
            for (Map.Entry<Integer, byte[]> e : records.entrySet()) {
                out.writeInt(e.getKey());
                out.writeInt(e.getValue().length);
                out.write(e.getValue());
            }
            out.close();
            if (!tmp.renameTo(file)) {
                file.delete();
                tmp.renameTo(file);
            }
        } catch (Exception e) {
            throw new RecordStoreException(String.valueOf(e));
        }
    }

    private void changed() throws RecordStoreException {
        version++;
        lastModified = System.currentTimeMillis();
        save();
    }

    public void closeRecordStore() throws RecordStoreException {
        synchronized (open) {
            if (--openCount <= 0) {
                open.remove(name);
            }
        }
    }

    public String getName() {
        return name;
    }

    public synchronized int getVersion() {
        return version;
    }

    public synchronized long getLastModified() {
        return lastModified;
    }

    public synchronized int getNumRecords() {
        return records.size();
    }

    public synchronized int getSize() {
        int s = 0;
        for (byte[] r : records.values()) {
            s += r.length;
        }
        return s;
    }

    public int getSizeAvailable() {
        return 1 << 20;
    }

    public synchronized int getNextRecordID() {
        return nextId;
    }

    public synchronized int addRecord(byte[] data, int offset, int len) throws RecordStoreException {
        byte[] r = new byte[len];
        if (data != null) {
            System.arraycopy(data, offset, r, 0, len);
        }
        int id = nextId++;
        records.put(id, r);
        changed();
        return id;
    }

    public synchronized void deleteRecord(int id) throws RecordStoreException {
        if (records.remove(id) == null) {
            throw new InvalidRecordIDException(String.valueOf(id));
        }
        changed();
    }

    private byte[] get(int id) throws InvalidRecordIDException {
        byte[] r = records.get(id);
        if (r == null) {
            throw new InvalidRecordIDException(String.valueOf(id));
        }
        return r;
    }

    public synchronized int getRecordSize(int id) throws RecordStoreException {
        return get(id).length;
    }

    public synchronized int getRecord(int id, byte[] buf, int offset) throws RecordStoreException {
        byte[] r = get(id);
        System.arraycopy(r, 0, buf, offset, r.length);
        return r.length;
    }

    public synchronized byte[] getRecord(int id) throws RecordStoreException {
        byte[] r = get(id);
        return r.length == 0 ? null : r.clone();
    }

    public synchronized void setRecord(int id, byte[] data, int offset, int len) throws RecordStoreException {
        get(id);
        byte[] r = new byte[len];
        if (data != null) {
            System.arraycopy(data, offset, r, 0, len);
        }
        records.put(id, r);
        changed();
    }
}
