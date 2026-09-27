package com.fongmi.android.tv.ui.dialog;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Parse;
import com.fongmi.android.tv.databinding.ActivityVideoBinding;
import com.fongmi.android.tv.databinding.DialogControlBinding;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.player.effect.audio.AudioEffectPreset;
import com.fongmi.android.tv.setting.AudioSetting;
import com.fongmi.android.tv.setting.SpeedSetting;
import com.fongmi.android.tv.utils.Timer;

import java.util.Locale;

public class ControlDialog extends BaseBottomSheetDialog {

    private static final int[] AUDIO_MODES = {AudioEffectPreset.OFF, AudioEffectPreset.NATURAL, AudioEffectPreset.VOCAL, AudioEffectPreset.CINEMA, AudioEffectPreset.BASS, AudioEffectPreset.TREBLE, AudioEffectPreset.POP, AudioEffectPreset.ROCK, AudioEffectPreset.DANCE, AudioEffectPreset.ELECTRONIC, AudioEffectPreset.JAZZ, AudioEffectPreset.CLASSICAL, AudioEffectPreset.CUSTOM};

    private DialogControlBinding binding;
    private ActivityVideoBinding parent;
    private PlayerManager player;
    private boolean parse;

    public static ControlDialog create() {
        return new ControlDialog();
    }

    public ControlDialog parent(ActivityVideoBinding parent) {
        this.parent = parent;
        return this;
    }

    public ControlDialog parse(boolean parse) {
        this.parse = parse;
        return this;
    }

    public ControlDialog player(PlayerManager player) {
        this.player = player;
        return this;
    }

    public ControlDialog show(FragmentActivity activity) {
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) if (f instanceof ControlDialog) return this;
        show(activity.getSupportFragmentManager(), null);
        return this;
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = DialogControlBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        binding.player.setText(parent.control.action.player.getText());
        binding.decode.setText(parent.control.action.decode.getText());
        binding.scale.setText(parent.control.action.scale.getText());
        binding.speed.setText(formatSpeed(SpeedSetting.getPlayback()));
        binding.audio.setText(getAudioModeText(AudioSetting.getPreset()));
        binding.opening.setText(parent.control.action.opening.getText());
        binding.ending.setText(parent.control.action.ending.getText());
        setRepeatText();
        binding.timer.setText(getString(com.fongmi.android.tv.R.string.play_timer));
        setContentVisible();
    }

    @Override
    protected void initEvent() {
        binding.close.setOnClickListener(v -> dismiss());
        binding.player.setOnClickListener(v -> click(binding.player, parent.control.action.player));
        binding.decode.setOnClickListener(v -> click(binding.decode, parent.control.action.decode));
        binding.scale.setOnClickListener(v -> click(binding.scale, parent.control.action.scale));
        binding.speed.setOnClickListener(v -> dismiss(parent.control.action.speed));
        binding.speed.setOnLongClickListener(v -> onSpeedLong());
        binding.audio.setOnClickListener(v -> cycleAudio());
        binding.audio.setOnLongClickListener(v -> openAudioSetting());
        binding.opening.setOnClickListener(v -> click(binding.opening, parent.control.action.opening));
        binding.opening.setOnLongClickListener(v -> longClick(binding.opening, parent.control.action.opening));
        binding.ending.setOnClickListener(v -> click(binding.ending, parent.control.action.ending));
        binding.ending.setOnLongClickListener(v -> longClick(binding.ending, parent.control.action.ending));
        binding.repeat.setOnClickListener(v -> active(binding.repeat, parent.control.action.repeat));
        binding.reset.setOnClickListener(v -> dismiss(parent.control.action.reset));
        binding.parse.setOnClickListener(v -> dismiss(parent.control.action.parse));
        binding.edition.setOnClickListener(v -> dismiss(parent.control.action.edition));
        binding.chapter.setOnClickListener(v -> dismiss(parent.control.action.chapter));
        binding.track.setOnClickListener(v -> dismiss(parent.control.action.text));
        binding.danmaku.setOnClickListener(v -> dismiss(parent.control.action.danmaku));
        binding.timer.setOnClickListener(v -> onTimer());
        binding.audioSetting.setOnClickListener(v -> openAudioSetting());
        binding.videoSetting.setOnClickListener(v -> openVideoSetting());
    }

    private void onTimer() {
        TimerDialog.create().show(getActivity());
        dismiss();
    }

    private boolean onSpeedLong() {
        binding.speed.setText(formatSpeed(player.toggleSpeed()));
        return true;
    }

    private void cycleAudio() {
        int preset = nextAudioMode(AudioSetting.getPreset());
        AudioSetting.putPreset(preset);
        player.setAudioSetting(preset);
        binding.audio.setText(getAudioModeText(preset));
    }

    private boolean openAudioSetting() {
        AudioSettingDialog.create().show(getActivity());
        dismiss();
        return true;
    }

    private void openVideoSetting() {
        VideoSettingDialog.create().show(getActivity());
        dismiss();
    }

    private static int nextAudioMode(int preset) {
        for (int i = 0; i < AUDIO_MODES.length; i++) if (AUDIO_MODES[i] == preset) return AUDIO_MODES[(i + 1) % AUDIO_MODES.length];
        return AudioEffectPreset.NATURAL;
    }

    private String getAudioModeText(int preset) {
        return switch (preset) {
            case AudioEffectPreset.OFF -> getString(com.fongmi.android.tv.R.string.audio_mode_original);
            case AudioEffectPreset.NATURAL -> getString(com.fongmi.android.tv.R.string.audio_mode_natural);
            case AudioEffectPreset.VOCAL -> getString(com.fongmi.android.tv.R.string.audio_mode_vocal);
            case AudioEffectPreset.CINEMA -> getString(com.fongmi.android.tv.R.string.audio_mode_cinema);
            case AudioEffectPreset.BASS -> getString(com.fongmi.android.tv.R.string.audio_mode_bass);
            case AudioEffectPreset.TREBLE -> getString(com.fongmi.android.tv.R.string.audio_mode_treble);
            case AudioEffectPreset.POP -> getString(com.fongmi.android.tv.R.string.audio_mode_pop);
            case AudioEffectPreset.ROCK -> getString(com.fongmi.android.tv.R.string.audio_mode_rock);
            case AudioEffectPreset.DANCE -> getString(com.fongmi.android.tv.R.string.audio_mode_dance);
            case AudioEffectPreset.ELECTRONIC -> getString(com.fongmi.android.tv.R.string.audio_mode_electronic);
            case AudioEffectPreset.JAZZ -> getString(com.fongmi.android.tv.R.string.audio_mode_jazz);
            case AudioEffectPreset.CLASSICAL -> getString(com.fongmi.android.tv.R.string.audio_mode_classical);
            case AudioEffectPreset.CUSTOM -> getString(com.fongmi.android.tv.R.string.audio_mode_custom);
            default -> getString(com.fongmi.android.tv.R.string.audio_mode_original);
        };
    }

    private static String formatSpeed(float speed) {
        String s = String.format(Locale.US, "%.2f", speed);
        if (s.endsWith("00")) s = s.substring(0, s.length() - 3);
        else if (s.endsWith("0")) s = s.substring(0, s.length() - 1);
        return s + "x";
    }

    private void setRepeatText() {
        binding.repeat.setText(getString(parent.control.action.repeat.isSelected() ? com.fongmi.android.tv.R.string.control_on : com.fongmi.android.tv.R.string.control_off));
        binding.repeat.setSelected(parent.control.action.repeat.isSelected());
    }

    private void setContentVisible() {
        boolean parse = this.parse && parent.control.action.parse.getVisibility() == View.VISIBLE;
        boolean edition = parent.control.action.edition.getVisibility() == View.VISIBLE;
        boolean chapter = parent.control.action.chapter.getVisibility() == View.VISIBLE;
        boolean track = parent.control.action.text.getVisibility() == View.VISIBLE || parent.control.action.audio.getVisibility() == View.VISIBLE || parent.control.action.video.getVisibility() == View.VISIBLE;
        boolean danmaku = parent.control.action.danmaku.getVisibility() == View.VISIBLE;
        boolean visible = parse || edition || chapter || track || danmaku;
        binding.groupContent.setVisibility(visible ? View.VISIBLE : View.GONE);
        binding.rowParse.setVisibility(parse ? View.VISIBLE : View.GONE);
        binding.rowEdition.setVisibility(edition ? View.VISIBLE : View.GONE);
        binding.rowChapter.setVisibility(chapter ? View.VISIBLE : View.GONE);
        binding.rowTrack.setVisibility(track ? View.VISIBLE : View.GONE);
        binding.rowDanmaku.setVisibility(danmaku ? View.VISIBLE : View.GONE);
    }

    private void active(android.widget.TextView view, android.widget.TextView target) {
        target.performClick();
        view.setSelected(target.isSelected());
        if (view.getId() == binding.repeat.getId()) setRepeatText();
    }

    private void click(android.widget.TextView view, android.widget.TextView target) {
        target.performClick();
        view.setText(target.getText());
    }

    private boolean longClick(android.widget.TextView view, android.widget.TextView target) {
        target.performLongClick();
        view.setText(target.getText());
        return true;
    }

    private void dismiss(View view) {
        App.post(view::performClick, 200);
        dismiss();
    }

    public interface Listener {

        void onScale(int tag);

        void onParse(Parse item);
    }
}
