package org.sucareto.androidhidkeyboard;

import android.util.Log;

import com.topjohnwu.superuser.io.SuFile;
import com.topjohnwu.superuser.io.SuFileOutputStream;

import java.io.IOException;
import java.io.OutputStream;

public class HidController {
    public byte[] kCode = null;
    public byte[] mCode = null;
    private OutputStream kDev = null;
    private OutputStream mDev = null;

    public void UnInit() {
        if (kDev != null) {
            try {
                kDev.write(new byte[]{0, 0, 0, 0, 0, 0, 0, 0});
                kDev.close();
            } catch (IOException e) {
                Log.e("UnInit", String.valueOf(e));
            } finally {
                kDev = null;
            }
        }
        if (mDev != null) {
            try {
                mDev.write(new byte[]{0, 0, 0, 0});
                mDev.close();
            } catch (IOException e) {
                Log.e("UnInit", String.valueOf(e));
            } finally {
                mDev = null;
            }
        }
    }

    public boolean kInit() {
        kCode = new byte[]{0, 0, 0, 0, 0, 0, 0, 0};
        if (!SuFile.open("/dev/hidg0").exists()) {
            return true;
        }
        try {
            kDev = SuFileOutputStream.open(SuFile.open("/dev/hidg0"));
            kDev.write(kCode);
            return false;
        } catch (Exception e) {
            Log.e("kInit", String.valueOf(e));
            return true;
        }
    }

    public boolean mInit() {
        mCode = new byte[]{0, 0, 0, 0};
        if (!SuFile.open("/dev/hidg1").exists()) {
            return true;
        }
        try {
            mDev = SuFileOutputStream.open(SuFile.open("/dev/hidg1"));
            mDev.write(mCode);
            return false;
        } catch (Exception e) {
            Log.e("mInit", String.valueOf(e));
            return true;
        }
    }

    public void kSend() {
        if (kDev == null) return;
        try {
            kDev.write(kCode);
        } catch (Exception e) {
            Log.e("kSend", String.valueOf(e));
        }
    }

    public void kPress(byte code) {
        for (byte i = 2; i < 8; i++) {
            if (kCode[i] == 0) {
                kCode[i] = code;
                break;
            }
        }
        kSend();
    }

    public void kRelease(byte code) {
        for (byte i = 2; i < 8; i++) {
            if (kCode[i] == code) {
                kCode[i] = 0;
                break;
            }
        }
        kSend();
    }

    public void kPress_c(byte code) {
        kCode[0] += code;
        kSend();

    }

    public void kRelease_c(byte code) {
        kCode[0] -= code;
        kSend();
    }

    public void mSend() {
        if (mDev == null) return;
        try {
            mDev.write(mCode);
        } catch (Exception e) {
            Log.e("mSend", String.valueOf(e));
        }
        mCode[1] = 0;
        mCode[2] = 0;
        mCode[3] = 0;
    }

    public void mPress(byte code) {
        mCode[0] = (byte) (mCode[0] | code);
        mSend();

    }

    public void mRelease(byte code) {
        mCode[0] = (byte) (mCode[0] & ~code);
        mSend();
    }

    public void mReleaseAll() {
        if (mCode == null) return;
        mCode[0] = 0;
        mSend();
    }

    public void mMove(int x, int y) {
        while (x != 0 || y != 0) {
            int dx = Math.max(-127, Math.min(127, x));
            int dy = Math.max(-127, Math.min(127, y));
            mCode[1] = (byte) dx;
            mCode[2] = (byte) dy;
            mSend();
            x -= dx;
            y -= dy;
        }
    }

    public void mScroll(int steps) {
        while (steps != 0) {
            int chunk = Math.max(-127, Math.min(127, steps));
            mCode[3] = (byte) chunk;
            mSend();
            steps -= chunk;
        }
    }
}
