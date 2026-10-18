package org.sucareto.androidhidkeyboard;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TouchpadGestureTest {
    private final List<String> events = new ArrayList<>();
    private final TouchpadGesture pad = new TouchpadGesture(5, 20, new TouchpadGesture.Output() {
        public void move(int x, int y) { events.add("move:" + x + "," + y); }
        public void scroll(int steps) { events.add("scroll:" + steps); }
        public void click(int button) { events.add("click:" + button); }
    });

    @Test public void tapAllowsSmallJitter() {
        pad.down(10, 10, 0);
        pad.move(12, 11, 0, 0);
        pad.up(100);
        assertEquals(Arrays.asList("click:1"), events);
    }

    @Test public void doubleTapSendsExactlyTwoClicks() {
        pad.down(0, 0, 0);
        pad.up(70);
        pad.down(0, 0, 130);
        pad.up(200);
        assertEquals(Arrays.asList("click:1", "click:1"), events);
    }

    @Test public void movementUsesRelativeDeltasAndDoesNotClick() {
        pad.down(10, 20, 0);
        pad.move(20, 15, 0, 0);
        pad.move(23, 14, 0, 0);
        pad.up(100);
        assertEquals(Arrays.asList("move:30,-15", "move:9,-3"), events);
    }

    @Test public void fractionalMovementIsPreservedAndNewTouchRebases() {
        pad.setSpeed(1);
        pad.down(0, 0, 0);
        pad.move(6, 0, 0, 0);
        pad.move(6.4f, 0, 0, 0);
        pad.move(6.8f, 0, 0, 0);
        pad.move(7.2f, 0, 0, 0);
        pad.up(100);
        pad.down(100, 100, 200);
        pad.move(106, 100, 0, 0);
        pad.up(300);
        assertEquals(Arrays.asList("move:6,0", "move:1,0", "move:6,0"), events);
    }

    @Test public void twoFingerTapWorksWhenEitherFingerLiftsFirst() {
        pad.down(10, 10, 0);
        pad.secondDown(10, 10, 50, 10);
        pad.pointerUp(50, 10);
        pad.move(51, 11, 0, 0);
        pad.up(100);
        assertEquals(Arrays.asList("click:2"), events);
    }

    @Test public void scrollingAccumulatesRemainderAndNeverMovesPointer() {
        pad.down(0, 100, 0);
        pad.secondDown(0, 100, 50, 100);
        pad.move(0, 70, 50, 70);
        pad.move(0, 60, 50, 60);
        pad.pointerUp(0, 60);
        pad.move(100, 0, 0, 0);
        pad.up(200);
        assertEquals(Arrays.asList("scroll:1", "scroll:1"), events);
    }

    @Test public void downwardScrollIsNegativeAndHorizontalSwipeDoesNotClick() {
        pad.down(0, 0, 0);
        pad.secondDown(0, 0, 50, 0);
        pad.move(0, 40, 50, 40);
        pad.up(100);
        assertEquals(Arrays.asList("scroll:-2"), events);
        events.clear();
        pad.down(0, 0, 200);
        pad.secondDown(0, 0, 50, 0);
        pad.move(40, 0, 90, 0);
        pad.up(300);
        assertTrue(events.isEmpty());
    }

    @Test public void remainingFingerMovementCancelsRightClick() {
        pad.down(0, 0, 0);
        pad.secondDown(0, 0, 50, 0);
        pad.pointerUp(50, 0);
        pad.move(70, 0, 0, 0);
        pad.up(100);
        assertTrue(events.isEmpty());
    }

    @Test public void addingFingerAfterMovementDoesNotRightClickOrJump() {
        pad.down(0, 0, 0);
        pad.move(10, 0, 0, 0);
        pad.secondDown(10, 0, 100, 100);
        pad.pointerUp(10, 0);
        pad.up(150);
        assertEquals(Arrays.asList("move:30,0"), events);
    }

    @Test public void longHoldDoesNotClick() {
        pad.down(0, 0, 0);
        pad.up(600);
        assertTrue(events.isEmpty());
    }

    @Test public void cancellationAndThreeFingersSuppressClicksUntilNextTouch() {
        pad.down(0, 0, 0);
        pad.cancel();
        pad.up(100);
        pad.down(0, 0, 200);
        pad.secondDown(0, 0, 50, 0);
        pad.block();
        pad.move(0, 80, 50, 80);
        pad.up(300);
        assertTrue(events.isEmpty());
        pad.down(0, 0, 400);
        pad.up(450);
        assertEquals(Arrays.asList("click:1"), events);
    }
}
