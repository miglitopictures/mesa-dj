import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * A "mesa de DJ": guarda todas as faixas cadastradas e cuida das threads delas.
 *
 * ---------------------------------------------------------------------------
 * SINCRONIZACAO
 * ---------------------------------------------------------------------------
 * O mapa de faixas tambem e estado compartilhado entre threads:
 *   - a thread principal adiciona faixas (add), remove faixas (remove) e
 *     busca faixas para pausar/retomar;
 *   - a thread do painel percorre a lista de faixas a cada 2 segundos para
 *     desenhar a tela.
 *
 * Percorrer um LinkedHashMap enquanto outra thread o modifica causaria
 * ConcurrentModificationException / leituras inconsistentes. Por isso, todo
 * acesso ao mapa acontece dentro de um bloco synchronized no mesmo objeto
 * "lock", e allTracks() devolve uma COPIA da lista, para que quem for
 * percorrer nao dependa mais do lock.
 *
 * ---------------------------------------------------------------------------
 * PORTAO DE LARGADA (inicio sincronizado)
 * ---------------------------------------------------------------------------
 * O Mixer guarda um CountDownLatch compartilhado por todas as faixas. Cada
 * faixa carrega seu audio e entao bloqueia nesse portao. Quando o Main termina
 * de carregar as faixas iniciais, chama releaseAll() uma unica vez e todas
 * comecam a tocar praticamente no mesmo instante.
 *
 * Faixas adicionadas depois (comando "add") recebem o mesmo portao, que a
 * essa altura ja esta aberto — entao elas comecam a tocar imediatamente.
 */
public class Mixer {

    /** Faixas por nome (em minusculo). LinkedHashMap mantem a ordem de insercao. */
    private final Map<String, Track> tracks = new LinkedHashMap<>();

    /** Lock unico que protege o mapa acima contra acesso concorrente. */
    private final Object lock = new Object();

    /** Portao de largada: 1 contagem, liberada uma unica vez por releaseAll(). */
    private final CountDownLatch startGate = new CountDownLatch(1);

    public CountDownLatch getStartGate() {
        return startGate;
    }

    /** Abre o portao: todas as faixas que estavam esperando comecam juntas. */
    public void releaseAll() {
        startGate.countDown();
    }

    /**
     * Cadastra a faixa na mesa e inicia sua thread dedicada.
     *
     * A thread e marcada como daemon para que o programa nao fique preso
     * caso encerre por um caminho inesperado; o encerramento normal continua
     * sendo feito de forma controlada via requestStop().
     */
    public void addTrack(Track track) {
        synchronized (lock) {
            tracks.put(key(track.getName()), track);
        }
        Thread thread = new Thread(track, "faixa-" + track.getName());
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Retira uma faixa da mesa: pede o encerramento seguro da thread dela
     * (que vai liberar o recurso de audio) e tira do mapa para que ela suma
     * do painel.
     *
     * @return a faixa removida, ou null se nao existia nenhuma com esse nome.
     */
    public Track removeTrack(String name) {
        Track removed;
        synchronized (lock) {
            removed = tracks.remove(key(name));
        }
        if (removed != null) {
            removed.requestStop(); // encerramento controlado, sem matar a thread
        }
        return removed;
    }

    /** Busca uma faixa pelo nome (sem diferenciar maiusculas/minusculas). */
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

    /** Copia defensiva da lista de faixas, segura para o painel percorrer. */
    public List<Track> allTracks() {
        synchronized (lock) {
            return new ArrayList<>(tracks.values());
        }
    }

    /** Sinaliza para todas as faixas encerrarem de forma segura (saida do app). */
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
