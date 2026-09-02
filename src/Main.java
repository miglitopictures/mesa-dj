import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.Locale;
import javax.sound.sampled.Clip;

/**
 * MESA DE DJ COM THREADS — Infraestrutura de Software (SO)
 *
 * Simulador de mesa de DJ em terminal: cada faixa/instrumento toca em sua
 * propria thread, simultaneamente, e o DJ controla cada uma por comandos de
 * texto sem afetar as demais.
 *
 * ---------------------------------------------------------------------------
 * EQUIPE
 * ---------------------------------------------------------------------------
 *   Lucas Bonfim Gomes
 *   Lucas Moreira de Carvalho
 *   Lucas Guilherme Pinheiro Valenca Barbosa
 *   Raysa Costa Queiroz
 *   Rodrigo Morais Silvestri de Castro Montenegro
 *   Pablo Tamborini Nogueira
 *   Miguel Duarte de Barros
 *   Gabriel Cavalcante Barros de Oliveira
 *
 * Professor | Infraestrutura de Software - SO:
 *   Raoni Monteiro de Oliveira
 *
 * ---------------------------------------------------------------------------
 * O QUE ESTA CLASSE FAZ
 * ---------------------------------------------------------------------------
 * Main e a thread principal do programa. Ela:
 *   1. carrega as faixas de exemplo da pasta stems/ (cada uma ja com sua thread,
 *      mas todas seguradas no "portao de largada" do Mixer);
 *   2. abre o portao, fazendo todas comecarem juntas no mesmo instante;
 *   3. sobe a thread do painel de status (atualiza a tela a cada 2s);
 *   4. entra no loop de leitura de comandos do DJ.
 *
 * Repare que Main NUNCA mexe diretamente no estado de uma faixa: ela apenas
 * chama metodos synchronized da Track (pause/resume/requestStop), que sao o
 * ponto onde a concorrencia e controlada.
 */
public class Main {

    public static void main(String[] args) throws Exception {
        Mixer mixer = new Mixer();

        // ---- 1) Carrega as faixas de exemplo -------------------------------
        // O carregamento do audio (lento e de duracao variavel) acontece aqui,
        // antes de qualquer som tocar, para nao baguncar o inicio sincronizado.
        System.out.println("Carregando faixas de stems/ ...");
        loadStem(mixer, "bateria", "stems/drums.wav");
        loadStem(mixer, "baixo", "stems/bass.wav");
        loadStem(mixer, "synth", "stems/other.wav");

        // ---- 2) Largada sincronizada ---------------------------------------
        // Todas as threads das faixas estao bloqueadas esperando este sinal;
        // ao abrir o portao, elas chamam clip.start() praticamente juntas.
        mixer.releaseAll();

        // ---- 3) Interface: painel fixo + thread que o atualiza -------------
        Console console = new Console(mixer);
        console.init();

        StatusPanel panel = new StatusPanel(console);
        Thread panelThread = new Thread(panel, "painel-status");
        panelThread.setDaemon(true);
        panelThread.start();

        // ---- 4) Loop de comandos do DJ -------------------------------------
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        String line;
        while ((line = in.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) {
                console.promptAgain();
                continue;
            }

            String[] parts = line.split("\\s+");
            String cmd = parts[0].toLowerCase(Locale.ROOT);

            // Saida: encerra TODAS as faixas de forma controlada antes de sair.
            if (cmd.equals("sair") || cmd.equals("exit") || cmd.equals("quit")) {
                mixer.stopAll();
                panel.stopPanel();
                console.shutdownMessage("Encerrando a mesa de DJ...");
                return;
            }

            // Cada comando devolve uma mensagem, exibida dentro do painel
            // (em vez de rolar a tela e atrapalhar o layout fixo).
            String message = handleCommand(mixer, cmd, parts);
            console.setMessage(message);
            console.promptAgain();
        }

        // Se a entrada padrao fechar (Ctrl+D / pipe), encerra tudo com seguranca.
        mixer.stopAll();
        panel.stopPanel();
    }

    /** Despacha o comando digitado e devolve a mensagem de resposta. */
    private static String handleCommand(Mixer mixer, String cmd, String[] parts) {
        switch (cmd) {
            case "play":
            case "resume":
                return handleResume(mixer, parts);
            case "pause":
                return handlePause(mixer, parts);
            case "add":
                return handleAdd(mixer, parts);
            case "remove":
            case "rm":
                return handleRemove(mixer, parts);
            case "list":
            case "status":
                return "Status atual de todas as faixas exibido no painel acima.";
            case "help":
                return "Comandos: play/pause/remove <faixa>, add <arquivo>, list, exit.";
            default:
                return "Comando desconhecido: " + cmd + " (digite 'help')";
        }
    }

    /**
     * Carrega uma faixa de exemplo. Uma falha aqui (arquivo ausente, formato
     * nao suportado, sem placa de som) e reportada mas NAO derruba o programa:
     * as outras faixas continuam funcionando normalmente.
     */
    private static void loadStem(Mixer mixer, String name, String path) {
        try {
            Track track = Track.fromFile(name, path, mixer.getStartGate());
            mixer.addTrack(track);
            System.out.println("Faixa carregada: " + name + " (" + path + ")");
        } catch (Exception e) {
            System.out.println("Falha ao carregar " + name + " de " + path + ": " + e.getMessage());
        }
    }

    /** pause <faixa> — sinaliza a pausa; a thread da faixa continua viva. */
    private static String handlePause(Mixer mixer, String[] parts) {
        if (parts.length < 2) {
            return "Uso: pause <faixa>";
        }
        Track track = mixer.get(parts[1]);
        if (track == null) {
            return "Faixa nao encontrada: " + parts[1];
        }
        track.pause();
        return "Pausado: " + track.getName();
    }

    /** play <faixa> — retoma a reproducao de onde parou. */
    private static String handleResume(Mixer mixer, String[] parts) {
        if (parts.length < 2) {
            return "Uso: play <faixa>";
        }
        Track track = mixer.get(parts[1]);
        if (track == null) {
            return "Faixa nao encontrada: " + parts[1];
        }
        track.resume();
        return "Tocando: " + track.getName();
    }

    /**
     * remove <faixa> — tira a faixa da mesa.
     *
     * Nao mata a thread na forca: o Mixer pede o encerramento controlado
     * (requestStop), a thread sai do loop no proximo ponto seguro e libera o
     * recurso de audio. A faixa some do painel imediatamente.
     */
    private static String handleRemove(Mixer mixer, String[] parts) {
        if (parts.length < 2) {
            return "Uso: remove <faixa>";
        }
        Track removed = mixer.removeTrack(parts[1]);
        if (removed == null) {
            return "Faixa nao encontrada: " + parts[1];
        }
        return "Faixa removida da mesa: " + removed.getName();
    }

    /**
     * Adiciona uma faixa nova enquanto a musica ja esta tocando. Tres formas:
     *
     *   add <arquivo>          -> carrega o audio; o nome vem do arquivo
     *                             (ex: "add stems/other.wav" vira a faixa "other")
     *   add <nome> <arquivo>   -> carrega o audio com o nome escolhido
     *                             (ex: "add synth2 stems/other.wav")
     *   add <nome>             -> sem arquivo correspondente, gera um som
     *                             sintetizado na hora (ex: "add guitarra")
     */
    private static String handleAdd(Mixer mixer, String[] parts) {
        if (parts.length < 2) {
            return "Uso: add <arquivo> | add <nome> <arquivo> | add <nome>";
        }

        String name;
        String path;
        if (parts.length >= 3) {
            // Forma explicita: nome e caminho informados separadamente.
            name = parts[1];
            path = parts[2];
        } else if (new File(parts[1]).isFile()) {
            // Um unico argumento que E um arquivo existente: nome vem dele.
            path = parts[1];
            name = nameFromPath(path);
        } else {
            // Um unico argumento que nao e arquivo: som sintetizado.
            name = parts[1];
            path = null;
        }

        if (mixer.exists(name)) {
            return "Ja existe uma faixa chamada: " + name;
        }

        try {
            Track track;
            if (path != null) {
                track = Track.fromFile(name, path, mixer.getStartGate());
            } else {
                Clip clip = ToneGenerator.generateClip(name);
                track = new Track(name, clip, mixer.getStartGate(), "som sintetizado");
            }
            // O portao de largada ja esta aberto, entao a faixa toca na hora.
            mixer.addTrack(track);
            return "Instrumento adicionado e tocando: " + name;
        } catch (Exception e) {
            return "Falha ao adicionar " + name + ": " + e.getMessage();
        }
    }

    /** Deriva o nome da faixa do arquivo: "stems/other.wav" -> "other". */
    private static String nameFromPath(String path) {
        String base = new File(path).getName();
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        return base.toLowerCase(Locale.ROOT);
    }
}
