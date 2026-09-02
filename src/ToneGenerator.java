import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineUnavailableException;

public class ToneGenerator {

    private static final float SAMPLE_RATE = 44100f;

    private static final int NOTE_MS = 150;

    private static final double[] STEP_RATIOS = {1.0, 1.125, 1.25, 1.5};

    public static Clip generateClip(String seed) throws LineUnavailableException {
        double baseFreq = 220.0 + (Math.abs(seed.hashCode()) % 5) * 55.0;
        int samplesPerNote = (int) (SAMPLE_RATE * NOTE_MS / 1000.0);

        byte[] data = new byte[samplesPerNote * STEP_RATIOS.length * 2];

        int idx = 0;
        for (double ratio : STEP_RATIOS) {
            double freq = baseFreq * ratio;
            for (int i = 0; i < samplesPerNote; i++) {
                double angle = 2.0 * Math.PI * i * freq / SAMPLE_RATE;
                double envelope = 1.0 - ((double) i / samplesPerNote);
                short sample = (short) (Math.sin(angle) * Short.MAX_VALUE * 0.4 * envelope);
                data[idx++] = (byte) (sample & 0xFF);
                data[idx++] = (byte) ((sample >> 8) & 0xFF);
            }
        }

        AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
        Clip clip = AudioSystem.getClip();
        clip.open(format, data, 0, data.length);
        return clip;
    }
}
