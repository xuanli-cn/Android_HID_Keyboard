package org.sucareto.androidhidkeyboard;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ScrollInertiaTest {
    @Test public void decaySlowsAndStopsWithBoundedDistance() {
        List<Integer> events = new ArrayList<>();
        ScrollInertia inertia = new ScrollInertia(events::add);
        inertia.start(0.08f, 0);
        for (int time = 16; time <= 1600; time += 16) inertia.advance(time);
        assertFalse(inertia.isRunning());
        int total = events.stream().mapToInt(Integer::intValue).sum();
        assertTrue(total > 10 && total < 25);
        assertTrue(events.get(0) >= events.get(events.size() - 1));
        for (int steps : events) assertTrue(steps > 0);
    }

    @Test public void negativeVelocityPreservesDirectionAndStopDiscardsRemainder() {
        List<Integer> events = new ArrayList<>();
        ScrollInertia inertia = new ScrollInertia(events::add);
        inertia.start(-0.08f, 0);
        inertia.advance(32);
        assertTrue(events.get(0) < 0);
        inertia.stop();
        int count = events.size();
        assertFalse(inertia.advance(100));
        assertEquals(count, events.size());
        inertia.start(0.001f, 200);
        assertFalse(inertia.isRunning());
    }

    @Test public void frameRateDoesNotMateriallyChangeDistance() {
        int[] total = new int[2];
        for (int i = 0; i < 2; i++) {
            final int index = i;
            ScrollInertia inertia = new ScrollInertia(steps -> total[index] += steps);
            inertia.start(0.05f, 0);
            int frameMillis = i == 0 ? 16 : 32;
            for (int time = frameMillis; time < 1500; time += frameMillis) inertia.advance(time);
        }
        assertTrue(Math.abs(total[0] - total[1]) <= 1);
    }

    @Test public void stalledFrameAndExtremeVelocityCannotProduceLargeBurst() {
        List<Integer> events = new ArrayList<>();
        ScrollInertia inertia = new ScrollInertia(events::add);
        inertia.start(100, 0);
        inertia.advance(500);
        assertTrue(events.get(0) <= 8);
        int count = events.size();
        assertFalse(inertia.advance(1300));
        assertEquals(count, events.size());
    }
}
