public enum TrackState {
    PLAYING("TOCANDO"),
    PAUSED("PAUSADO"),
    STOPPED("PARADO");

    /** Texto exibido no painel de status. */
    private final String label;

    TrackState(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
