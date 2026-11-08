package br.com.biblioteca;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/** Relógio de teste que pode andar para frente (simular atraso sem esperar). */
class MutableClock extends Clock {

    private final ZoneId zone;
    private volatile Instant now = Instant.now();

    MutableClock(ZoneId zone) {
        this.zone = zone;
    }

    void reset() {
        now = Instant.now();
    }

    void advanceDays(long days) {
        now = now.plus(Duration.ofDays(days));
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId z) {
        return this;
    }

    @Override
    public Instant instant() {
        return now;
    }
}
