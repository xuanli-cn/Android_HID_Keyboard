package org.sucareto.androidhidkeyboard;

/** Gesture state independent of Android views for device-free transition tests. */
final class TouchpadGesture {
    interface Output {
        void move(int x, int y);
        void scroll(int steps);
        void click(int button);
        void press(int button);
        void release(int button);
    }

    private static final long TAP_MILLIS = 350;
    private static final long DOUBLE_TAP_MILLIS = 300;
    private final Output output;
    private final ScrollInertia inertia;
    private final float slop, scrollStep, density;
    private float speed = 3, scrollSpeed = 3;
    private boolean reverseScroll, acceleration, inertiaEnabled;
    private int fingers;
    private boolean twoFinger, moved, blocked, waitingForLift, dragCandidate, dragging;
    private boolean previousTap;
    private long downTime, lastMotionTime, lastScrollTime, previousTapTime;
    private float startX, startY, secondX, secondY, lastX, lastY, previousTapX, previousTapY;
    private float remainderX, remainderY, remainderScroll, scrollVelocity;

    TouchpadGesture(float slop, float scrollStep, Output output) {
        this.slop = slop;
        this.scrollStep = scrollStep;
        density = scrollStep / 20;
        this.output = output;
        inertia = new ScrollInertia(output::scroll);
    }

    void setSpeed(float speed) { this.speed = speed; }

    void setScrollSpeed(float speed) {
        scrollSpeed = speed;
        remainderScroll = scrollVelocity = 0;
        stopInertia();
    }

    void setReverseScroll(boolean reverseScroll) {
        this.reverseScroll = reverseScroll;
        remainderScroll = scrollVelocity = 0;
        stopInertia();
    }

    void setAcceleration(boolean enabled) { acceleration = enabled; }

    void setInertiaEnabled(boolean enabled) {
        inertiaEnabled = enabled;
        if (!enabled) stopInertia();
    }

    void stopInertia() { inertia.stop(); }
    boolean hasInertia() { return inertia.isRunning(); }
    boolean advanceInertia(long time) { return inertia.advance(time); }

    void down(float x, float y, long time) {
        boolean stoppingInertia = inertia.isRunning();
        float dx = x - previousTapX, dy = y - previousTapY;
        boolean canDrag = previousTap && time >= previousTapTime
                && time - previousTapTime <= DOUBLE_TAP_MILLIS
                && dx * dx + dy * dy <= slop * slop * 16;
        cancel();
        // A touch used to stop momentum should not also click an item underneath.
        blocked = stoppingInertia;
        dragCandidate = canDrag;
        fingers = 1;
        downTime = lastMotionTime = time;
        startX = lastX = x;
        startY = lastY = y;
    }

    void secondDown(float x1, float y1, float x2, float y2, long time) {
        if (fingers != 1 || twoFinger || waitingForLift) {
            block();
            return;
        }
        moved |= outsideSlop(x1 - startX, y1 - startY);
        // Adding a finger after moving (including dragging) must not right-click.
        blocked |= moved;
        releaseDrag();
        dragCandidate = false;
        fingers = 2;
        twoFinger = true;
        moved = false;
        startX = x1;
        startY = y1;
        secondX = x2;
        secondY = y2;
        lastX = (x1 + x2) / 2;
        lastY = (y1 + y2) / 2;
        lastMotionTime = time;
        remainderX = remainderY = remainderScroll = scrollVelocity = 0;
    }

    void move(float x1, float y1, float x2, float y2, long time) {
        if (fingers == 0) return;
        if (waitingForLift) {
            blocked |= outsideSlop(x1 - lastX, y1 - lastY);
            return;
        }
        boolean beyondSlop = outsideSlop(x1 - startX, y1 - startY);
        if (twoFinger) beyondSlop |= outsideSlop(x2 - secondX, y2 - secondY);
        moved |= beyondSlop;
        float x = twoFinger ? (x1 + x2) / 2 : x1;
        float y = twoFinger ? (y1 + y2) / 2 : y1;
        // Keep the initial origin until slop is crossed.
        if (!moved) return;
        long elapsed = Math.max(1, time - lastMotionTime);
        if (twoFinger) {
            float steps = (lastY - y) / scrollStep * (scrollSpeed / 3) * (reverseScroll ? -1 : 1);
            remainderScroll += steps;
            if (steps != 0) {
                float sample = Math.max(-0.15f, Math.min(0.15f, steps / elapsed));
                scrollVelocity = sample * 0.6f + scrollVelocity * 0.4f;
                lastScrollTime = time;
            } else {
                scrollVelocity *= (float) Math.exp(-elapsed / 60f);
            }
            sendScrollRemainder();
        } else {
            if (dragCandidate && !dragging) {
                dragging = true;
                output.press(1);
            }
            float gain = speed;
            if (acceleration) {
                float velocity = (float) Math.hypot(x - lastX, y - lastY) / (elapsed * density);
                // Slow movement keeps the selected speed; fast swipes gain up to 2.5x.
                gain *= 1 + Math.min(1.5f, Math.max(0, velocity - 0.15f) * 1.5f);
            }
            remainderX += (x - lastX) * gain;
            remainderY += (y - lastY) * gain;
            int dx = (int) remainderX, dy = (int) remainderY;
            if (dx != 0 || dy != 0) output.move(dx, dy);
            remainderX -= dx;
            remainderY -= dy;
        }
        lastX = x;
        lastY = y;
        lastMotionTime = time;
    }

    void pointerUp(float remainingX, float remainingY) {
        waitingForLift = true;
        lastX = remainingX;
        lastY = remainingY;
    }

    void up(long time) {
        boolean tap = fingers != 0 && !blocked && !moved && time - downTime <= TAP_MILLIS;
        boolean fling = inertiaEnabled && twoFinger && moved && !blocked && time - lastScrollTime <= 100;
        float releaseVelocity = scrollVelocity * (float) Math.exp(-Math.max(0, time - lastMotionTime) / 60f);
        boolean rememberTap = tap && !twoFinger;
        if (tap) output.click(twoFinger ? 2 : 1);
        previousTapX = startX;
        previousTapY = startY;
        resetTouch();
        previousTap = rememberTap;
        previousTapTime = time;
        if (fling) inertia.start(releaseVelocity, time);
    }

    void block() {
        releaseDrag();
        dragCandidate = previousTap = false;
        blocked = true;
        waitingForLift = true;
        stopInertia();
    }

    void cancel() {
        resetTouch();
        previousTap = false;
        stopInertia();
    }

    private void resetTouch() {
        releaseDrag();
        fingers = 0;
        twoFinger = moved = blocked = waitingForLift = dragCandidate = false;
        remainderX = remainderY = remainderScroll = scrollVelocity = 0;
        lastScrollTime = 0;
    }

    private void releaseDrag() {
        if (dragging) output.release(1);
        dragging = false;
    }

    private void sendScrollRemainder() {
        int steps = (int) remainderScroll;
        if (steps != 0) {
            output.scroll(steps);
            remainderScroll -= steps;
        }
    }

    private boolean outsideSlop(float dx, float dy) {
        return dx * dx + dy * dy > slop * slop;
    }
}
