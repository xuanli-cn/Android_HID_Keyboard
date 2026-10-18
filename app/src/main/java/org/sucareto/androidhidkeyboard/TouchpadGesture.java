package org.sucareto.androidhidkeyboard;

/** Gesture state independent of Android views for device-free transition tests. */
final class TouchpadGesture {
    interface Output {
        void move(int x, int y);
        void scroll(int steps);
        void click(int button);
    }

    private static final long TAP_MILLIS = 350;
    private final Output output;
    private final float slop, scrollStep;
    private float speed = 3;
    private int fingers;
    private boolean twoFinger, moved, blocked, waitingForLift;
    private long downTime;
    private float startX, startY, secondX, secondY, lastX, lastY;
    private float remainderX, remainderY, remainderScroll;

    TouchpadGesture(float slop, float scrollStep, Output output) {
        this.slop = slop;
        this.scrollStep = scrollStep;
        this.output = output;
    }

    void setSpeed(float speed) {
        this.speed = speed;
    }

    void down(float x, float y, long time) {
        cancel();
        fingers = 1;
        downTime = time;
        startX = lastX = x;
        startY = lastY = y;
    }

    void secondDown(float x1, float y1, float x2, float y2) {
        if (fingers != 1 || twoFinger || waitingForLift) {
            block();
            return;
        }
        moved |= outsideSlop(x1 - startX, y1 - startY);
        // Do not right-click when adding a finger after moving the pointer.
        blocked |= moved;
        fingers = 2;
        twoFinger = true;
        moved = false;
        startX = x1;
        startY = y1;
        secondX = x2;
        secondY = y2;
        lastX = (x1 + x2) / 2;
        lastY = (y1 + y2) / 2;
        remainderX = remainderY = remainderScroll = 0;
    }

    void move(float x1, float y1, float x2, float y2) {
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
        if (twoFinger) {
            remainderScroll += (lastY - y) / scrollStep;
            int steps = (int) remainderScroll;
            if (steps != 0) {
                output.scroll(steps);
                remainderScroll -= steps;
            }
        } else {
            remainderX += (x - lastX) * speed;
            remainderY += (y - lastY) * speed;
            int dx = (int) remainderX;
            int dy = (int) remainderY;
            if (dx != 0 || dy != 0) output.move(dx, dy);
            remainderX -= dx;
            remainderY -= dy;
        }
        lastX = x;
        lastY = y;
    }

    void pointerUp(float remainingX, float remainingY) {
        // Consume all remaining movement until the entire hand lifts.
        waitingForLift = true;
        lastX = remainingX;
        lastY = remainingY;
    }

    void up(long time) {
        if (fingers != 0 && !blocked && !moved && time - downTime <= TAP_MILLIS) {
            output.click(twoFinger ? 2 : 1);
        }
        cancel();
    }

    void block() {
        blocked = true;
        waitingForLift = true;
    }

    void cancel() {
        fingers = 0;
        twoFinger = moved = blocked = waitingForLift = false;
        remainderX = remainderY = remainderScroll = 0;
    }

    private boolean outsideSlop(float dx, float dy) {
        return dx * dx + dy * dy > slop * slop;
    }
}
