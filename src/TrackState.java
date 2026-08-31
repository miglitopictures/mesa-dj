/**
 * Estados possiveis de uma faixa/instrumento na mesa de DJ.
 *
 * Este enum e o "estado compartilhado" da atividade: ele e lido e escrito
 * por threads diferentes (a thread da propria faixa, a thread principal que
 * processa os comandos do DJ e a thread do painel de status). Por isso, todo
 * acesso ao campo que guarda este estado dentro de Track acontece sob
 * "synchronized" — ver Track.java.
 *
 * PLAYING  - a faixa esta tocando seu loop de audio.
 * PAUSED   - a faixa foi pausada pelo DJ; a thread continua viva, apenas
 *            esperando (wait) ate receber a ordem de retomar.
 * STOPPED  - foi pedido o encerramento da faixa; a thread vai sair do seu
 *            loop no proximo ponto seguro e liberar o recurso de audio.
 */
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
