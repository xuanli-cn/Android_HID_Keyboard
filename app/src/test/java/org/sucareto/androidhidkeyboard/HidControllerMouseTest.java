package org.sucareto.androidhidkeyboard;

import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class HidControllerMouseTest {
    private final HidController hid = new HidController();
    private final ByteArrayOutputStream reports = new ByteArrayOutputStream();

    @Before public void connectFakeDevice() throws Exception {
        hid.mCode = new byte[4];
        Field device = HidController.class.getDeclaredField("mDev");
        device.setAccessible(true);
        device.set(hid, reports);
    }

    @Test public void duplicatePressAndReleaseDoNotCorruptOtherButtons() {
        hid.mPress((byte) 1);
        hid.mPress((byte) 1);
        hid.mPress((byte) 2);
        hid.mRelease((byte) 1);
        hid.mRelease((byte) 1);
        assertEquals(2, hid.mCode[0]);
        hid.mReleaseAll();
        assertEquals(0, hid.mCode[0]);
    }

    @Test public void largeMovementSplitsReportsAndPreservesHeldButton() {
        hid.mPress((byte) 1);
        reports.reset();
        hid.mMove(300, -260);
        assertArrayEquals(new byte[]{1, 127, -127, 0, 1, 127, -127, 0, 1, 46, -6, 0},
                reports.toByteArray());
        assertArrayEquals(new byte[]{1, 0, 0, 0}, hid.mCode);
    }

    @Test public void wheelSplitsReportsAndDoesNotRepeatOnClick() {
        hid.mScroll(-130);
        hid.mPress((byte) 2);
        hid.mRelease((byte) 2);
        assertArrayEquals(new byte[]{0, 0, 0, -127, 0, 0, 0, -3,
                2, 0, 0, 0, 0, 0, 0, 0}, reports.toByteArray());
    }

    @Test public void stoppingReleasesHeldButtons() {
        hid.mPress((byte) 1);
        reports.reset();
        hid.UnInit();
        assertArrayEquals(new byte[4], reports.toByteArray());
    }
}
