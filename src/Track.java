import java.io.File;
import java.util.concurrent.CountDownLatch;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;

public class Track implements Runnable {

    private static final long POLL_STEP_MS = 50;

    private final String name;

    private final Clip clip;

    private final CountDownLatch startGate;

    private final String source;

    private TrackState state = TrackState.PLAYING;

    private boolean stopRequested = false;

    public Track(String name, Clip clip, CountDownLatch startGate, String source) {
        this.name = name;
        this.clip = clip;
        this.startGate = startGate;
        this.source = source;
    }

    public static Track fromFile(String name, String filePath, CountDownLatch startGate) throws Exception {
        File file = new File(filePath);
        AudioInputStream audioIn = AudioSystem.getAudioInputStream(file);
        Clip clip = AudioSystem.getClip();
        clip.open(audioIn);
        audioIn.close();
        return new Track(name, clip, startGate, filePath);
    }

    public String getName() {
        return name;
    }

    public String getSource() {
        return source;
    }

    public synchronized TrackState getState() {
        return state;
    }

    public synchronized void pause() {
        if (state == TrackState.PLAYING) {
            state = TrackState.PAUSED;
        }
    }

    public synchronized void resume() {
        if (state == TrackState.PAUSED) {
            state = TrackState.PLAYING;
            notifyAll();
        }
    }

    public synchronized void requestStop() {
        state = TrackState.STOPPED;
        stopRequested = true;
        notifyAll();
    }

    @Override
    public void run() {
        try {
            startGate.await();

            while (true) {
                synchronized (this) {
                    while (state == TrackState.PAUSED) {
                        wait();
                    }
                    if (stopRequested) {
                        break;
                    }
                }

                clip.setFramePosition(0);
                clip.start();

                long durationMs = clip.getMicrosecondLength() / 1000;
                if (!sleepResponsive(durationMs)) {
                    break;
                }

                clip.stop();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            clip.stop();
            clip.close();
        }
    }

    private boolean sleepResponsive(long totalMs) throws InterruptedException {
        long elapsed = 0;
        while (elapsed < totalMs) {
            synchronized (this) {
                if (stopRequested) {
                    return false;
                }
                if (state == TrackState.PAUSED) {
                    clip.stop();
                    while (state == TrackState.PAUSED) {
                        wait();
                        if (stopRequested) {
                            return false;
                        }
                    }
                    clip.start();
                }
            }
            long step = Math.min(POLL_STEP_MS, totalMs - elapsed);
            Thread.sleep(step);
            elapsed += step;
        }
        return true;
    }
}