package com.fongmi.android.tv.ui.dialog;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Parse;
import com.fongmi.android.tv.databinding.DialogControlBinding;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.player.effect.audio.AudioEffectPreset;
import com.fongmi.android.tv.setting.AudioSetting;
import com.fongmi.android.tv.setting.SpeedSetting;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Timer;

/**
 * 播放设置（对齐影视仓胶囊直选风格）：解码/画面缩放/倍速播放/音效模式 + 片头/片尾/重播/刷新。
 * 选择项点选即生效；动作项转发给底部功能栏对应按钮（直播布局缺的行自动隐藏）。
 */
public class ControlDialog extends BaseBottomSheetDialog {

    private static final String ACTION_ARROW = "›";
    private static final int[] AUDIO_MODES = {AudioEffectPreset.OFF, AudioEffectPreset.NATURAL, AudioEffectPreset.SURROUND, AudioEffectPreset.VOCAL, AudioEffectPreset.CINEMA, AudioEffectPreset.BASS, AudioEffectPreset.TREBLE, AudioEffectPreset.POP, AudioEffectPreset.ROCK, AudioEffectPreset.DANCE, AudioEffectPreset.ELECTRONIC, AudioEffectPreset.JAZZ, AudioEffectPreset.CLASSICAL, AudioEffectPreset.CUSTOM};
    /** 胶囊视觉顺序 [硬解, 软解] 对应 select_decode 下标（0=软解 1=硬解） */
    private static final int[] DECODE_ORDER = {1, 0};

    private DialogControlBinding binding;
    private View parent;   // 底部功能栏根(点播/直播共用，按 id 查找，缺则隐藏对应行)
    private PlayerManager player;
    private boolean parse;

    public static ControlDialog create() {
        return new ControlDialog();
    }

    public ControlDialog parent(View parent) {
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

    /** 从底部功能栏按 id 取控件（直播/点播布局不同，缺的返回 null） */
    private @Nullable TextView tv(int id) {
        View v = parent != null ? parent.findViewById(id) : null;
        return v instanceof TextView ? (TextView) v : null;
    }

    private boolean visible(int id) {
        TextView t = tv(id);
        return t != null && t.getVisibility() == View.VISIBLE;
    }

    private static int indexOf(String[] items, CharSequence text) {
        for (int i = 0; i < items.length; i++) if (items[i].contentEquals(text)) return i;
        return -1;
    }

    private static boolean same(float a, float b) {
        return Math.abs(a - b) < 0.01f;
    }

    @Override
    protected void initView() {
        syncDecode();
        syncScale();
        syncSpeed();
        syncAudio();
        syncOpening();
        syncEnding();
        syncRepeat();
        // 动作行（点击执行，无状态值）右侧统一显示箭头
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
        // 胶囊直选：点选即生效
        setRowClick(binding.rowDecode, (row, i) -> onDecode(i));
        setRowClick(binding.rowScale, (row, i) -> onScale(i));
        setRowClick(binding.rowSpeed, (row, i) -> onSpeed(i));
        setRowClick(binding.rowAudio, (row, i) -> onAudio(i));
        // 标记项：点击在当前位置设点，长按清除（直播无 opening/ending 时对应行已隐藏）
        if (tv(R.id.opening) != null) {
            binding.opening.setOnClickListener(v -> onOpening());
            binding.opening.setOnLongClickListener(v -> longClickAction(R.id.opening));
        }
        if (tv(R.id.ending) != null) {
            binding.ending.setOnClickListener(v -> onEnding());
            binding.ending.setOnLongClickListener(v -> longClickAction(R.id.ending));
        }
        binding.repeat.setOnClickListener(v -> onRepeat());
        // 动作项：转发给底部功能栏对应按钮
        binding.reset.setOnClickListener(v -> clickAction(R.id.reset));
        binding.track.setOnClickListener(v -> clickAction(R.id.text));
        binding.danmaku.setOnClickListener(v -> clickAction(R.id.danmaku));
        binding.timer.setOnClickListener(v -> onTimer());
        binding.parse.setOnClickListener(v -> clickAction(R.id.parse));
        binding.edition.setOnClickListener(v -> clickAction(R.id.edition));
        binding.chapter.setOnClickListener(v -> clickAction(R.id.chapter));
        binding.audioSetting.setOnClickListener(v -> openAudioSetting());
        binding.videoSetting.setOnClickListener(v -> openVideoSetting());
        binding.rowParse.setOnClickListener(v -> clickAction(R.id.parse));
        binding.rowEdition.setOnClickListener(v -> clickAction(R.id.edition));
        binding.rowChapter.setOnClickListener(v -> clickAction(R.id.chapter));
        binding.rowTrack.setOnClickListener(v -> clickAction(R.id.text));
        binding.rowDanmaku.setOnClickListener(v -> clickAction(R.id.danmaku));
        binding.rowTimer.setOnClickListener(v -> onTimer());
    }

    private interface RowClick {

        void onClick(LinearLayoutCompat row, int index);
    }

    private void setRowClick(LinearLayoutCompat row, RowClick listener) {
        for (int i = 0; i < row.getChildCount(); i++) {
            int index = i;
            row.getChildAt(i).setOnClickListener(v -> listener.onClick(row, index));
        }
    }

    private void onDecode(int index) {
        TextView d = tv(R.id.decode);
        if (d == null) return;
        int current = indexOf(ResUtil.getStringArray(R.array.select_decode), d.getText());
        if (DECODE_ORDER[index] == current) return;
        d.performClick();// 底部栏解码按钮为硬/软切换
        syncDecode();
    }

    private void onScale(int index) {
        TextView s = tv(R.id.scale);
        if (s != null && index == indexOf(ResUtil.getStringArray(R.array.select_scale), s.getText())) return;
        if (getActivity() instanceof Listener listener) listener.onScale(index);
        syncScale();
    }

    private void onSpeed(int index) {
        float preset = SpeedSetting.getPresets()[index];
        if (same(preset, SpeedSetting.getPlayback())) return;
        SpeedSetting.putPlayback(preset);
        player.setSpeed(preset);
        syncSpeed();
    }

    private void onAudio(int index) {
        int preset = AUDIO_MODES[index];
        if (preset == AudioSetting.getPreset()) return;
        AudioSetting.putPreset(preset);
        player.setAudioSetting(preset);
        syncAudio();
    }

    private void onOpening() {
        TextView o = tv(R.id.opening);
        if (o != null) o.performClick();
        syncOpening();
    }

    private void onEnding() {
        TextView e = tv(R.id.ending);
        if (e != null) e.performClick();
        syncEnding();
    }

    private void onRepeat() {
        TextView r = tv(R.id.repeat);
        if (r != null) r.performClick();
        syncRepeat();
    }

    private void syncDecode() {
        TextView d = tv(R.id.decode);
        int current = d == null ? -1 : indexOf(ResUtil.getStringArray(R.array.select_decode), d.getText());
        for (int i = 0; i < binding.rowDecode.getChildCount(); i++) binding.rowDecode.getChildAt(i).setSelected(DECODE_ORDER[i] == current);
    }

    private void syncScale() {
        String[] items = ResUtil.getStringArray(R.array.select_scale);
        TextView s = tv(R.id.scale);
        int current = s == null ? -1 : indexOf(items, s.getText());
        for (int i = 0; i < binding.rowScale.getChildCount(); i++) {
            ((TextView) binding.rowScale.getChildAt(i)).setText(items[i]);
            binding.rowScale.getChildAt(i).setSelected(i == current);
        }
    }

    private void syncSpeed() {
        float[] presets = SpeedSetting.getPresets();
        float current = SpeedSetting.getPlayback();
        for (int i = 0; i < binding.rowSpeed.getChildCount() && i < presets.length; i++) {
            ((TextView) binding.rowSpeed.getChildAt(i)).setText(SpeedSetting.formatValue(presets[i]));
            binding.rowSpeed.getChildAt(i).setSelected(same(presets[i], current));
        }
    }

    private void syncAudio() {
        for (int i = 0; i < binding.rowAudio.getChildCount() && i < AUDIO_MODES.length; i++) {
            ((TextView) binding.rowAudio.getChildAt(i)).setText(getAudioModeText(AUDIO_MODES[i]));
            binding.rowAudio.getChildAt(i).setSelected(AUDIO_MODES[i] == AudioSetting.getPreset());
        }
    }

    private void syncOpening() {
        TextView o = tv(R.id.opening);
        if (o == null) return;
        binding.opening.setText(o.getText());
        binding.opening.setSelected(!o.getText().toString().contentEquals(getString(R.string.play_op)));
    }

    private void syncEnding() {
        TextView e = tv(R.id.ending);
        if (e == null) return;
        binding.ending.setText(e.getText());
        binding.ending.setSelected(!e.getText().toString().contentEquals(getString(R.string.play_ed)));
    }

    private void syncRepeat() {
        TextView r = tv(R.id.repeat);
        binding.repeat.setSelected(r != null && r.isSelected());
    }

    private void onTimer() {
        TimerDialog.create().show(getActivity());
        dismiss();
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

    private void setContentVisible() {
        boolean parse = this.parse && visible(R.id.parse);
        boolean edition = visible(R.id.edition);
        boolean chapter = visible(R.id.chapter);
        boolean track = visible(R.id.text) || visible(R.id.audio) || visible(R.id.video);
        boolean danmaku = visible(R.id.danmaku);
        boolean visible = parse || edition || chapter || track || danmaku;
        binding.groupContent.setVisibility(visible ? View.VISIBLE : View.GONE);
        binding.rowParse.setVisibility(parse ? View.VISIBLE : View.GONE);
        binding.rowEdition.setVisibility(edition ? View.VISIBLE : View.GONE);
        binding.rowChapter.setVisibility(chapter ? View.VISIBLE : View.GONE);
        binding.rowTrack.setVisibility(track ? View.VISIBLE : View.GONE);
        binding.rowDanmaku.setVisibility(danmaku ? View.VISIBLE : View.GONE);
    }

    /** 点击播放设置项：触发底部功能栏对应按钮并关闭弹窗（弹窗关闭后功能栏自行刷新文字） */
    private void clickAction(int id) {
        TextView t = tv(id);
        if (t != null) App.post(t::performClick, 200);
        dismiss();
    }

    private boolean longClickAction(int id) {
        TextView t = tv(id);
        if (t != null) t.performLongClick();
        return true;
    }

    public interface Listener {

        void onScale(int tag);

        void onParse(Parse item);
    }
}
