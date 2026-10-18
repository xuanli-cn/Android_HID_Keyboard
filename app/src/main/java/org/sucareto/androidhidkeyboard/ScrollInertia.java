package org.sucareto.androidhidkeyboard;

/** Time-based wheel decay. The view supplies animation frames; tests supply a fake clock. */
final class ScrollInertia {
    interface Output { void scroll(int steps); }

    private static final float DECAY_MILLIS = 240;
    private final Output output;
    private float velocity, remainder;
    private long lastTime, startTime;
    private boolean running;

    ScrollInertia(Output output) {
        this.output = output;
    }

    void start(float stepsPerMillis, long time) {
        stop();
        velocity = Math.max(-0.12f, Math.min(0.12f, stepsPerMillis));
        running = Math.abs(velocity) >= 0.006f;
        lastTime = startTime = time;
    }

    boolean advance(long time) {
        if (!running) return false;
        if (time - startTime >= 1200) {
            stop();
            return false;
        }
        // Avoid a large wheel burst if the UI misses several animation frames.
        long elapsed = Math.max(0, Math.min(64, time - lastTime));
        float decay = (float) Math.exp(-elapsed / DECAY_MILLIS);
        remainder += velocity * DECAY_MILLIS * (1 - decay);
        velocity *= decay;
        lastTime = time;
        int steps = (int) remainder;
        if (steps != 0) {
            output.scroll(steps);
            remainder -= steps;
        }
        if (Math.abs(velocity) < 0.002f) stop();
        return running;
    }

    void stop() {
        running = false;
        velocity = remainder = 0;
    }

    boolean isRunning() { return running; }
}
