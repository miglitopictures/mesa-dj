/**
 * Desafio extra: thread dedicada apenas a atualizar o painel de status.
 *
 * Esta e a terceira "familia" de threads do programa:
 *   1. as threads das faixas (Track), que tocam audio;
 *   2. a thread principal, que le os comandos do DJ;
 *   3. esta thread, que a cada 2 segundos manda o Console redesenhar o painel.
 *
 * A classe so cuida do AGENDAMENTO (dormir 2s, pedir refresh, repetir). Todo
 * o desenho e o cuidado de nao atrapalhar a digitacao do usuario ficam no
 * Console, que serializa as escritas na tela com seu proprio lock.
 *
 * O encerramento tambem e controlado: stopPanel() apenas levanta a flag
 * "running" (volatile, para que a mudanca seja visivel imediatamente para
 * esta thread) e o loop termina sozinho no proximo ciclo.
 */
public class StatusPanel implements Runnable {

    /** Intervalo de atualizacao do painel, conforme pedido na atividade. */
    private static final long REFRESH_MS = 2000;

    private final Console console;

    /** volatile: escrita pela thread principal, lida por esta thread. */
    private volatile boolean running = true;

    public StatusPanel(Console console) {
        this.console = console;
    }

    /** Pede o encerramento controlado desta thread. */
    public void stopPanel() {
        running = false;
    }

    @Override
    public void run() {
        try {
            while (running) {
                Thread.sleep(REFRESH_MS);
                if (running) { // recheca: pode ter sido encerrado durante o sleep
                    console.refresh();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
