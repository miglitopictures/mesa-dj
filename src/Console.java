import java.util.List;

/**
 * Cuida de TODA a saida na tela, em um layout fixo estilo "painel de mesa".
 *
 * ---------------------------------------------------------------------------
 * O PROBLEMA DE UX QUE ESTA CLASSE RESOLVE
 * ---------------------------------------------------------------------------
 * A atividade pede um painel que se atualize sozinho a cada 2 segundos. A
 * forma ingenua (limpar a tela inteira e reimprimir tudo) tem um efeito
 * colateral horrivel: se voce estiver no meio da digitacao de um comando
 * quando o painel atualizar, seu texto some da tela ou fica embaralhado, e
 * voce precisa "correr" para digitar antes do proximo refresh.
 *
 * A solucao aqui e um layout de tela FIXO, usando codigos ANSI/VT100:
 *   - o painel sempre ocupa as MESMAS linhas do topo (posicionamento
 *     absoluto, linha por linha), nunca rolando a tela;
 *   - a linha de comando do usuario mora em uma linha propria, mais abaixo,
 *     na qual o painel NUNCA escreve;
 *   - antes de redesenhar, salvamos a posicao do cursor (ESC 7) e, ao final,
 *     restauramos (ESC 8), devolvendo o cursor exatamente para onde o
 *     usuario estava digitando.
 *
 * Resultado: o painel pisca sozinho a cada 2s enquanto o texto que voce
 * digitou (mesmo sem ter apertado Enter) continua intacto na tela. Nao ha
 * pressa para digitar.
 *
 * ---------------------------------------------------------------------------
 * SINCRONIZACAO
 * ---------------------------------------------------------------------------
 * Duas threads escrevem na tela: a do painel (a cada 2s) e a principal
 * (respostas aos comandos). Se as duas escrevessem ao mesmo tempo, as
 * sequencias ANSI se misturariam e o layout quebraria. Por isso, toda escrita
 * acontece dentro de synchronized (lock), garantindo que um redesenho inteiro
 * nunca seja interrompido pela metade por outra thread.
 */
public class Console {

    /** Caractere ESC (27), inicio de toda sequencia de controle ANSI. */
    private static final char ESC = 27;

    /** Quantas faixas cabem no painel; as linhas sao reservadas de antemao. */
    private static final int MAX_TRACKS_SHOWN = 8;

    // Mapa fixo da tela: cada elemento tem sua linha reservada e imutavel.
    private static final int ROW_TITLE = 1;
    private static final int ROW_HEADER = ROW_TITLE + 1;
    private static final int ROW_SEP1 = ROW_HEADER + 1;
    private static final int ROW_TRACK_START = ROW_SEP1 + 1;
    private static final int ROW_SEP2 = ROW_TRACK_START + MAX_TRACKS_SHOWN;
    private static final int ROW_MESSAGE = ROW_SEP2 + 1;
    private static final int ROW_HINT1 = ROW_MESSAGE + 1;
    private static final int ROW_HINT2 = ROW_HINT1 + 1;

    /** Linha onde o usuario digita. O painel jamais escreve nela. */
    private static final int INPUT_ROW = ROW_HINT2 + 2;

    private static final String SEPARATOR =
            "----------------------------------------------------------------";

    private static final String HINT1 =
            "play <faixa> | pause <faixa> | remove <faixa> | list | help | exit";
    private static final String HINT2 =
            "add <arquivo> | add <nome> <arquivo> | add <nome>  (ex: add stems/other.wav)";

    private final Mixer mixer;

    /** Lock que serializa as escritas na tela vindas de threads diferentes. */
    private final Object lock = new Object();

    /** Ultima mensagem de resposta a um comando, exibida dentro do painel. */
    private volatile String message = "Bem-vindo! As faixas iniciais ja estao tocando juntas.";

    public Console(Mixer mixer) {
        this.mixer = mixer;
    }

    /** Limpa a tela uma unica vez, desenha o painel e posiciona o prompt. */
    public void init() {
        synchronized (lock) {
            System.out.print(ESC + "[2J" + ESC + "[H"); // limpa tudo e vai pro topo
            renderPanel();
            moveTo(INPUT_ROW, 1);
            System.out.print("> ");
            System.out.flush();
        }
    }

    /** Troca a mensagem exibida no painel e redesenha imediatamente. */
    public void setMessage(String msg) {
        this.message = msg;
        refresh();
    }

    /**
     * Redesenha o painel preservando a digitacao em andamento.
     * Chamado pela thread do painel a cada 2 segundos e apos cada comando.
     */
    public void refresh() {
        synchronized (lock) {
            System.out.print(ESC + "7"); // salva a posicao atual do cursor
            renderPanel();
            System.out.print(ESC + "8"); // restaura o cursor na linha de comando
            System.out.flush();
        }
    }

    /** Reposiciona o cursor na linha de comando e imprime um prompt limpo. */
    public void promptAgain() {
        synchronized (lock) {
            moveTo(INPUT_ROW, 1);
            System.out.print(ESC + "[K> "); // [K limpa o resto da linha
            System.out.flush();
        }
    }

    /** Mensagem final da saida, impressa abaixo do painel. */
    public void shutdownMessage(String msg) {
        synchronized (lock) {
            moveTo(INPUT_ROW + 2, 1);
            System.out.println(msg);
        }
    }

    /** Desenha o painel inteiro, sempre nas mesmas linhas absolutas. */
    private void renderPanel() {
        printRow(ROW_TITLE, "=== MESA DE DJ - PAINEL AO VIVO ===");
        printRow(ROW_HEADER, String.format("%-15s %-10s %s", "FAIXA", "ESTADO", "ORIGEM"));
        printRow(ROW_SEP1, SEPARATOR);

        List<Track> tracks = mixer.allTracks();
        for (int i = 0; i < MAX_TRACKS_SHOWN; i++) {
            // Linhas sem faixa sao escritas em branco, para apagar restos de
            // uma faixa que foi removida e manter o layout sempre do mesmo tamanho.
            String content = "";
            if (i < tracks.size()) {
                Track t = tracks.get(i);
                content = String.format("%-15s %-10s %s", t.getName(), t.getState(), t.getSource());
            }
            printRow(ROW_TRACK_START + i, content);
        }

        printRow(ROW_SEP2, SEPARATOR);

        String extra = tracks.size() > MAX_TRACKS_SHOWN
                ? "(+" + (tracks.size() - MAX_TRACKS_SHOWN) + " faixas nao exibidas) "
                : "";
        printRow(ROW_MESSAGE, extra + message);
        printRow(ROW_HINT1, HINT1);
        printRow(ROW_HINT2, HINT2);
    }

    /** Escreve uma linha inteira em uma posicao fixa, limpando o resto dela. */
    private void printRow(int row, String content) {
        moveTo(row, 1);
        System.out.print(content);
        System.out.print(ESC + "[K"); // apaga qualquer sobra do desenho anterior
    }

    /** Move o cursor para (linha, coluna) absolutas da tela. */
    private void moveTo(int row, int col) {
        System.out.print(ESC + "[" + row + ";" + col + "H");
    }
}
