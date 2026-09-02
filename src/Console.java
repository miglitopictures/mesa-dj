import java.util.List;

public class Console {

    private static final char ESC = 27;

    private static final int MAX_TRACKS_SHOWN = 8;

    private static final int ROW_TITLE = 1;
    private static final int ROW_HEADER = ROW_TITLE + 1;
    private static final int ROW_SEP1 = ROW_HEADER + 1;
    private static final int ROW_TRACK_START = ROW_SEP1 + 1;
    private static final int ROW_SEP2 = ROW_TRACK_START + MAX_TRACKS_SHOWN;
    private static final int ROW_MESSAGE = ROW_SEP2 + 1;
    private static final int ROW_HINT1 = ROW_MESSAGE + 1;
    private static final int ROW_HINT2 = ROW_HINT1 + 1;

    private static final int INPUT_ROW = ROW_HINT2 + 2;

    private static final String SEPARATOR =
            "----------------------------------------------------------------";

    private static final String HINT1 =
            "play <faixa> | pause <faixa> | remove <faixa> | list | help | exit";
    private static final String HINT2 =
            "add <arquivo> | add <nome> <arquivo> | add <nome>  (ex: add stems/other.wav)";

    private final Mixer mixer;

    private final Object lock = new Object();

    private volatile String message = "Bem-vindo! As faixas iniciais ja estao tocando juntas.";

    public Console(Mixer mixer) {
        this.mixer = mixer;
    }

    public void init() {
        synchronized (lock) {
            System.out.print(ESC + "[2J" + ESC + "[H");
            renderPanel();
            moveTo(INPUT_ROW, 1);
            System.out.print("> ");
            System.out.flush();
        }
    }

    public void setMessage(String msg) {
        this.message = msg;
        refresh();
    }

    public void refresh() {
        synchronized (lock) {
            System.out.print(ESC + "7");
            renderPanel();
            System.out.print(ESC + "8");
            System.out.flush();
        }
    }

    public void promptAgain() {
        synchronized (lock) {
            moveTo(INPUT_ROW, 1);
            System.out.print(ESC + "[K> ");
            System.out.flush();
        }
    }

    public void shutdownMessage(String msg) {
        synchronized (lock) {
            moveTo(INPUT_ROW + 2, 1);
            System.out.println(msg);
        }
    }

    private void renderPanel() {
        printRow(ROW_TITLE, "=== MESA DE DJ - PAINEL AO VIVO ===");
        printRow(ROW_HEADER, String.format("%-15s %-10s %s", "FAIXA", "ESTADO", "ORIGEM"));
        printRow(ROW_SEP1, SEPARATOR);

        List<Track> tracks = mixer.allTracks();
        for (int i = 0; i < MAX_TRACKS_SHOWN; i++) {
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

    private void printRow(int row, String content) {
        moveTo(row, 1);
        System.out.print(content);
        System.out.print(ESC + "[K");
    }

    private void moveTo(int row, int col) {
        System.out.print(ESC + "[" + row + ";" + col + "H");
    }
}