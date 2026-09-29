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
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Parse;
import com.fongmi.android.tv.databinding.ActivityVideoBinding;
import com.fongmi.android.tv.databinding.DialogControlBinding;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.player.effect.audio.AudioEffectPreset;
import com.fongmi.android.tv.setting.AudioSetting;
import com.fongmi.android.tv.setting.SpeedSetting;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Timer;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Locale;

public class ControlDialog extends BaseBottomSheetDialog {

    private static final String ACTION_ARROW = "›";
    private static final int[] AUDIO_MODES = {AudioEffectPreset.OFF, AudioEffectPreset.NATURAL, AudioEffectPreset.SURROUND, AudioEffectPreset.VOCAL, AudioEffectPreset.CINEMA, AudioEffectPreset.BASS, AudioEffectPreset.TREBLE, AudioEffectPreset.POP, AudioEffectPreset.ROCK, AudioEffectPreset.DANCE, AudioEffectPreset.ELECTRONIC, AudioEffectPreset.JAZZ, AudioEffectPreset.CLASSICAL, AudioEffectPreset.CUSTOM};

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
        // 动作行（点击执行，无状态值）右侧统一显示箭头
        binding.reset.setText(ACTION_ARROW);
        binding.track.setText(ACTION_ARROW);
        binding.timer.setText(ACTION_ARROW);
        binding.parse.setText(ACTION_ARROW);
        binding.edition.setText(ACTION_ARROW);
        binding.chapter.setText(ACTION_ARROW);
        binding.danmaku.setText(ACTION_ARROW);
        binding.audioSetting.setText(ACTION_ARROW);
        binding.videoSetting.setText(ACTION_ARROW);
        setContentVisible();
    }

    @Override
    protected void initEvent() {
        binding.close.setOnClickListener(v -> dismiss());
        // 选择项：点按弹窗选择，不再点一下切一档
        binding.player.setOnClickListener(v -> dismiss(parent.control.action.player));
        binding.decode.setOnClickListener(v -> showDecodeDialog());
        binding.scale.setOnClickListener(v -> showScaleDialog());
        binding.speed.setOnClickListener(v -> dismiss(parent.control.action.speed));
        binding.speed.setOnLongClickListener(v -> onSpeedLong());
        binding.audio.setOnClickListener(v -> showAudioDialog());
        binding.audio.setOnLongClickListener(v -> openAudioSetting());
        binding.repeat.setOnClickListener(v -> showRepeatDialog());
        // 标记项：点击在当前位置设点，长按清除
        binding.opening.setOnClickListener(v -> click(binding.opening, parent.control.action.opening));
        binding.opening.setOnLongClickListener(v -> longClick(binding.opening, parent.control.action.opening));
        binding.ending.setOnClickListener(v -> click(binding.ending, parent.control.action.ending));
        binding.ending.setOnLongClickListener(v -> longClick(binding.ending, parent.control.action.ending));
        // 动作项：转发给底部功能栏对应按钮，整行可点
        binding.reset.setOnClickListener(v -> dismiss(parent.control.action.reset));
        binding.track.setOnClickListener(v -> dismiss(parent.control.action.text));
        binding.danmaku.setOnClickListener(v -> dismiss(parent.control.action.danmaku));
        binding.timer.setOnClickListener(v -> onTimer());
        binding.parse.setOnClickListener(v -> dismiss(parent.control.action.parse));
        binding.edition.setOnClickListener(v -> dismiss(parent.control.action.edition));
        binding.chapter.setOnClickListener(v -> dismiss(parent.control.action.chapter));
        binding.audioSetting.setOnClickListener(v -> openAudioSetting());
        binding.videoSetting.setOnClickListener(v -> openVideoSetting());
        binding.rowParse.setOnClickListener(v -> dismiss(parent.control.action.parse));
        binding.rowEdition.setOnClickListener(v -> dismiss(parent.control.action.edition));
        binding.rowChapter.setOnClickListener(v -> dismiss(parent.control.action.chapter));
        binding.rowTrack.setOnClickListener(v -> dismiss(parent.control.action.text));
        binding.rowDanmaku.setOnClickListener(v -> dismiss(parent.control.action.danmaku));
        binding.rowTimer.setOnClickListener(v -> onTimer());
    }

    private void onTimer() {
        TimerDialog.create().show(getActivity());
        dismiss();
    }

    private boolean onSpeedLong() {
        binding.speed.setText(formatSpeed(player.toggleSpeed()));
        return true;
    }

    private void showDecodeDialog() {
        String[] items = ResUtil.getStringArray(R.array.select_decode);
        int checked = getCheckedIndex(items, binding.decode);
        new MaterialAlertDialogBuilder(requireActivity()).setTitle(getString(R.string.setting_decode)).setSingleChoiceItems(items, checked, (dialog, which) -> {
            dialog.dismiss();
            if (which == checked) return;
            parent.control.action.decode.performClick();
            binding.decode.setText(parent.control.action.decode.getText());
        }).show();
    }

    private void showScaleDialog() {
        String[] items = ResUtil.getStringArray(R.array.select_scale);
        int checked = getCheckedIndex(items, binding.scale);
        new MaterialAlertDialogBuilder(requireActivity()).setTitle(getString(R.string.setting_scale)).setSingleChoiceItems(items, checked, (dialog, which) -> {
            dialog.dismiss();
            if (which == checked) return;
            if (getActivity() instanceof Listener listener) listener.onScale(which);
            binding.scale.setText(items[which]);
        }).show();
    }

    private void showAudioDialog() {
        String[] items = new String[AUDIO_MODES.length];
        int found = 0;
        for (int i = 0; i < AUDIO_MODES.length; i++) {
            items[i] = getAudioModeText(AUDIO_MODES[i]);
            if (AUDIO_MODES[i] == AudioSetting.getPreset()) found = i;
        }
        final int checked = found;
        new MaterialAlertDialogBuilder(requireActivity()).setTitle(getString(R.string.setting_audio_mode)).setSingleChoiceItems(items, checked, (dialog, which) -> {
            dialog.dismiss();
            if (which == checked) return;
            int preset = AUDIO_MODES[which];
            AudioSetting.putPreset(preset);
            player.setAudioSetting(preset);
            binding.audio.setText(getAudioModeText(preset));
        }).show();
    }

    private void showRepeatDialog() {
        String[] items = {getString(R.string.control_off), getString(R.string.control_on)};
        boolean selected = parent.control.action.repeat.isSelected();
        new MaterialAlertDialogBuilder(requireActivity()).setTitle(getString(R.string.setting_repeat)).setSingleChoiceItems(items, selected ? 1 : 0, (dialog, which) -> {
            dialog.dismiss();
            boolean target = which == 1;
            if (target == selected) return;
            parent.control.action.repeat.performClick();
            setRepeatText();
        }).show();
    }

    private int getCheckedIndex(String[] items, android.widget.TextView view) {
        CharSequence text = view.getText();
        for (int i = 0; i < items.length; i++) if (items[i].contentEquals(text)) return i;
        return 0;
    }

    private boolean openAudioSetting() {
        AudioSettingDialog.create().player(player).show(getActivity());
        dismiss();
        return true;
    }

    private void openVideoSetting() {
        VideoSettingDialog.create().player(player).show(getActivity());
        dismiss();
    }

    private String getAudioModeText(int preset) {
        return switch (preset) {
            case AudioEffectPreset.OFF -> getString(R.string.audio_mode_original);
            case AudioEffectPreset.NATURAL -> getString(R.string.audio_mode_natural);
            case AudioEffectPreset.SURROUND -> getString(R.string.audio_effect_surround);
            case AudioEffectPreset.VOCAL -> getString(R.string.audio_mode_vocal);
            case AudioEffectPreset.CINEMA -> getString(R.string.audio_mode_cinema);
            case AudioEffectPreset.BASS -> getString(R.string.audio_mode_bass);
            case AudioEffectPreset.TREBLE -> getString(R.string.audio_mode_treble);
            case AudioEffectPreset.POP -> getString(R.string.audio_mode_pop);
            case AudioEffectPreset.ROCK -> getString(R.string.audio_mode_rock);
            case AudioEffectPreset.DANCE -> getString(R.string.audio_mode_dance);
            case AudioEffectPreset.ELECTRONIC -> getString(R.string.audio_mode_electronic);
            case AudioEffectPreset.JAZZ -> getString(R.string.audio_mode_jazz);
            case AudioEffectPreset.CLASSICAL -> getString(R.string.audio_mode_classical);
            case AudioEffectPreset.CUSTOM -> getString(R.string.audio_mode_custom);
            default -> getString(R.string.audio_mode_original);
        };
    }

    private static String formatSpeed(float speed) {
        String s = String.format(Locale.US, "%.2f", speed);
        if (s.endsWith("00")) s = s.substring(0, s.length() - 3);
        else if (s.endsWith("0")) s = s.substring(0, s.length() - 1);
        return s + "x";
    }

    private void setRepeatText() {
        binding.repeat.setText(getString(parent.control.action.repeat.isSelected() ? R.string.control_on : R.string.control_off));
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
