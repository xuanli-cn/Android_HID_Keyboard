package org.sucareto.androidhidkeyboard;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import org.sucareto.androidhidkeyboard.databinding.ActivityMouseBinding;

public class Mouse extends AppCompatActivity {
    private static final String SHOW_SHORTCUTS = "show_shortcuts";
    private static final String SHOW_BUTTONS = "show_buttons";
    private static final String MOVE_SPEED = "move_speed";
    private static final String REVERSE_SCROLL = "reverse_scroll";
    private static final String SCROLL_SPEED = "scroll_speed";
    private static final String SCROLL_INERTIA = "scroll_inertia";
    private static final String POINTER_ACCELERATION = "pointer_acceleration";
    private final HidController hid = new HidController();
    private TouchpadGesture gestures;
    private ActivityMouseBinding binding;
    private SharedPreferences preferences;
    private boolean showingSettings;
    private int manualButtons, gestureButtons;
    private final Runnable scrollFrame = new Runnable() {
        @Override
        public void run() {
            if (gestures.advanceInertia(SystemClock.uptimeMillis())) {
                binding.touchArea.postOnAnimation(this);
            }
        }
    };

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMouseBinding.inflate(getLayoutInflater());
        preferences = getSharedPreferences("mouse_settings", MODE_PRIVATE);
        setContentView(binding.getRoot());
        findViewById(R.id.BtnBack).setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS);
            if (showingSettings) showSettings(false);
            else startActivity(new Intent(Mouse.this, Keyboard.class));
        });
        binding.openSettings.setOnClickListener(v -> showSettings(true));
        binding.toggleShortcuts.setOnClickListener(v -> {
            stopScrollAnimation();
            gestures.cancel();
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
            public void press(int button) {
                gestureButtons |= button;
                sendMouseButtons();
                binding.touchArea.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            }

            @Override
            public void release(int button) {
                gestureButtons &= ~button;
                sendMouseButtons();
            }

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
                case MotionEvent.ACTION_DOWN -> {
                    binding.touchArea.removeCallbacks(scrollFrame);
                    gestures.down(event.getX(), event.getY(), event.getEventTime());
                }
                case MotionEvent.ACTION_POINTER_DOWN -> {
                    if (event.getPointerCount() == 2) {
                        gestures.secondDown(event.getX(0), event.getY(0), event.getX(1), event.getY(1), event.getEventTime());
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
                    binding.touchArea.removeCallbacks(scrollFrame);
                    if (gestures.hasInertia()) binding.touchArea.postOnAnimation(scrollFrame);
                }
                case MotionEvent.ACTION_CANCEL -> {
                    stopScrollAnimation();
                    gestures.cancel();
                    releaseMouseButtons();
                }
            }
            return true;
        });
        binding.sbMouseMoveSpeed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                gestures.setSpeed(progress);
                binding.speedValue.setText(getString(R.string.mouse_speed_value, progress));
                if (fromUser) preferences.edit().putInt(MOVE_SPEED, progress).apply();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        binding.sbScrollSpeed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                gestures.setScrollSpeed(progress);
                binding.scrollSpeedValue.setText(getString(R.string.scroll_speed_value, progress));
                if (fromUser) preferences.edit().putInt(SCROLL_SPEED, progress).apply();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        binding.showShortcutButton.setChecked(preferences.getBoolean(SHOW_SHORTCUTS, true));
        binding.showMouseButtons.setChecked(preferences.getBoolean(SHOW_BUTTONS, true));
        binding.reverseScroll.setChecked(preferences.getBoolean(REVERSE_SCROLL, false));
        binding.scrollInertia.setChecked(preferences.getBoolean(SCROLL_INERTIA, false));
        binding.pointerAcceleration.setChecked(preferences.getBoolean(POINTER_ACCELERATION, false));
        binding.sbMouseMoveSpeed.setProgress(preferences.getInt(MOVE_SPEED, 3));
        binding.speedValue.setText(getString(R.string.mouse_speed_value, binding.sbMouseMoveSpeed.getProgress()));
        binding.sbScrollSpeed.setProgress(preferences.getInt(SCROLL_SPEED, 3));
        binding.scrollSpeedValue.setText(getString(R.string.scroll_speed_value, binding.sbScrollSpeed.getProgress()));
        applySettings();
        binding.showShortcutButton.setOnCheckedChangeListener((button, checked) -> {
            preferences.edit().putBoolean(SHOW_SHORTCUTS, checked).apply();
            applySettings();
        });
        binding.showMouseButtons.setOnCheckedChangeListener((button, checked) -> {
            preferences.edit().putBoolean(SHOW_BUTTONS, checked).apply();
            applySettings();
        });
        binding.reverseScroll.setOnCheckedChangeListener((button, checked) -> {
            preferences.edit().putBoolean(REVERSE_SCROLL, checked).apply();
            applySettings();
        });
        binding.scrollInertia.setOnCheckedChangeListener((button, checked) -> {
            preferences.edit().putBoolean(SCROLL_INERTIA, checked).apply();
            applySettings();
        });
        binding.pointerAcceleration.setOnCheckedChangeListener((button, checked) -> {
            preferences.edit().putBoolean(POINTER_ACCELERATION, checked).apply();
            applySettings();
        });
        if (savedInstanceState != null) showSettings(savedInstanceState.getBoolean("showing_settings"));
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (showingSettings) showSettings(false);
                else finish();
            }
        });
    }

    private void applySettings() {
        boolean shortcuts = preferences.getBoolean(SHOW_SHORTCUTS, true);
        binding.toggleShortcuts.setVisibility(shortcuts ? View.VISIBLE : View.GONE);
        if (!shortcuts) {
            binding.shortcuts.setVisibility(View.GONE);
            binding.toggleShortcuts.setText(R.string.show_shortcuts);
        }
        binding.mouseButtons.setVisibility(preferences.getBoolean(SHOW_BUTTONS, true) ? View.VISIBLE : View.GONE);
        gestures.setSpeed(binding.sbMouseMoveSpeed.getProgress());
        gestures.setReverseScroll(preferences.getBoolean(REVERSE_SCROLL, false));
        gestures.setScrollSpeed(binding.sbScrollSpeed.getProgress());
        gestures.setInertiaEnabled(preferences.getBoolean(SCROLL_INERTIA, false));
        gestures.setAcceleration(preferences.getBoolean(POINTER_ACCELERATION, false));
    }

    private void showSettings(boolean show) {
        stopScrollAnimation();
        gestures.cancel();
        // Navigation may be tapped while another finger still holds a mouse/shortcut key.
        releaseMouseButtons();
        for (View button : new View[]{binding.mBtn1, binding.mBtn2, binding.mBtn3,
                binding.mBtn4, binding.mBtn5, binding.mBtn6}) {
            if (hid.kCode != null) hid.kRelease((byte) Integer.parseInt(button.getTag().toString(), 16));
            button.setPressed(false);
        }
        showingSettings = show;
        binding.touchpadPanel.setVisibility(show ? View.GONE : View.VISIBLE);
        binding.settingsPanel.setVisibility(show ? View.VISIBLE : View.GONE);
        binding.openSettings.setEnabled(!show);
        binding.toggleShortcuts.setEnabled(!show);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean("showing_settings", showingSettings);
        super.onSaveInstanceState(outState);
    }

    private void updateGesture(MotionEvent event) {
        gestures.move(event.getX(0), event.getY(0),
                event.getPointerCount() > 1 ? event.getX(1) : 0,
                event.getPointerCount() > 1 ? event.getY(1) : 0, event.getEventTime());
    }

    private void stopScrollAnimation() {
        binding.touchArea.removeCallbacks(scrollFrame);
        gestures.stopInertia();
    }

    private void sendMouseButtons() {
        if (hid.mCode == null) return;
        hid.mCode[0] = (byte) (manualButtons | gestureButtons);
        hid.mSend();
    }

    private void releaseMouseButtons() {
        manualButtons = gestureButtons = 0;
        hid.mReleaseAll();
        for (View button : new View[]{binding.mouseLeft, binding.mouseMiddle, binding.mouseRight}) {
            button.setPressed(false);
        }
    }

    private boolean handleButton(View view, MotionEvent event, boolean mouse) {
        byte code = (byte) Integer.parseInt(view.getTag().toString(), 16);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN -> {
                stopScrollAnimation();
                view.setPressed(true);
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS);
                if (mouse) {
                    manualButtons |= code;
                    sendMouseButtons();
                } else hid.kPress(code);
            }
            case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                view.setPressed(false);
                if (mouse) {
                    manualButtons &= ~code;
                    sendMouseButtons();
                } else hid.kRelease(code);
                if (event.getActionMasked() == MotionEvent.ACTION_UP) view.performClick();
            }
        }
        return true;
    }

    @Override
    protected void onPause() {
        stopScrollAnimation();
        gestures.cancel();
        releaseMouseButtons();
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
