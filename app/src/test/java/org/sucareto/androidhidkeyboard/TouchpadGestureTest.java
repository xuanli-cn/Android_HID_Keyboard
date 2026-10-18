package org.sucareto.androidhidkeyboard;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public class TouchpadGestureTest {
    private final List<String> events = new ArrayList<>();
    private final TouchpadGesture pad = new TouchpadGesture(5, 20, new TouchpadGesture.Output() {
        public void move(int x, int y) { events.add("move:" + x + "," + y); }
        public void scroll(int steps) { events.add("scroll:" + steps); }
        public void click(int button) { events.add("click:" + button); }
        public void press(int button) { events.add("press:" + button); }
        public void release(int button) { events.add("release:" + button); }
    });

    private long time;

    private void down(float x, float y, long time) {
        this.time = time;
        pad.down(x, y, time);
    }

    private void up(long time) {
        this.time = time;
        pad.up(time);
    }

    private void move(float x1, float y1, float x2, float y2) {
        pad.move(x1, y1, x2, y2, time += 16);
    }

    private void secondDown(float x1, float y1, float x2, float y2) {
        pad.secondDown(x1, y1, x2, y2, ++time);
    }

    @Test public void tapAllowsSmallJitter() {
        down(10, 10, 0);
        move(12, 11, 0, 0);
        up(100);
        assertEquals(Arrays.asList("click:1"), events);
    }

    @Test public void doubleTapSendsExactlyTwoClicks() {
        down(0, 0, 0);
        up(70);
        down(0, 0, 130);
        up(200);
        assertEquals(Arrays.asList("click:1", "click:1"), events);
    }

    @Test public void movementUsesRelativeDeltasAndDoesNotClick() {
        down(10, 20, 0);
        move(20, 15, 0, 0);
        move(23, 14, 0, 0);
        up(100);
        assertEquals(Arrays.asList("move:30,-15", "move:9,-3"), events);
    }

    @Test public void fractionalMovementIsPreservedAndNewTouchRebases() {
        pad.setSpeed(1);
        down(0, 0, 0);
        move(6, 0, 0, 0);
        move(6.4f, 0, 0, 0);
        move(6.8f, 0, 0, 0);
        move(7.2f, 0, 0, 0);
        up(100);
        down(100, 100, 200);
        move(106, 100, 0, 0);
        up(300);
        assertEquals(Arrays.asList("move:6,0", "move:1,0", "move:6,0"), events);
    }

    @Test public void twoFingerTapWorksWhenEitherFingerLiftsFirst() {
        down(10, 10, 0);
        secondDown(10, 10, 50, 10);
        pad.pointerUp(50, 10);
        move(51, 11, 0, 0);
        up(100);
        assertEquals(Arrays.asList("click:2"), events);
    }

    @Test public void scrollingAccumulatesRemainderAndNeverMovesPointer() {
        down(0, 100, 0);
        secondDown(0, 100, 50, 100);
        move(0, 70, 50, 70);
        move(0, 60, 50, 60);
        pad.pointerUp(0, 60);
        move(100, 0, 0, 0);
        up(200);
        assertEquals(Arrays.asList("scroll:1", "scroll:1"), events);
    }

    @Test public void downwardScrollIsNegativeAndHorizontalSwipeDoesNotClick() {
        down(0, 0, 0);
        secondDown(0, 0, 50, 0);
        move(0, 40, 50, 40);
        up(100);
        assertEquals(Arrays.asList("scroll:-2"), events);
        events.clear();
        down(0, 0, 200);
        secondDown(0, 0, 50, 0);
        move(40, 0, 90, 0);
        up(300);
        assertTrue(events.isEmpty());
    }

    @Test public void remainingFingerMovementCancelsRightClick() {
        down(0, 0, 0);
        secondDown(0, 0, 50, 0);
        pad.pointerUp(50, 0);
        move(70, 0, 0, 0);
        up(100);
        assertTrue(events.isEmpty());
    }

    @Test public void reversedScrollFlipsBothDirectionsWithoutAffectingPointerOrClick() {
        pad.setReverseScroll(true);
        down(0, 100, 0);
        secondDown(0, 100, 50, 100);
        move(0, 60, 50, 60);
        move(0, 100, 50, 100);
        up(100);
        down(0, 0, 200);
        move(10, 0, 0, 0);
        up(250);
        down(0, 0, 300);
        secondDown(0, 0, 50, 0);
        pad.pointerUp(0, 0);
        up(350);
        assertEquals(Arrays.asList("scroll:-2", "scroll:2", "move:30,0", "click:2"), events);
    }

    @Test public void switchingDirectionClearsFractionalScrollAndCanRestoreOriginalDirection() {
        down(0, 100, 0);
        secondDown(0, 100, 50, 100);
        move(0, 90, 50, 90);
        pad.setReverseScroll(true);
        move(0, 70, 50, 70);
        up(100);
        pad.setReverseScroll(false);
        down(0, 100, 200);
        secondDown(0, 100, 50, 100);
        move(0, 80, 50, 80);
        up(300);
        assertEquals(Arrays.asList("scroll:-1", "scroll:1"), events);
    }

    @Test public void addingFingerAfterMovementDoesNotRightClickOrJump() {
        down(0, 0, 0);
        move(10, 0, 0, 0);
        secondDown(10, 0, 100, 100);
        pad.pointerUp(10, 0);
        up(150);
        assertEquals(Arrays.asList("move:30,0"), events);
    }

    @Test public void longHoldDoesNotClick() {
        down(0, 0, 0);
        up(600);
        assertTrue(events.isEmpty());
    }

    @Test public void cancellationAndThreeFingersSuppressClicksUntilNextTouch() {
        down(0, 0, 0);
        pad.cancel();
        up(100);
        down(0, 0, 200);
        secondDown(0, 0, 50, 0);
        pad.block();
        move(0, 80, 50, 80);
        up(300);
        assertTrue(events.isEmpty());
        down(0, 0, 400);
        up(450);
        assertEquals(Arrays.asList("click:1"), events);
    }

    @Test public void tapThenSlideHoldsLeftButtonUntilLift() {
        down(10, 10, 0);
        up(60);
        down(11, 10, 140);
        move(21, 10, 0, 0);
        move(25, 10, 0, 0);
        up(250);
        assertEquals(Arrays.asList("click:1", "press:1", "move:30,0", "move:12,0", "release:1"), events);
    }

    @Test public void dragCanStartAfterHoldingSecondTouch() {
        down(0, 0, 0);
        up(60);
        down(0, 0, 120);
        pad.move(10, 0, 0, 0, 800);
        up(900);
        assertEquals(Arrays.asList("click:1", "press:1", "move:30,0", "release:1"), events);
    }

    @Test public void cancellationAndThirdFingerReleaseDragExactlyOnce() {
        down(0, 0, 0);
        up(60);
        down(0, 0, 120);
        move(10, 0, 0, 0);
        pad.block();
        pad.cancel();
        up(200);
        assertEquals(Arrays.asList("click:1", "press:1", "move:30,0", "release:1"), events);
    }

    @Test public void addingSecondFingerReleasesDragWithoutRightClick() {
        down(0, 0, 0);
        up(60);
        down(0, 0, 120);
        move(10, 0, 0, 0);
        secondDown(10, 0, 50, 0);
        up(200);
        assertEquals(Arrays.asList("click:1", "press:1", "move:30,0", "release:1"), events);
    }

    @Test public void twoFingerTapAfterSingleTapDoesNotStartDrag() {
        down(0, 0, 0);
        up(60);
        down(0, 0, 120);
        secondDown(0, 0, 50, 0);
        pad.pointerUp(0, 0);
        up(200);
        assertEquals(Arrays.asList("click:1", "click:2"), events);
    }

    @Test public void lateOrDistantSecondTouchOnlyMovesPointer() {
        down(0, 0, 0);
        up(60);
        down(0, 0, 400);
        move(10, 0, 0, 0);
        up(450);
        down(0, 0, 500);
        up(550);
        down(100, 100, 600);
        move(110, 100, 0, 0);
        up(650);
        assertEquals(Arrays.asList("click:1", "move:30,0", "click:1", "move:30,0"), events);
    }

    @Test public void cancelClearsPendingDrag() {
        down(0, 0, 0);
        up(60);
        pad.cancel();
        down(0, 0, 120);
        move(10, 0, 0, 0);
        up(200);
        assertEquals(Arrays.asList("click:1", "move:30,0"), events);
    }

    @Test public void scrollSpeedIsIndependentOfPointerSpeed() {
        pad.setScrollSpeed(1);
        down(0, 100, 0);
        secondDown(0, 100, 50, 100);
        move(0, 40, 50, 40);
        up(100);
        pad.setScrollSpeed(6);
        down(0, 100, 200);
        secondDown(0, 100, 50, 100);
        move(0, 80, 50, 80);
        up(300);
        down(0, 0, 400);
        move(10, 0, 0, 0);
        up(500);
        assertEquals(Arrays.asList("scroll:1", "scroll:2", "move:30,0"), events);
    }

    @Test public void accelerationKeepsSlowPrecisionAndIncreasesFastMovement() {
        pad.setAcceleration(true);
        down(0, 0, 0);
        pad.move(100, 0, 0, 0, 1000);
        up(1001);
        down(0, 0, 2000);
        pad.move(100, 0, 0, 0, 2020);
        up(2021);
        pad.setAcceleration(false);
        down(0, 0, 3000);
        pad.move(100, 0, 0, 0, 3020);
        up(3021);
        assertEquals(Arrays.asList("move:300,0", "move:750,0", "move:300,0"), events);
    }

    @Test public void fastScrollStartsInertiaAndNewTouchStopsIt() {
        pad.setInertiaEnabled(true);
        down(0, 100, 0);
        secondDown(0, 100, 50, 100);
        move(0, 60, 50, 60);
        pad.pointerUp(0, 60);
        up(30);
        assertTrue(pad.hasInertia());
        pad.advanceInertia(62);
        assertTrue(events.size() > 1);
        down(0, 0, 70);
        assertFalse(pad.hasInertia());
        int count = events.size();
        assertFalse(pad.advanceInertia(90));
        assertEquals(count, events.size());
    }

    @Test public void inertiaRespectsReverseAndScrollSpeed() {
        pad.setInertiaEnabled(true);
        pad.setReverseScroll(true);
        pad.setScrollSpeed(6);
        down(0, 100, 0);
        secondDown(0, 100, 50, 100);
        move(0, 60, 50, 60);
        up(25);
        for (int time = 41; time < 1200; time += 16) pad.advanceInertia(time);
        assertEquals("scroll:-4", events.get(0));
        assertTrue(events.size() > 1);
        for (String event : events) assertTrue(event.startsWith("scroll:-"));
        assertFalse(pad.hasInertia());
    }

    @Test public void pauseBeforeLiftOrWaitingForRemainingFingerPreventsInertia() {
        pad.setInertiaEnabled(true);
        down(0, 100, 0);
        secondDown(0, 100, 50, 100);
        move(0, 60, 50, 60);
        pad.move(0, 60, 50, 60, 200);
        up(210);
        assertFalse(pad.hasInertia());
        down(0, 100, 300);
        secondDown(0, 100, 50, 100);
        move(0, 60, 50, 60);
        pad.pointerUp(0, 60);
        up(500);
        assertFalse(pad.hasInertia());
    }

    @Test public void disabledInertiaAndCancellationDoNotContinueScrolling() {
        down(0, 100, 0);
        secondDown(0, 100, 50, 100);
        move(0, 60, 50, 60);
        up(30);
        assertFalse(pad.hasInertia());
        pad.setInertiaEnabled(true);
        down(0, 100, 100);
        secondDown(0, 100, 50, 100);
        move(0, 60, 50, 60);
        up(130);
        assertTrue(pad.hasInertia());
        pad.cancel();
        assertFalse(pad.hasInertia());
        int count = events.size();
        assertFalse(pad.advanceInertia(200));
        assertEquals(count, events.size());
    }

    @Test public void touchToStopInertiaDoesNotAlsoClick() {
        pad.setInertiaEnabled(true);
        down(0, 100, 0);
        secondDown(0, 100, 50, 100);
        move(0, 60, 50, 60);
        up(30);
        assertTrue(pad.hasInertia());
        int count = events.size();
        down(0, 0, 40);
        up(80);
        assertFalse(pad.hasInertia());
        assertEquals(count, events.size());
        down(0, 0, 150);
        up(200);
        assertEquals("click:1", events.get(events.size() - 1));
    }
}
