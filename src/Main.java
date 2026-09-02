import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.Locale;
import javax.sound.sampled.Clip;

public class Main {

    public static void main(String[] args) throws Exception {
        Mixer mixer = new Mixer();

        System.out.println("Carregando faixas de stems/ ...");
        loadStem(mixer, "bateria", "stems/drums.wav");
        loadStem(mixer, "baixo", "stems/bass.wav");
        loadStem(mixer, "synth", "stems/other.wav");

        mixer.releaseAll();


        Console console = new Console(mixer);
        console.init();

        StatusPanel panel = new StatusPanel(console);
        Thread panelThread = new Thread(panel, "painel-status");
        panelThread.setDaemon(true);
        panelThread.start();

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

            if (cmd.equals("sair") || cmd.equals("exit") || cmd.equals("quit")) {
                mixer.stopAll();
                panel.stopPanel();
                console.shutdownMessage("Encerrando a mesa de DJ...");
                return;
            }

            String message = handleCommand(mixer, cmd, parts);
            console.setMessage(message);
            console.promptAgain();
        }

        mixer.stopAll();
        panel.stopPanel();
    }

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

    private static void loadStem(Mixer mixer, String name, String path) {
        try {
            Track track = Track.fromFile(name, path, mixer.getStartGate());
            mixer.addTrack(track);
            System.out.println("Faixa carregada: " + name + " (" + path + ")");
        } catch (Exception e) {
            System.out.println("Falha ao carregar " + name + " de " + path + ": " + e.getMessage());
        }
    }

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

    private static String handleAdd(Mixer mixer, String[] parts) {
        if (parts.length < 2) {
            return "Uso: add <arquivo> | add <nome> <arquivo> | add <nome>";
        }

        String name;
        String path;
        if (parts.length >= 3) {
            name = parts[1];
            path = parts[2];
        } else if (new File(parts[1]).isFile()) {
            path = parts[1];
            name = nameFromPath(path);
        } else {
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
            mixer.addTrack(track);
            return "Instrumento adicionado e tocando: " + name;
        } catch (Exception e) {
            return "Falha ao adicionar " + name + ": " + e.getMessage();
        }
    }

    private static String nameFromPath(String path) {
        String base = new File(path).getName();
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        return base.toLowerCase(Locale.ROOT);
    }
}
