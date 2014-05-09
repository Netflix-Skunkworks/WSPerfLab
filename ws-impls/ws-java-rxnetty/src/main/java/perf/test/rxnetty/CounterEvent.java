package perf.test.rxnetty;

import com.netflix.numerus.NumerusRollingNumberEvent;

public enum CounterEvent implements NumerusRollingNumberEvent {

    REQUESTS(true), SUCCESS(true), HTTP_ERROR(true), NETTY_ERROR(true), CLIENT_POOL_EXHAUSTION(true),
    SOCKET_EXCEPTION(true), IO_EXCEPTION(true), CANCELLATION_EXCEPTION(true), PARSING_EXCEPTION(true), BYTES(true);

    private final boolean isCounter;
    private final boolean isMaxUpdater;

    CounterEvent(boolean isCounter) {
        this.isCounter = isCounter;
        this.isMaxUpdater = !isCounter;
    }

    @Override
    public boolean isCounter() {
        return isCounter;
    }

    @Override
    public boolean isMaxUpdater() {
        return isMaxUpdater;
    }

    @Override
    public NumerusRollingNumberEvent[] getValues() {
        return values();
    }

}
