public class StatusPanel implements Runnable {

    private static final long REFRESH_MS = 2000;

    private final Console console;

    private volatile boolean running = true;

    public StatusPanel(Console console) {
        this.console = console;
    }

    public void stopPanel() {
        running = false;
    }

    @Override
    public void run() {
        try {
            while (running) {
                Thread.sleep(REFRESH_MS);
                if (running) { 
                    console.refresh();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
