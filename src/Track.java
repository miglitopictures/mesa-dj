import java.io.File;
import java.util.concurrent.CountDownLatch;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;

/**
 * Representa um instrumento / faixa da mesa de DJ.
 *
 * ---------------------------------------------------------------------------
 * THREADS
 * ---------------------------------------------------------------------------
 * Track implementa Runnable e cada instancia roda em sua propria Thread (ver
 * Mixer.addTrack). O metodo run() e um loop infinito que toca o audio da
 * faixa do inicio ao fim e recomeca, de forma totalmente independente das
 * outras faixas — varias faixas tocam ao mesmo tempo porque cada uma tem sua
 * propria thread e sua propria linha de audio (Clip).
 *
 * ---------------------------------------------------------------------------
 * SINCRONIZACAO (o ponto central da atividade)
 * ---------------------------------------------------------------------------
 * Os campos "state" e "stopRequested" sao o estado compartilhado da faixa:
 *   - a thread principal (o DJ digitando comandos) ESCREVE neles ao chamar
 *     pause(), resume() e requestStop();
 *   - a thread da propria faixa LE eles a todo momento dentro do seu loop;
 *   - a thread do painel de status LE o estado para desenhar a tela.
 *
 * Se esse acesso fosse feito sem protecao, teriamos condicao de corrida
 * (leituras desatualizadas, pausas ignoradas, etc). Por isso:
 *   - todos os metodos que leem/escrevem esses campos sao "synchronized",
 *     ou seja, so uma thread por vez entra neles (lock do proprio objeto Track);
 *   - quando a faixa e pausada, sua thread chama wait() e fica dormindo sem
 *     gastar CPU, liberando o lock;
 *   - quando o DJ manda retomar ou encerrar, chamamos notifyAll() para
 *     acordar a thread da faixa imediatamente.
 *
 * ---------------------------------------------------------------------------
 * ENCERRAMENTO SEGURO
 * ---------------------------------------------------------------------------
 * Nunca usamos Thread.stop() nem matamos a thread na forca. requestStop()
 * apenas LEVANTA UMA FLAG; a propria thread da faixa percebe essa flag em um
 * ponto seguro do seu loop, sai do loop e, no bloco finally, para e fecha o
 * Clip liberando o recurso de audio do sistema.
 *
 * ---------------------------------------------------------------------------
 * INICIO SINCRONIZADO
 * ---------------------------------------------------------------------------
 * Antes de tocar qualquer nota, a thread espera em um "portao de largada"
 * (CountDownLatch startGate, compartilhado pelo Mixer). Assim as faixas
 * carregadas no inicio ficam todas prontas e so entao comecam juntas, no
 * mesmo instante, em vez de comecarem escalonadas conforme cada thread e
 * criada.
 */
public class Track implements Runnable {

    /**
     * Granularidade das esperas, em ms.
     *
     * Em vez de dormir de uma vez o tempo inteiro da musica (o que faria a
     * faixa demorar ate o fim do loop para "perceber" um pause), dormimos em
     * pequenos passos de 50ms e, entre um passo e outro, verificamos se o DJ
     * pediu pausa ou encerramento. E o que deixa os comandos responsivos.
     */
    private static final long POLL_STEP_MS = 50;

    /** Nome da faixa, usado nos comandos e no painel (ex: "bateria"). */
    private final String name;

    /** Linha de audio ja carregada em memoria com o som desta faixa. */
    private final Clip clip;

    /** Portao de largada compartilhado: segura a faixa ate o sinal de inicio. */
    private final CountDownLatch startGate;

    /** Origem do som, exibida no painel (caminho do arquivo ou "sintetizado"). */
    private final String source;

    // ---- Estado compartilhado: SEMPRE acessado sob synchronized ----

    /** Estado atual da faixa (TOCANDO / PAUSADO / PARADO). */
    private TrackState state = TrackState.PLAYING;

    /** Flag de encerramento: quando true, a thread sai do loop com seguranca. */
    private boolean stopRequested = false;

    public Track(String name, Clip clip, CountDownLatch startGate, String source) {
        this.name = name;
        this.clip = clip;
        this.startGate = startGate;
        this.source = source;
    }

    /**
     * Cria uma Track a partir de um arquivo de audio em disco (os stems).
     *
     * O Clip e aberto (carregado em memoria) aqui, ANTES de a thread comecar,
     * justamente para que o carregamento — que e lento e demora diferente
     * para cada arquivo — nao atrapalhe o inicio sincronizado das faixas.
     */
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

    /** Leitura do estado sob lock, para o painel e para os comandos. */
    public synchronized TrackState getState() {
        return state;
    }

    /**
     * Sinaliza a faixa para pausar.
     * Nao interrompe nem mata a thread: apenas muda o estado compartilhado,
     * que a thread da faixa vai perceber no proximo ponto de verificacao.
     */
    public synchronized void pause() {
        if (state == TrackState.PLAYING) {
            state = TrackState.PAUSED;
        }
    }

    /**
     * Sinaliza a faixa para retomar a reproducao de onde parou.
     * O notifyAll() acorda a thread da faixa, que esta dormindo em wait().
     */
    public synchronized void resume() {
        if (state == TrackState.PAUSED) {
            state = TrackState.PLAYING;
            notifyAll();
        }
    }

    /**
     * Pede o encerramento seguro e controlado desta faixa.
     * Levanta a flag e acorda a thread caso ela esteja pausada, para que ela
     * consiga sair do loop e liberar o Clip no finally do run().
     */
    public synchronized void requestStop() {
        state = TrackState.STOPPED;
        stopRequested = true;
        notifyAll();
    }

    /**
     * Corpo da thread da faixa: espera a largada e entao toca em loop
     * continuo ate que o encerramento seja solicitado.
     */
    @Override
    public void run() {
        try {
            // 1) Espera o sinal de largada, para comecar junto com as demais faixas.
            startGate.await();

            // 2) Loop principal: toca o audio inteiro, recomeca, e assim por diante.
            while (true) {
                synchronized (this) {
                    // Se a faixa foi pausada antes mesmo de comecar a repeticao,
                    // dorme aqui ate alguem chamar resume() ou requestStop().
                    while (state == TrackState.PAUSED) {
                        wait();
                    }
                    if (stopRequested) {
                        break; // saida controlada do loop
                    }
                }

                clip.setFramePosition(0); // volta pro inicio do audio
                clip.start();

                // Espera a duracao do audio, mas em passos curtos, para
                // continuar reagindo a pause/remove durante a reproducao.
                long durationMs = clip.getMicrosecondLength() / 1000;
                if (!sleepResponsive(durationMs)) {
                    break;
                }

                clip.stop();
            }
        } catch (InterruptedException e) {
            // Boa pratica: restaura a flag de interrupcao e encerra a thread.
            Thread.currentThread().interrupt();
        } finally {
            // Encerramento seguro: sempre libera o recurso de audio.
            clip.stop();
            clip.close();
        }
    }

    /**
     * Espera "totalMs" milissegundos, mas fatiado em passos de POLL_STEP_MS,
     * verificando entre os passos se a faixa foi pausada ou encerrada.
     *
     * Se estiver pausada, para o audio de fato e dorme em wait() ate ser
     * retomada — a musica volta de onde parou, sem reiniciar.
     *
     * @return false se a faixa deve encerrar sua execucao; true se pode seguir.
     */
    private boolean sleepResponsive(long totalMs) throws InterruptedException {
        long elapsed = 0;
        while (elapsed < totalMs) {
            synchronized (this) {
                if (stopRequested) {
                    return false;
                }
                if (state == TrackState.PAUSED) {
                    clip.stop(); // silencia, mantendo a posicao atual do audio
                    while (state == TrackState.PAUSED) {
                        wait();
                        if (stopRequested) {
                            return false;
                        }
                    }
                    clip.start(); // retoma exatamente de onde parou
                }
            }
            long step = Math.min(POLL_STEP_MS, totalMs - elapsed);
            Thread.sleep(step);
            elapsed += step;
        }
        return true;
    }
}
