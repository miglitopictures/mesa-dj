public enum TrackState {
    PLAYING("TOCANDO"),
    PAUSED("PAUSADO"),
    STOPPED("PARADO");

    private final String label;

    TrackState(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}