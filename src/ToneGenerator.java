import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineUnavailableException;

/**
 * Gera, em memoria, um pequeno loop sintetizado (um mini arpejo de 4 notas)
 * para servir de som quando o usuario adiciona um instrumento sem indicar
 * um arquivo de audio — ex: "add guitarra".
 *
 * Serve como fallback do comando "add": o caminho principal continua sendo
 * carregar um arquivo real (ex: "add stems/other.wav"), mas assim da para
 * criar faixas novas na hora, sem depender de ter mais arquivos na pasta.
 *
 * Nao envolve threads nem estado compartilhado: e so matematica de onda
 * senoidal escrita em um array de bytes que vira um Clip.
 */
public class ToneGenerator {

    /** Taxa de amostragem do audio gerado (padrao de CD). */
    private static final float SAMPLE_RATE = 44100f;

    /** Duracao de cada nota do arpejo, em milissegundos. */
    private static final int NOTE_MS = 150;

    /** Proporcoes de frequencia das 4 notas (aproximam um acorde simples). */
    private static final double[] STEP_RATIOS = {1.0, 1.125, 1.25, 1.5};

    /**
     * Monta um Clip com um arpejo curto. A frequencia base varia conforme o
     * nome informado, entao "guitarra" e "teclado" soam diferentes um do outro.
     */
    public static Clip generateClip(String seed) throws LineUnavailableException {
        double baseFreq = 220.0 + (Math.abs(seed.hashCode()) % 5) * 55.0;
        int samplesPerNote = (int) (SAMPLE_RATE * NOTE_MS / 1000.0);

        // 16 bits por amostra = 2 bytes por amostra, mono.
        byte[] data = new byte[samplesPerNote * STEP_RATIOS.length * 2];

        int idx = 0;
        for (double ratio : STEP_RATIOS) {
            double freq = baseFreq * ratio;
            for (int i = 0; i < samplesPerNote; i++) {
                double angle = 2.0 * Math.PI * i * freq / SAMPLE_RATE;
                // Envelope decrescente: evita "clicks" na emenda entre as notas.
                double envelope = 1.0 - ((double) i / samplesPerNote);
                short sample = (short) (Math.sin(angle) * Short.MAX_VALUE * 0.4 * envelope);
                data[idx++] = (byte) (sample & 0xFF);          // byte menos significativo
                data[idx++] = (byte) ((sample >> 8) & 0xFF);   // byte mais significativo
            }
        }

        AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
        Clip clip = AudioSystem.getClip();
        clip.open(format, data, 0, data.length);
        return clip;
    }
}
