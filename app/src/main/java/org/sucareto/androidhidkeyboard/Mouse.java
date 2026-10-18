package org.sucareto.androidhidkeyboard;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.sucareto.androidhidkeyboard.databinding.ActivityMouseBinding;

public class Mouse extends AppCompatActivity {
    private final HidController hid = new HidController();
    private TouchpadGesture gestures;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityMouseBinding binding = ActivityMouseBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        findViewById(R.id.BtnBack).setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS);
            startActivity(new Intent(Mouse.this, Keyboard.class));
        });
        binding.toggleShortcuts.setOnClickListener(v -> {
            boolean show = binding.shortcuts.getVisibility() != View.VISIBLE;
            binding.shortcuts.setVisibility(show ? View.VISIBLE : View.GONE);
            binding.toggleShortcuts.setText(show ? R.string.hide_shortcuts : R.string.show_shortcuts);
        });
        for (int i = 1; i < 7; i++) {
            int id = getResources().getIdentifier("mBtn" + i, "id", getPackageName());
            findViewById(id).setOnTouchListener((v, event) -> handleButton(v, event, false));
        }
        for (View button : new View[]{binding.mouseLeft, binding.mouseMiddle, binding.mouseRight}) {
            button.setOnTouchListener((v, event) -> handleButton(v, event, true));
        }
        gestures = new TouchpadGesture(ViewConfiguration.get(this).getScaledTouchSlop(),
                20 * getResources().getDisplayMetrics().density, new TouchpadGesture.Output() {
            @Override
            public void move(int x, int y) { hid.mMove(x, y); }

            @Override
            public void scroll(int steps) { hid.mScroll(steps); }

            @Override
            public void click(int button) {
                // A separate on-screen button may already be held for dragging.
                if ((hid.mCode[0] & button) != 0) return;
                hid.mPress((byte) button);
                hid.mRelease((byte) button);
                binding.touchArea.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                binding.touchArea.performClick();
            }
        });
        binding.touchArea.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN -> gestures.down(event.getX(), event.getY(), event.getEventTime());
                case MotionEvent.ACTION_POINTER_DOWN -> {
                    if (event.getPointerCount() == 2) {
                        gestures.secondDown(event.getX(0), event.getY(0), event.getX(1), event.getY(1));
                    } else {
                        gestures.block();
                    }
                }
                case MotionEvent.ACTION_MOVE -> updateGesture(event);
                case MotionEvent.ACTION_POINTER_UP -> {
                    updateGesture(event);
                    int remaining = event.getActionIndex() == 0 ? 1 : 0;
                    gestures.pointerUp(event.getX(remaining), event.getY(remaining));
                }
                case MotionEvent.ACTION_UP -> {
                    updateGesture(event);
                    gestures.up(event.getEventTime());
                }
                case MotionEvent.ACTION_CANCEL -> {
                    gestures.cancel();
                    hid.mReleaseAll();
                }
            }
            return true;
        });
        binding.sbMouseMoveSpeed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                gestures.setSpeed(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void updateGesture(MotionEvent event) {
        gestures.move(event.getX(0), event.getY(0),
                event.getPointerCount() > 1 ? event.getX(1) : 0,
                event.getPointerCount() > 1 ? event.getY(1) : 0);
    }

    private boolean handleButton(View view, MotionEvent event, boolean mouse) {
        byte code = (byte) Integer.parseInt(view.getTag().toString(), 16);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN -> {
                view.setPressed(true);
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS);
                if (mouse) hid.mPress(code); else hid.kPress(code);
            }
            case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                view.setPressed(false);
                if (mouse) hid.mRelease(code); else hid.kRelease(code);
                if (event.getActionMasked() == MotionEvent.ACTION_UP) view.performClick();
            }
        }
        return true;
    }

    @Override
    protected void onPause() {
        gestures.cancel();
        hid.mReleaseAll();
        hid.UnInit();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (hid.kInit() | hid.mInit()) {
            Toast.makeText(this, R.string.msg_e_hid, Toast.LENGTH_LONG).show();
        }
    }
}
