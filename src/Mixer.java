import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

public class Mixer {

    private final Map<String, Track> tracks = new LinkedHashMap<>();

    private final Object lock = new Object();

    private final CountDownLatch startGate = new CountDownLatch(1);

    public CountDownLatch getStartGate() {
        return startGate;
    }

    public void releaseAll() {
        startGate.countDown();
    }

    public void addTrack(Track track) {
        synchronized (lock) {
            tracks.put(key(track.getName()), track);
        }
        Thread thread = new Thread(track, "faixa-" + track.getName());
        thread.setDaemon(true);
        thread.start();
    }

    public Track removeTrack(String name) {
        Track removed;
        synchronized (lock) {
            removed = tracks.remove(key(name));
        }
        if (removed != null) {
            removed.requestStop();
        }
        return removed;
    }

    public Track get(String name) {
        synchronized (lock) {
            return tracks.get(key(name));
        }
    }

    public boolean exists(String name) {
        synchronized (lock) {
            return tracks.containsKey(key(name));
        }
    }

    public List<Track> allTracks() {
        synchronized (lock) {
            return new ArrayList<>(tracks.values());
        }
    }

    public void stopAll() {
        synchronized (lock) {
            for (Track t : tracks.values()) {
                t.requestStop();
            }
            tracks.clear();
        }
    }

    private String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
