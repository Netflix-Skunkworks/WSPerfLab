package perf.client;

import com.netflix.numerus.NumerusRollingNumberEvent;

public enum Events implements NumerusRollingNumberEvent {
    SUCCESS(true), ERROR(true);

    private final boolean isCounter;
    private final boolean isMaxUpdater;

    Events(boolean isCounter) {
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
