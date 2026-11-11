package com.fongmi.android.tv.ui.activity;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.transition.ChangeBounds;
import androidx.transition.TransitionManager;
import androidx.viewbinding.ViewBinding;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.transition.Transition;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.DanmakuApi;
import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Danmaku;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Keep;
import com.fongmi.android.tv.bean.Parse;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.ActivityVideoBinding;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.event.CastEvent;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.impl.CustomTarget;
import com.fongmi.android.tv.model.VideoViewModel;
import com.fongmi.android.tv.playback.PlaybackAction;
import com.fongmi.android.tv.playback.PlaybackIntent;
import com.fongmi.android.tv.playback.PlaybackOrientation;
import com.fongmi.android.tv.playback.PlaybackReset;
import com.fongmi.android.tv.playback.PlaybackResult;
import com.fongmi.android.tv.playback.vod.VodDetailResult;
import com.fongmi.android.tv.playback.vod.VodPlayRequest;
import com.fongmi.android.tv.playback.vod.VodPlaybackController;
import com.fongmi.android.tv.playback.vod.VodPlaybackHost;
import com.fongmi.android.tv.playback.vod.VodPlaybackMedia;
import com.fongmi.android.tv.player.media.PlaySpec;
import com.fongmi.android.tv.service.PlaybackService;
import com.fongmi.android.tv.setting.DanmakuSetting;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.setting.SpeedSetting;
import com.fongmi.android.tv.ui.adapter.EpisodeAdapter;
import com.fongmi.android.tv.ui.adapter.FlagAdapter;
import com.fongmi.android.tv.ui.adapter.QualityAdapter;
import com.fongmi.android.tv.ui.adapter.QuickAdapter;
import com.fongmi.android.tv.ui.base.ViewType;
import com.fongmi.android.tv.ui.custom.CustomKeyDown;
import com.fongmi.android.tv.ui.custom.CustomSeekView;
import com.fongmi.android.tv.ui.custom.CustomMovement;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.ui.dialog.CastDialog;
import com.fongmi.android.tv.ui.dialog.ChapterDialog;
import com.fongmi.android.tv.ui.dialog.ControlDialog;
import com.fongmi.android.tv.ui.dialog.DanmakuDialog;
import com.fongmi.android.tv.ui.dialog.EditionDialog;
import com.fongmi.android.tv.ui.dialog.EpisodeGridDialog;
import com.fongmi.android.tv.ui.dialog.EpisodeListDialog;
import com.fongmi.android.tv.ui.dialog.InfoDialog;
import com.fongmi.android.tv.ui.dialog.ParseDialog;
import com.fongmi.android.tv.ui.dialog.PlayerEngineDialog;
import com.fongmi.android.tv.ui.dialog.ReceiveDialog;
import com.fongmi.android.tv.ui.dialog.SpeedSettingDialog;
import com.fongmi.android.tv.ui.dialog.TrackDialog;
import com.fongmi.android.tv.utils.Clock;
import com.fongmi.android.tv.utils.Douban;
import com.fongmi.android.tv.utils.FileChooser;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.PiP;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Sniffer;
import com.fongmi.android.tv.utils.Timer;
import com.fongmi.android.tv.utils.UrlUtil;
import com.fongmi.android.tv.utils.Util;
import com.fongmi.android.tv.utils.VodTheme;
import com.fongmi.android.tv.ui.dialog.GlassAlertDialogBuilder;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class VideoActivity extends PlaybackActivity implements Clock.Callback, CustomKeyDown.Listener, ControlDialog.Listener, ParseDialog.Listener, VodPlaybackHost, FlagAdapter.OnClickListener, EpisodeAdapter.OnClickListener, QualityAdapter.OnClickListener, QuickAdapter.OnClickListener, CastDialog.Listener, InfoDialog.Listener {

    private static final int KEEP_GOLD = 0xFFF2C35B;
    private static final int KEEP_GOLD_ON = 0xFF241A08;

    private ActivityVideoBinding mBinding;
    private VideoViewModel mViewModel;
    private VodPlaybackController mVod;
    private FlagAdapter mFlagAdapter;
    private EpisodeAdapter mEpisodeAdapter;
    private QualityAdapter mQualityAdapter;
    private Result mQualityResult;
    private QuickAdapter mQuickAdapter;
    /** 首次进入详情页时暂存的播放请求：等用户点「播放」才真正发起 */
    private VodPlayRequest mDeferredRequest;
    /** 用户是否已主动要求播放（点播放/选集/切线路/切画质），一旦为 true 不再延后 */
    private boolean mPlayConfirmed;
    /** 点击兜底：DOWN 命中的操作按钮 / 本次手势是否已被按钮自己的 OnClickListener 处理 */
    private View mFallbackTarget;
    private boolean mActionHandled;
    /** 同一按钮 300ms 内只放行一次；收藏是 toggle，重复触发会「收藏→取消」互相抵消 */
    private View mLastActionTarget;
    private long mLastActionTime;
    private long mLastKeepTime;
    private ViewGroup.LayoutParams mFrameParams;
    private ValueAnimator mAnimator;
    private CustomKeyDown mKeyDown;
    private Clock mClock;
    private PiP mPiP;
    private Runnable mR1;
    private Runnable mR3;
    private Runnable mR4;
    private Runnable mR6;
    private PlaybackOsdDelegate mOsd;
    private EpisodeScrollDelegate mEpisodeScroll;
    private History mHistory;
    private String mPlayTitle = "";
    private VodTheme mTheme;
    private String mTypeName;
    private boolean mDeferredShown;
    private ValueAnimator mDeferredAnimator;
    /** 详情态/播放态的目标布局，过渡期间以它为准，不靠 View 可见性反复判定 */
    private boolean mDetailMode;
    /** 过渡期间锁定的播放器高度：动画终点和结束态布局共用，避免差一截 */
    private int mLockedVideoHeight = -1;
    /** 过渡期间上报的真实视频尺寸先记下，动画结束再平滑应用 */
    private int mPendingVideoHeight = -1;
    private boolean fullscreen;
    private boolean useParse;
    private boolean rotate;

    public static void push(FragmentActivity activity, String text) {
        Uri uri = UrlUtil.uri(text);
        if (FileChooser.isValid(activity, uri)) file(activity, FileChooser.getPathFromUri(uri));
        else start(activity, Sniffer.getUrl(text));
    }

    public static void file(FragmentActivity activity, String path) {
        if (TextUtils.isEmpty(path)) return;
        String name = new File(path).getName();
        start(activity, SiteApi.PUSH, "file://" + path, name);
    }

    /** Compatibility overload for dialogs/callers that still pass a Uri. */
    public static void file(FragmentActivity activity, Uri fileUri) {
        if (fileUri == null || activity.isFinishing() || activity.isDestroyed()) return;
        start(activity, SiteApi.PUSH, fileUri.toString(), FileUtil.getDisplayName(fileUri));
    }

    public static void cast(Activity activity, History history) {
        Intent intent = new Intent(activity, VideoActivity.class);
        intent.putExtra("collect", false);
        intent.putExtra("name", history.getVodName());
        intent.putExtra("pic", history.getVodPic());
        intent.putExtra("key", history.getSiteKey());
        intent.putExtra("id", history.getVodId());
        intent.putExtra("history", history.toString());
        activity.startActivity(intent);
    }

    public static void collect(Activity activity, String key, String id, String name, String pic) {
        start(activity, key, id, name, pic, null, true);
    }

    public static void start(Activity activity, String url) {
        start(activity, SiteApi.PUSH, url, url);
    }

    public static void start(Activity activity, String key, String id, String name) {
        start(activity, key, id, name, null);
    }

    public static void start(Activity activity, String key, String id, String name, String pic) {
        start(activity, key, id, name, pic, null);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark) {
        start(activity, key, id, name, pic, mark, false);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark, boolean collect) {
        Intent intent = new Intent(activity, VideoActivity.class);
        intent.putExtra("collect", collect);
        intent.putExtra("mark", mark);
        intent.putExtra("name", name);
        intent.putExtra("pic", pic);
        intent.putExtra("key", key);
        intent.putExtra("id", id);
        activity.startActivity(intent);
    }

    private String getName() {
        return Objects.toString(getIntent().getStringExtra("name"), "");
    }

    private String getPic() {
        return Objects.toString(getIntent().getStringExtra("pic"), "");
    }

    private String getMark() {
        return Objects.toString(getIntent().getStringExtra("mark"), "");
    }

    private String getKey() {
        return Objects.toString(getIntent().getStringExtra("key"), "");
    }

    private String getId() {
        return Objects.toString(getIntent().getStringExtra("id"), "");
    }

    private boolean isSameVideo(Intent intent) {
        String key = Objects.toString(intent.getStringExtra("key"), "");
        String id = Objects.toString(intent.getStringExtra("id"), "");
        return TextUtils.equals(key, getKey()) && TextUtils.equals(id, getId());
    }

    @Override
    public String getHistoryKey() {
        return getKey().concat(AppDatabase.SYMBOL).concat(getId()).concat(AppDatabase.SYMBOL) + VodConfig.getCid();
    }

    @Override
    public History takeCastHistory() {
        History history = History.objectFrom(getIntent().getStringExtra("history"));
        getIntent().removeExtra("history");
        return history;
    }

    private Site getSite() {
        return VodConfig.get().getSite(getKey());
    }

    private int getScale() {
        return mHistory != null && mHistory.getScale() != -1 ? mHistory.getScale() : PlayerSetting.getScale();
    }

    private void setScale(int scale) {
        mVod.setScale(scale);
        mBinding.player.setResizeMode(scale);
        mBinding.control.action.scale.setText(ResUtil.getStringArray(R.array.select_scale)[scale]);
    }

    @Override
    public boolean isFromCollect() {
        return getIntent().getBooleanExtra("collect", false);
    }

    private boolean isAutoRotate() {
        return Settings.System.getInt(getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 0) == 1;
    }

    private boolean isLand() {
        return mBinding.getRoot().getTag().equals("land");
    }

    private boolean isPort() {
        return mBinding.getRoot().getTag().equals("port");
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityVideoBinding.inflate(getLayoutInflater());
    }

    @Override
    protected PlaybackService.NavigationCallback getNavigationCallback() {
        return mNavigationCallback;
    }

    @Override
    protected PlayerView getPlayerView() {
        return mBinding.player;
    }

    @Override
    protected CustomSeekView getSeekView() {
        return mBinding.control.seek;
    }

    @Override
    protected void onServiceConnected() {
        mVod.onPlaybackServiceReady();
        checkLand();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (TextUtils.isEmpty(intent.getStringExtra("id")) || (isSameVideo(intent) && !intent.hasExtra("history"))) return;
        mBinding.swipeLayout.setRefreshing(true);
        saveHistory(true);
        mVod.reset();
        setIntent(intent);
        updateNavigationKey();
        checkControl();
        setOrient();
        checkId();
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        super.initView(savedInstanceState);
        ViewCompat.setOnApplyWindowInsetsListener(mBinding.getRoot(), (v, insets) -> setStatusBar(insets));
        mKeyDown = CustomKeyDown.create(this, mBinding.player);
        mFrameParams = mBinding.video.getLayoutParams();
        mBinding.progressLayout.showProgress();
        mBinding.swipeLayout.setEnabled(false);
        mClock = Clock.create();
        mR1 = this::hideControl;
        mR3 = this::setOrient;
        mR4 = this::showEmpty;
        mR6 = this::runLock;
        mOsd = new PlaybackOsdDelegate(this, mBinding, this::isFullscreen);
        mEpisodeScroll = new EpisodeScrollDelegate(this, mBinding);
        mPiP = new PiP();
        checkDanmakuImg();
        setRecyclerView();
        setVideoView();
        setViewModel();
        applyTheme(VodTheme.from(VodTheme.NEUTRAL_SEED));
        // 「先详情后播放」时立刻摆好详情态骨架，别等站点数据回来才切换
        if (shouldDeferAutoPlay()) prepareDeferredDetail();
        else showProgress();
        setAnimator();
        checkId();
    }

    private void setClick(View view, View.OnClickListener listener) {
        if (view != null) view.setOnClickListener(listener);
    }

    private void setLongClick(View view, View.OnLongClickListener listener) {
        if (view != null) view.setOnLongClickListener(listener);
    }

    private void setTouch(View view, View.OnTouchListener listener) {
        if (view != null) view.setOnTouchListener(listener);
    }

    /**
     * 操作按钮埋在 SwipeRefreshLayout → ProgressLayout → NestedScrollView → 内容容器深处，
     * 详情态又叠了三块全屏盖层；部分 ROM 的触摸分发顺序会让按钮「看得见却收不到事件」。
     * 根 View 的 OnTouchListener 只在「没有子 View 消费 DOWN」时才会调用，而 NestedScrollView
     * 几乎总会消费 DOWN，等于形同虚设。改到 Activity 这一层：DOWN 记下按钮后照常分发，
     * UP 时若按钮自己没收到点击（mActionHandled 仍为 false）就按原坐标补触发一次。
     * 两种路径互斥，不会重复执行。
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            mFallbackTarget = findActionAt((int) event.getRawX(), (int) event.getRawY());
            mActionHandled = false;
        }
        boolean handled = super.dispatchTouchEvent(event);
        if (mFallbackTarget != null) {
            if (action == MotionEvent.ACTION_UP) {
                if (!mActionHandled && findActionAt((int) event.getRawX(), (int) event.getRawY()) == mFallbackTarget) triggerAction(mFallbackTarget);
                mFallbackTarget = null;
            } else if (action == MotionEvent.ACTION_CANCEL) {
                mFallbackTarget = null;
            }
        }
        return handled;
    }

    private void triggerAction(View target) {
        // 同一个按钮 300ms 内只放行一次：收藏是 toggle，重复触发会「加入→取消」互相抵消，
        // 表现就是「图标没变化，却提示已取消收藏」。
        long now = SystemClock.uptimeMillis();
        if (target == mLastActionTarget && now - mLastActionTime < 300) return;
        mLastActionTarget = target;
        mLastActionTime = now;
        if (target == mBinding.deferredPlay) onDeferredPlay(target);
        else if (target == mBinding.keepBtn) onKeep();
        else if (target == mBinding.quickBtn) onName();
        else if (target == mBinding.castBtn) onCast();
        else if (target == mBinding.settingBtn) onSetting();
        else onPlayer(); // 平板 action_bar 的「播放器」入口
    }

    /** 所有操作按钮统一走 triggerAction，并打标记告诉兜底「这次点击按钮自己收到了」 */
    private void setActionClick(View view) {
        if (view == null) return;
        view.setOnClickListener(v -> { mActionHandled = true; triggerAction(v); });
    }

    /** 用屏幕绝对坐标做命中测试，避开滚动偏移与各层坐标系换算 */
    private View findActionAt(int x, int y) {
        if (mBinding == null) return null;
        View[] views = {
                mBinding.deferredPlay, mBinding.keepBtn, mBinding.quickBtn,
                mBinding.castBtn, mBinding.settingBtn
        };
        Rect rect = new Rect();
        int[] loc = new int[2];
        for (View view : views) {
            if (view == null || view.getVisibility() != View.VISIBLE || !view.isEnabled()) continue;
            view.getLocationOnScreen(loc);
            rect.set(loc[0], loc[1], loc[0] + view.getWidth(), loc[1] + view.getHeight());
            if (rect.contains(x, y)) return view;
        }
        return null;
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    protected void initEvent() {
        setClick(mBinding.name, view -> onName());
        setClick(mBinding.more, view -> onMore());
        setActionClick(mBinding.keepBtn);
        setClick(mBinding.detailBack, view -> onBack());
        setClick(mBinding.actor, view -> onActor());
        setClick(mBinding.content, view -> onContent());
        setClick(mBinding.reverse, view -> onReverse());
        setClick(mBinding.director, view -> onDirector());
        setActionClick(mBinding.quickBtn);
        setActionClick(mBinding.deferredPlay);
        setActionClick(mBinding.settingBtn);
        setLongClick(mBinding.name, view -> onChange());
        setLongClick(mBinding.content, view -> onCopy());
        setClick(mBinding.control.back, view -> onBack());
        setActionClick(mBinding.castBtn);
        applyTextShadow();
        setClick(mBinding.control.info, view -> onInfo());
        setClick(mBinding.control.cast, view -> onCast());
        setClick(mBinding.control.play, view -> checkPlay());
        setClick(mBinding.control.next, view -> checkNext());
        setClick(mBinding.control.prev, view -> checkPrev());
        setLongClick(mBinding.control.title, view -> onChange());
        setClick(mBinding.control.right.lock, view -> onLock());
        setClick(mBinding.control.right.rotate, view -> onRotate());
        setClick(mBinding.control.danmaku, view -> onDanmakuShow());
        setClick(mBinding.control.action.text, this::onTrack);
        setClick(mBinding.control.action.audio, this::onTrack);
        setClick(mBinding.control.action.video, this::onTrack);
        setClick(mBinding.control.action.scale, view -> onScale());
        setClick(mBinding.control.action.speed, view -> onSpeed());
        setClick(mBinding.control.action.reset, view -> onReset());
        setClick(mBinding.control.action.replay, view -> onReplay());
        setClick(mBinding.control.action.parse, view -> onParse());
        setClick(mBinding.control.action.player, view -> onPlayer());
        setClick(mBinding.control.action.decode, view -> onDecode());
        setClick(mBinding.control.action.ending, view -> onEnding());
        setClick(mBinding.control.action.repeat, view -> onRepeat());
        setClick(mBinding.control.action.opening, view -> onOpening());
        setClick(mBinding.control.action.danmaku, view -> onDanmaku());
        setClick(mBinding.control.action.edition, view -> onEdition());
        setClick(mBinding.control.action.chapter, view -> onChapter());
        setClick(mBinding.control.action.episodes, view -> onEpisodes());
        setClick(mBinding.control.action.qualityPick, this::onQualityPick);
        setLongClick(mBinding.control.action.speed, view -> onSpeedLong());
        setLongClick(mBinding.control.action.ending, view -> onEndingReset());
        setLongClick(mBinding.control.action.opening, view -> onOpeningReset());
        setTouch(mBinding.video, (view, event) -> mKeyDown.onTouchEvent(event));
        setTouch(mBinding.control.action.getRoot(), this::onActionTouch);
        mBinding.swipeLayout.setOnRefreshListener(this::onSwipeRefresh);
        mBinding.scroll.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener) (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            updateDetailDim(scrollY);
            updateHeaderScroll(scrollY);
        });
    }

    private WindowInsetsCompat setStatusBar(WindowInsetsCompat insets) {
        int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
        ViewGroup.LayoutParams lp = mBinding.statusBar.getLayoutParams();
        lp.height = top;
        mBinding.statusBar.setLayoutParams(lp);
        return insets;
    }

    private void setRecyclerView() {
        mBinding.flag.setHasFixedSize(true);
        mBinding.flag.setItemAnimator(null);
        mBinding.flag.addItemDecoration(new SpaceItemDecoration(8));
        mBinding.flag.setAdapter(mFlagAdapter = new FlagAdapter(this));
        mBinding.quick.setAdapter(mQuickAdapter = new QuickAdapter(this));
        mBinding.episode.setHasFixedSize(true);
        mBinding.episode.setItemAnimator(null);
        // 平板：多列网格，放不下自动换行，绝不横向滚动
        if (isLand()) mBinding.episode.setLayoutManager(new androidx.recyclerview.widget.GridLayoutManager(this, 6));
        mBinding.episode.setAdapter(mEpisodeAdapter = new EpisodeAdapter(this, ViewType.GRID));
        mBinding.quality.setHasFixedSize(true);
        mBinding.quality.setItemAnimator(null);
        mBinding.quality.addItemDecoration(new SpaceItemDecoration(8));
        mBinding.quality.setAdapter(mQualityAdapter = new QualityAdapter(this));
        mEpisodeScroll.setAdapter(mEpisodeAdapter);
        // 平板整屏剧照详情：选集、线路排成横向分带（TMDB 海报式布局），手机保持原样
        if (isLand()) setHorizontal(mBinding.flag);
    }

    private void setHorizontal(RecyclerView view) {
        RecyclerView.LayoutManager lm = view.getLayoutManager();
        if (lm instanceof androidx.recyclerview.widget.LinearLayoutManager)
            ((androidx.recyclerview.widget.LinearLayoutManager) lm).setOrientation(RecyclerView.HORIZONTAL);
    }

    private void setVideoView() {
        PlayerEngineDialog.setText(mBinding.control.action.player);
        mBinding.control.action.danmaku.setVisibility(DanmakuSetting.isLoad() ? View.VISIBLE : View.GONE);
        mBinding.video.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> mPiP.update(this, view));
    }

    private void setVideoView(boolean isInPictureInPictureMode) {
        if (isInPictureInPictureMode) {
            mBinding.video.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
        } else {
            mBinding.video.setLayoutParams(mFrameParams);
        }
    }

    private void setAnimator() {
        mAnimator = new ValueAnimator();
        mAnimator.setInterpolator(new DecelerateInterpolator());
        mAnimator.addUpdateListener(animation -> {
            if (isFullscreen() || isInPictureInPictureMode()) return;
            mFrameParams.height = (int) animation.getAnimatedValue();
            mBinding.video.setLayoutParams(mFrameParams);
        });
    }

    private void setPlaybackMode() {
        PlaybackAction.setPlaybackMode(player(), mBinding.control.action.player, mBinding.control.action.decode);
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(VideoViewModel.class);
        mVod = mViewModel.createPlaybackController(this);
        observeWhenServiceReady(mViewModel.getDetail(), this::onDetailObserved);
        observeWhenServiceReady(mViewModel.getSearch(), this::onSearchObserved);
        observeWhenServiceReady(mViewModel.getPreload(), this::onPreloadObserved);
        observeWhenServiceReady(mViewModel.getPlayback(), this::onPlaybackObserved);
    }

    private void onDetailObserved(VodDetailResult result) {
        mVod.onDetailResult(result);
    }

    private void onPlaybackObserved(PlaybackResult<VodPlayRequest> result) {
        mVod.onPlaybackResult(result);
    }

    private void onPreloadObserved(PlaybackResult<VodPlayRequest> result) {
        mVod.onPreloadResult(result);
    }

    private void onSearchObserved(Result result) {
        mVod.onSearchResult(result);
    }

    @Override
    public String getVodKey() {
        return getKey();
    }

    @Override
    public String getVodId() {
        return getId();
    }

    @Override
    public void setVodId(String id) {
        getIntent().putExtra("id", id);
    }

    @Override
    public String getVodName() {
        String name = mBinding.name.getText().toString();
        return name.isEmpty() ? getName() : name;
    }

    @Override
    public String getVodPic() {
        return getPic();
    }

    @Override
    public String getVodMark() {
        return getMark();
    }

    @Override
    public boolean isSiteChangeable() {
        return getSite().isChangeable();
    }

    @Override
    public boolean isHostFinishing() {
        return isFinishing() || isDestroyed();
    }

    @Override
    public boolean isPlayerEmpty() {
        return player().isEmpty();
    }

    @Override
    public boolean hasPlaybackSession() {
        return service() != null && isOwner() && player().hasPlaySpec();
    }

    @Override
    public boolean isFullscreenForPlayback() {
        return isFullscreen();
    }

    @Override
    public boolean isLivePlayback() {
        return service() != null && isOwner() && player().isLive();
    }

    @Override
    public boolean canTrackPlaybackProgress() {
        return service() != null && isOwner() && player().isVod();
    }

    @Override
    public boolean canPreloadNext() {
        return service() != null && isOwner() && player().canPreloadNext();
    }

    @Override
    public long getPlayerPosition() {
        return player().getPosition();
    }

    @Override
    public long getPlayerDuration() {
        return player().getDuration();
    }

    @Override
    public void usePushId(String id) {
        getIntent().putExtra("key", SiteApi.PUSH).putExtra("id", id);
    }

    @Override
    public void requestDetail(String key, String id) {
        mViewModel.detailContent(key, id);
    }

    @Override
    public void requestPlayer(VodPlayRequest request) {
        mDeferredRequest = request;
        if (shouldDeferAutoPlay()) {
            // 停在详情页：不发播放请求，先让用户看清简介和剧集，点播放才起播
            renderDeferredPlay(true);
            return;
        }
        renderDeferredPlay(false);
        mViewModel.playerContent(request);
        showProgress();
    }

    /** 只在「首次进入详情页 + 用户没主动操作 + 设置未开启进入即播放」时延后起播 */
    private boolean shouldDeferAutoPlay() {
        return mBinding != null && mBinding.deferredCover != null && !mPlayConfirmed && !PlayerSetting.isAutoPlay() && !isFullscreenForPlayback();
    }

    /**
     * 站点详情还没返回时先把详情态骨架摆好。否则这段时间页面仍是默认布局：
     * 顶部一块黑色播放器占位 + 0 KB/s 转圈，下面是壁纸 + 另一个转圈，
     * 要等 3~4 秒数据回来才突然切成剧照详情页。
     */
    private void prepareDeferredDetail() {
        renderDeferredPlay(true);
        hideProgress();                        // 还没起播，播放器区的网速转圈没有意义
        mBinding.progressLayout.showContent(); // 内容区也不转圈，数据回来直接填文字
        mBinding.name.setText(getName());
        checkKeepImg();   // 详情态骨架一铺好就同步收藏状态，别等站点数据回来
        applyTextShadow();
        showPlaceholderBackdrop();
    }

    /** 先用列表页带过来的海报铺底（Glide 内存缓存通常命中，几乎瞬间显示），高清剧照随后替换 */
    private void showPlaceholderBackdrop() {
        if (mBinding == null || mBinding.deferredCover == null) return;
        String pic = getPic();
        if (TextUtils.isEmpty(pic)) return;
        List<String> chain = new ArrayList<>();
        String original = ImgUtil.original(pic);
        if (!original.equals(pic)) chain.add(original);
        chain.add(pic);
        ImgUtil.loadWithFallback(getName(), chain, mBinding.deferredCover);
        ImgUtil.load(pic, new CustomTarget<Bitmap>() {
            @Override
            public void onResourceReady(@NonNull Bitmap resource, Transition<? super Bitmap> transition) {
                VodTheme.extract(resource, theme -> {
                    if (!isFinishing() && !isDestroyed()) applyTheme(theme);
                });
            }

            @Override
            public void onLoadCleared(@Nullable Drawable placeholder) {
            }
        });
    }

    private void renderDeferredPlay(boolean visible) {
        if (mBinding == null || mBinding.deferredCover == null || mBinding.deferredPlay == null) return;
        boolean phone = mBinding.deferredCover.getParent() != mBinding.video;
        boolean first = !mDeferredShown;
        // 手机同态重复请求（点播放后播放链路会连着回调多次）只刷新按钮文案，绝不再动布局。
        // 否则会在过渡动画中途改写 padding 和 BELOW 规则，与逐帧 padding 互相覆盖，
        // 表现就是下方详情/选集「跳一下」。
        if (phone && mDeferredShown && mDetailMode == visible) {
            if (visible && mBinding.deferredPlay != null) mBinding.deferredPlay.setText(playLabel());
            return;
        }
        mDeferredShown = true;
        mDetailMode = visible;
        if (phone && !first) {
            animateDeferred(visible);
            return;
        }
        applyDeferredState(visible);
    }

    /** 详情↔播放双向过渡：盖层交叉淡入淡出 + 内容首屏留白连续变化，避免布局硬切 */
    private void animateDeferred(boolean toDetail) {
        if (mDeferredAnimator != null) {
            ValueAnimator old = mDeferredAnimator;
            mDeferredAnimator = null; // 旧动画的结束回调随之失效，不抢占新动画状态
            old.cancel();
            mLockedVideoHeight = -1;
            mPendingVideoHeight = -1;
        }
        // 过渡期间锁定播放器高度：动画终点与结束态布局共用同一个值，
        // 视频轨稍后上报的真实比例延到动画结束后平滑应用。
        mLockedVideoHeight = targetVideoHeight();
        setVideoHeight();
        View content = mBinding.detailContent;
        int padDetail = detailTopPadding();
        int padPlay = mLockedVideoHeight + ResUtil.dp2px(36);
        int from = toDetail ? padPlay : content.getPaddingTop();
        int to = toDetail ? padDetail : padPlay;
        if (toDetail) {
            applyDeferredState(true);
            content.setPadding(content.getPaddingLeft(), from, content.getPaddingRight(), content.getPaddingBottom());
            setDeferredAlpha(0f);
        }
        ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(300);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            setDeferredAlpha(toDetail ? t : 1f - t);
            content.setPadding(content.getPaddingLeft(), (int) (from + (to - from) * t), content.getPaddingRight(), content.getPaddingBottom());
        });
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (mDeferredAnimator != animation) return;
                mDeferredAnimator = null;
                setDeferredAlpha(1f);
                applyDeferredState(toDetail);
                mLockedVideoHeight = -1;
                int height = mPendingVideoHeight > 0 ? mPendingVideoHeight : (toDetail ? -1 : targetVideoHeight());
                mPendingVideoHeight = -1;
                if (height > 0) applyVideoHeight(height, true);
            }
        });
        mDeferredAnimator = anim;
        anim.start();
    }

    private void setDeferredAlpha(float alpha) {
        mBinding.deferredCover.setAlpha(alpha);
        mBinding.deferredPlay.setAlpha(alpha);
        if (mBinding.detailScrim != null) mBinding.detailScrim.setAlpha(alpha);
        for (View layer : new View[]{mBinding.topScrim, mBinding.detailBack}) {
            if (layer != null) layer.setAlpha(alpha);
        }
    }

    private void applyDeferredState(boolean visible) {
        // 手机是整屏剧照，平板只在 400x225 视频窗内铺剧照、保留右侧信息分栏
        if (mBinding == null || mBinding.deferredCover == null || mBinding.deferredPlay == null) return;
        mBinding.deferredPlay.setVisibility(visible ? View.VISIBLE : View.GONE);
        mBinding.deferredCover.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (mBinding.topScrim != null) mBinding.topScrim.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (mBinding.detailBack != null) mBinding.detailBack.setVisibility(visible ? View.VISIBLE : View.GONE);
        // 详情态把剧照铺到状态栏后面，图标保持白色，靠顶部渐变保证在亮剧照上也看得清
        setLightStatusBar(false);
        // 剧照铺满整屏，播放器区和状态栏占位都要让开，否则会压出黑边
        mBinding.video.setBackgroundColor(visible ? Color.TRANSPARENT : Color.BLACK);
        mBinding.statusBar.setBackgroundColor(visible ? Color.TRANSPARENT : Color.BLACK);
        if (visible) mBinding.deferredPlay.setText(playLabel());
        // 剧照整屏不透明铺底，淡出交给上层 scrim 做平滑过渡，避免半张图 + 硬线
        mBinding.deferredCover.setFadeEnabled(false);
        mBinding.deferredCover.setTranslationY(0f);
        if (mBinding.detailScrim != null) mBinding.detailScrim.setTranslationY(0f);
        // 手机布局的剧照是根布局子 View，平板布局的剧照在 video 内部 —— 用这个区分两者，
        // 避免把平板视频窗一起隐藏，也避免对左右分栏做无意义的 Z 顺序调整。
        boolean coverInsideVideo = mBinding.deferredCover.getParent() == mBinding.video;
        // 详情态播放器占位（XML clickable=true + OnTouchListener）透明留在原位仍会吞点击，
        // 直接整块隐藏；起播后恢复。平板不隐藏。
        mBinding.video.setClickable(!visible);
        if (!coverInsideVideo) mBinding.video.setVisibility(visible ? View.INVISIBLE : View.VISIBLE);
        for (View layer : new View[]{mBinding.deferredCover, mBinding.detailScrim, mBinding.detailDim}) {
            if (layer == null) continue;
            layer.setClickable(false);
            layer.setLongClickable(false);
            layer.setFocusable(false);
            layer.setFocusableInTouchMode(false);
        }
        // 内容层压过所有盖层：Z 顺序 + elevation 双保险（部分 ROM 按 elevation 排触摸顺序）
        if (!coverInsideVideo) {
            if (visible) {
                mBinding.swipeLayout.bringToFront();
                // 悬浮返回要留在内容层之上，否则被整屏滚动区盖住收不到点击
                if (mBinding.detailBack != null) mBinding.detailBack.bringToFront();
            }
            ViewCompat.setElevation(mBinding.swipeLayout, visible ? ResUtil.dp2px(4) : 0f);
            // 触摸分发按 Z 排序：悬浮按钮 elevation 必须高于整屏滚动区，否则视觉在上、点击却被滚动区吞掉
            if (mBinding.detailBack != null) ViewCompat.setElevation(mBinding.detailBack, visible ? ResUtil.dp2px(8) : 0f);
        }
        // 注意：detailActions 是 detailContent（垂直 LinearLayout）的子 View，
        // 对它调用 bringToFront() 会把它挪到子 View 末尾（选集列表之后）而不是只改层级 ——
        // 四个按钮会整行从首屏消失。按钮的点击由 Activity 层兜底保证，这里只确保可见。
        if (mBinding.detailActions != null) mBinding.detailActions.setVisibility(View.VISIBLE);
        for (View button : new View[]{mBinding.deferredPlay, mBinding.keepBtn, mBinding.quickBtn, mBinding.castBtn, mBinding.settingBtn}) {
            if (button == null) continue;
            button.setEnabled(true);
            button.setClickable(true);
        }
        // 先定稿播放器高度，再排下方内容；反过来会按旧高度摆一次、再改高度，出现一帧错位
        setVideoHeight();
        setDetailLayout(visible);
    }

    /** 继续播放时把集数也带上，用户不用点进选集确认播哪一集 */
    private CharSequence playLabel() {
        if (mHistory == null || mHistory.getPosition() <= 0) return getString(R.string.play_deferred);
        String text = getString(R.string.play_deferred_continue);
        String episode = mHistory.getVodRemarks();
        if (!TextUtils.isEmpty(episode) && !episode.equals(getName())) text = text + " · " + episode;
        return text;
    }

    private void setLightStatusBar(boolean light) {
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (controller != null) controller.setAppearanceLightStatusBars(light);
    }

    /** 剧照当整屏背景时，标题/评分等白字叠在图上，加一层深色投影保证任何画质下都看得清 */
    private void applyTextShadow() {
        if (mTheme != null) return;
        for (TextView view : new TextView[]{mBinding.name, mBinding.other}) {
            if (view != null) view.setShadowLayer(6f, 0f, 1f, 0x99000000);
        }
    }

    /** 头图随内容一起滚走，露出页面底色；scrim 跟着图走，渐变收敛始终贴在图底 */
    private void updateHeaderScroll(int scrollY) {
        if (mTheme == null || mBinding.deferredCover == null || mBinding.deferredCover.getVisibility() != View.VISIBLE) return;
        if (mBinding.deferredCover.getParent() == mBinding.video) return;
        mBinding.deferredCover.setTranslationY(-scrollY);
        if (mBinding.detailScrim != null) mBinding.detailScrim.setTranslationY(-scrollY);
        if (mBinding.topScrim != null) {
            float range = Math.max(1f, ResUtil.getScreenHeight() * 0.42f);
            mBinding.topScrim.setAlpha(Math.max(0f, 1f - scrollY / range));
        }
    }

    private void applyTheme(VodTheme theme) {
        mTheme = theme;
        mBinding.getRoot().setBackgroundColor(theme.surface);
        if (mBinding.detailScrim != null) mBinding.detailScrim.setBackground(theme.headerScrim());
        if (mBinding.detailDim != null) mBinding.detailDim.setVisibility(View.GONE);
        for (TextView view : new TextView[]{mBinding.name, mBinding.headerContent, mBinding.headerFlag, mBinding.headerEpisode, mBinding.headerQuick, mBinding.qualityText}) {
            if (view != null) {
                view.setTextColor(theme.onSurface);
                view.setShadowLayer(0f, 0f, 0f, 0);
            }
        }
        for (TextView view : new TextView[]{mBinding.other, mBinding.content, mBinding.director, mBinding.actor}) {
            if (view != null) {
                view.setTextColor(theme.onVariant);
                view.setShadowLayer(0f, 0f, 0f, 0);
            }
        }
        if (mBinding.reverse instanceof TextView) ((TextView) mBinding.reverse).setTextColor(theme.onVariant);
        for (View icon : new View[]{mBinding.iconContent, mBinding.iconFlag, mBinding.iconEpisode, mBinding.iconQuick, mBinding.more}) {
            if (icon instanceof android.widget.ImageView) ((android.widget.ImageView) icon).setImageTintList(ColorStateList.valueOf(theme.onSurface));
        }
        mBinding.deferredPlay.setBackgroundTintList(ColorStateList.valueOf(theme.accent));
        mBinding.deferredPlay.setTextColor(theme.onAccent);
        mBinding.deferredPlay.setIconTint(ColorStateList.valueOf(theme.onAccent));
        for (android.widget.ImageView button : new android.widget.ImageView[]{mBinding.quickBtn, mBinding.castBtn, mBinding.settingBtn}) {
            if (button == null) continue;
            button.setBackground(theme.actionCircle());
            button.setImageTintList(ColorStateList.valueOf(theme.onSurface));
        }
        if (mBinding.keepBtn instanceof android.widget.ImageView) {
            android.widget.ImageView keep = (android.widget.ImageView) mBinding.keepBtn;
            keep.setBackground(theme.actionCircle());
            keep.setImageTintList(ColorStateList.valueOf(theme.onSurface));
        }
        if (mBinding.detailBack != null) {
            mBinding.detailBack.setBackground(theme.floatingCircle());
            mBinding.detailBack.setImageTintList(ColorStateList.valueOf(Color.WHITE));
        }
        if (mEpisodeAdapter != null) mEpisodeAdapter.setTheme(theme);
        if (mFlagAdapter != null) mFlagAdapter.setTheme(theme);
        if (mQualityAdapter != null) mQualityAdapter.setTheme(theme);
        if (mQuickAdapter != null) mQuickAdapter.setTheme(theme);
        checkKeepImg();
    }

    /**
     * 详情态把详情滚动区从「播放器下方」抬到整屏：内容靠顶部留白先落在剧照下半部，
     * 向上滑时可以一直铺满全屏，不会像以前那样被播放器下沿那条线裁掉。
     * 起播后恢复 layout_below=video，播放器正常占位。
     */
    private void setDetailLayout(boolean deferred) {
        if (mBinding.detailScrim == null || mBinding.detailDim == null || mBinding.detailContent == null) return;
        ViewGroup.LayoutParams lp = mBinding.swipeLayout.getLayoutParams();
        if (lp instanceof RelativeLayout.LayoutParams) {
            RelativeLayout.LayoutParams rlp = (RelativeLayout.LayoutParams) lp;
            int below = rlp.getRule(RelativeLayout.BELOW);
            if (deferred && below == R.id.video) {
                rlp.removeRule(RelativeLayout.BELOW);
                mBinding.swipeLayout.setLayoutParams(rlp);
            } else if (!deferred && below != R.id.video) {
                rlp.addRule(RelativeLayout.BELOW, R.id.video);
                mBinding.swipeLayout.setLayoutParams(rlp);
            }
        }
        int top = deferred ? detailTopPadding() : ResUtil.dp2px(36);
        View content = mBinding.detailContent;
        content.setPadding(content.getPaddingLeft(), top, content.getPaddingRight(), content.getPaddingBottom());
        mBinding.detailScrim.setVisibility(deferred ? View.VISIBLE : View.GONE);
        mBinding.detailDim.setVisibility(deferred && mTheme == null ? View.VISIBLE : View.GONE);
        if (deferred) {
            mBinding.scroll.scrollTo(0, 0);
            updateDetailDim(0);
        } else {
            mBinding.detailDim.setAlpha(0f);
        }
    }

    /** 详情态内容起点，横屏留出更多剧照；过渡动画与结束态布局必须共用这个口径 */
    private int detailTopPadding() {
        return (int) (ResUtil.getScreenHeight() * (isLand() ? 0.30f : 0.42f));
    }

    /** 内容越往上滑、越贴到清晰剧照区，整体压暗越重，白字始终看得清 */
    private void updateDetailDim(int scrollY) {
        if (mTheme != null) return;
        if (mBinding == null || mBinding.detailDim == null || mBinding.detailDim.getVisibility() != View.VISIBLE) return;
        float range = Math.max(1f, ResUtil.getScreenHeight() * 0.42f);
        float ratio = Math.min(1f, Math.max(0f, scrollY / range));
        // 首屏只压 6%（原 15% 会让整张低分辨率海报发灰）；上滑后逐步加深保证白字可读
        mBinding.detailDim.setAlpha(0.06f + 0.50f * ratio);
    }

    /** 剧照已经整屏，详情态不再抬高播放器占位。
     *  窗口尺寸优先跟随已知分辨率：播放器已报尺寸 > 已选视频轨的分辨率 > 16:9 兜底。
     *  这样缓冲阶段就能定到和起播后一致的大小，不会「加载时宽一截、起播后跳一下」。
     *  平板已改为整屏剧照详情，同样按此规则定尺寸。 */
    private void setVideoHeight() {
        if (isFullscreenForPlayback()) return;
        if (mAnimator != null && mAnimator.isRunning()) return; // 尺寸过渡中不要抢占动画的高度
        int height = mLockedVideoHeight > 0 ? mLockedVideoHeight : targetVideoHeight();
        ViewGroup.LayoutParams lp = mBinding.video.getLayoutParams();
        if (lp.height == height) return;
        lp.height = height;
        mBinding.video.setLayoutParams(lp);
    }

    /** 过渡动画的终点与结束态布局必须按同一个高度算，否则会差一截 */
    private int targetVideoHeight() {
        int height = knownVideoHeight();
        return height > 0 ? height : (int) (ResUtil.getScreenWidth() * 9f / 16f);
    }

    /** 已经知道的比例优先，拿不到返回 0 */
    private int knownVideoHeight() {
        if (service() == null) return 0;
        if (player().getVideoWidth() > 0 && player().getVideoHeight() > 0) {
            return calcVideoHeight(player().getVideoWidth(), player().getVideoHeight());
        }
        int[] size = trackVideoSize();
        return size == null ? 0 : calcVideoHeight(size[0], size[1]);
    }

    private void onDeferredPlay(View view) {
        mPlayConfirmed = true;
        renderDeferredPlay(false);
        mDeferredRequest = null;
        // 走既有的刷新链路：内部会重新请求当前线路/集数的播放地址，状态衔接更稳
        mVod.refresh();
    }

    @Override
    public void requestPreload(VodPlayRequest request) {
        mViewModel.preloadContent(request);
    }

    @Override
    public void requestSearch(List<Site> sites, String keyword) {
        mQuickAdapter.clear();
        mViewModel.searchContent(sites, keyword, true);
    }

    @Override
    public void prepareSource(Vod item) {
        getIntent().putExtra("key", item.getSiteKey());
        getIntent().putExtra("pic", item.getPic());
        getIntent().putExtra("id", item.getId());
        mBinding.swipeLayout.setRefreshing(true);
        mBinding.swipeLayout.setEnabled(false);
        mBinding.scroll.scrollTo(0, 0);
        mClock.setCallback(null);
        updateNavigationKey();
        player().reset();
        player().stop();
        checkControl();
    }

    @Override
    public void stopPlaybackForRefresh() {
        player().stop();
        player().clear();
        mClock.setCallback(null);
        checkControl();
    }

    @Override
    public void resetPlaybackForError(String msg) {
        mBinding.swipeLayout.setEnabled(true);
        PlaybackReset.afterError(player(), () -> mClock.setCallback(null));
        showError(msg);
    }

    @Override
    public void replay(long position) {
        player().replay(position);
    }

    @Override
    public void startPlayback(Result result, boolean useParse, long startPositionMs, MediaMetadata metadata) {
        renderDeferredPlay(false);
        startPlayer(getHistoryKey(), result, useParse, getSite().getTimeout(), startPositionMs, metadata);
    }

    @Override
    public boolean preloadPlayback(Result result, long startPositionMs, MediaMetadata metadata) {
        return player().preload(PlaySpec.from(result, getHistoryKey(), metadata), startPositionMs);
    }

    @Override
    public void clearPreload() {
        if (service() != null && isOwner()) player().clearPreload();
    }

    @Override
    public void loadDanmaku(Result result, History history, Episode episode) {
        VodPlaybackMedia.searchDanmaku(result, history, episode, player()::setDanmaku, player()::addDanmaku);
    }

    @Override
    public void renderDetail(Vod item, History history) {
        mHistory = history;
        mBinding.swipeLayout.setRefreshing(false);
        mBinding.progressLayout.showContent();
        mBinding.name.setText(item.getName());
        App.removeCallbacks(mR4);
        setArtwork(item.getPic());
        loadBackdrop(item.getName(), item.getYear(), item.getPic());
        checkKeepImg();
        setText(item);
        updateKeep();
        // 数据回来后再刷一次播放按钮文案：此时才知道上次看到第几集
        if (shouldDeferAutoPlay()) renderDeferredPlay(true);
    }

    /**
     * 详情页剧照分级加载（手机整屏、平板视频窗内）：
     * ① 先用站源图（剥离缩略参数后的原图优先，失败回退原始 URL）保证立即有画面；
     * ② 后台按片名查豆瓣 /l/ 大图（≈1080×1914，正好全屏），命中后替换；
     * ③ 任何一级失败都由 ImgUtil.loadWithFallback 自动回退，最终兜底文字占位，不会黑屏。
     */
    private void loadBackdrop(String name, String year, String pic) {
        if (mBinding == null || mBinding.deferredCover == null) return;
        List<String> base = new ArrayList<>();
        String original = ImgUtil.original(pic);
        if (!TextUtils.isEmpty(original) && !original.equals(pic)) base.add(original);
        if (!TextUtils.isEmpty(pic)) base.add(pic);
        ImgUtil.loadWithFallback(name, base, mBinding.deferredCover);
        Douban.getLargePicAsync(name, year, large -> {
            if (mBinding == null || mBinding.deferredCover == null) return;
            // 已经起播、或期间切了片，就不要再替换背景
            if (mPlayConfirmed || !name.equals(getVodName())) return;
            if (TextUtils.isEmpty(large)) return;
            List<String> chain = new ArrayList<>();
            chain.add(large);
            chain.addAll(base);
            ImgUtil.loadWithFallback(name, chain, mBinding.deferredCover);
        });
    }

    @Override
    public void renderVodUpdate(Vod item) {
        if (!item.getId().isEmpty()) updateNavigationKey();
        renderVodMetadata(item.getName(), item.getPic());
        setText(item);
    }

    private void renderVodMetadata(String name, String pic) {
        if (name.isEmpty() && pic.isEmpty()) return;
        if (!name.isEmpty()) mBinding.name.setText(name);
        if (!pic.isEmpty()) {
            setArtwork();
        }
        updateKeep();
    }

    @Override
    public void renderEmptyDetail() {
        showEmpty();
    }

    @Override
    public void renderFallbackName(String name) {
        mBinding.name.setText(name);
    }

    @Override
    public void renderFlags(List<Flag> items) {
        mBinding.flag.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
        mFlagAdapter.addAll(items);
    }

    @Override
    public void renderEpisodes(List<Episode> items) {
        setEpisodeAdapter(items);
    }

    @Override
    public void renderFlagSelection(Flag item) {
        notifyItemChanged(mBinding.flag, mFlagAdapter);
        scrollToPosition(mBinding.flag, mFlagAdapter.getPosition());
    }

    @Override
    public void renderEpisodeSelection(Episode item) {
        notifyItemChanged(mBinding.episode, mEpisodeAdapter);
        scrollToPosition(mBinding.episode, mEpisodeAdapter.getPosition());
    }

    @Override
    public void renderReverseEpisodes(List<Episode> items, boolean scroll) {
        setEpisodeAdapter(items);
        if (scroll) scrollToPosition(mBinding.episode, mEpisodeAdapter.getPosition());
    }

    @Override
    public void renderQuality(Result result, boolean visible) {
        mBinding.swipeLayout.setRefreshing(false);
        mQualityResult = result;
        mQualityAdapter.addAll(result);
        setQualityVisible(visible);
    }

    @Override
    public void renderQualityVisible(boolean visible) {
        setQualityVisible(visible);
    }

    @Override
    public void renderSources(List<Vod> items) {
        mQuickAdapter.addAll(items);
        mBinding.quick.setVisibility(mQuickAdapter.isEmpty() ? View.GONE : View.VISIBLE);
        if (mBinding.quickHeader != null) mBinding.quickHeader.setVisibility(mQuickAdapter.isEmpty() ? View.GONE : View.VISIBLE);
    }

    @Override
    public void renderHistory(History history) {
        mHistory = history;
        mBinding.control.action.opening.setText(history.getOpening() <= 0 ? getString(R.string.play_op) : Util.timeMs(history.getOpening()));
        mBinding.control.action.ending.setText(history.getEnding() <= 0 ? getString(R.string.play_ed) : Util.timeMs(history.getEnding()));
        player().setSpeed(SpeedSetting.getPlayback());
        setScale(getScale());
    }

    @Override
    public void renderUseParse(boolean useParse) {
        setUseParse(useParse);
        mBinding.control.action.parse.setVisibility(isUseParse() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void renderArtwork(String url) {
        setArtwork(url);
    }

    @Override
    public void renderDescription(String desc) {
        setText(mBinding.content, 0, desc);
    }

    @Override
    public void renderPlaybackMetadata(MediaMetadata metadata) {
        if (service() != null && isOwner()) player().setMetadata(metadata);
        mPlayTitle = metadata.displayTitle == null ? "" : metadata.displayTitle.toString();
        updateTitle(null);
    }

    @Override
    public void onDetailFallbackScheduled() {
        App.post(mR4, 10000);
    }

    @Override
    public void onDetailFallbackCancelled() {
        App.removeCallbacks(mR4);
    }

    @Override
    public void onSearchStarted(String keyword) {
    }

    @Override
    public void onSearchResult() {
        App.removeCallbacks(mR4);
    }

    @Override
    public void showDetailMessage(String msg) {
        Notify.show(msg);
    }

    @Override
    public void showSwitchLine(Flag flag) {
        Notify.show(getString(R.string.play_switch_flag, flag.getFlag()));
    }

    @Override
    public void showSwitchSource(Vod item) {
        Notify.show(getString(R.string.play_switch_site, item.getSiteName()));
    }

    @Override
    public void showEpisodeReady(Episode item) {
        Notify.show(getString(R.string.play_ready, item.getName()));
    }

    @Override
    public void showNoNext(boolean reversed) {
        Notify.show(reversed ? R.string.error_play_prev : R.string.error_play_next);
    }

    @Override
    public void showNoPrev(boolean reversed) {
        Notify.show(reversed ? R.string.error_play_next : R.string.error_play_prev);
    }

    @Override
    public void finishVod() {
        finish();
    }

    private void checkId() {
        mVod.checkId();
    }

    private void checkLand() {
        if (isPort() && ResUtil.isLand(this)) enterFullscreen();
    }

    private void showEmpty() {
        showError(getString(R.string.error_detail));
        mBinding.swipeLayout.setEnabled(true);
        mBinding.progressLayout.showEmpty();
    }

    private void setText(Vod item) {
        mTypeName = item.getTypeName();
        String site = getSite() != null ? getSite().getName() : "";
        if (mBinding.headerFlag != null && !TextUtils.isEmpty(site)) mBinding.headerFlag.setText(getString(R.string.detail_flag) + " · " + site);
        setText(mBinding.director, R.string.detail_director, item.getDirector());
        setText(mBinding.actor, R.string.detail_actor, item.getActor());
        setText(mBinding.content, 0, item.getContent());
        setMeta(mBinding.other, item);
    }

    private void setText(TextView view, int resId, String text) {
        if (view == null) return;
        if (TextUtils.isEmpty(text) && !TextUtils.isEmpty(view.getText())) return;
        String plain = decodeEntities(text);
        view.setText(Sniffer.buildClickable(resId > 0 ? getString(resId, plain) : plain, this::clickableSpan), TextView.BufferType.SPANNABLE);
        view.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
        if (view == mBinding.content) setContentVisible();
        view.setLinkTextColor(Color.WHITE);
        CustomMovement.bind(view);
    }

    /** 源的简介等文本常带 HTML 实体，直接显示会出现字面 &nbsp; */
    private static String decodeEntities(String s) {
        if (s == null) return "";
        return s.replace("&nbsp;", " ").replace("&#160;", " ").replace("&quot;", "\"")
                .replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
    }

    private void setContentVisible() {
        mBinding.contentLayout.setVisibility(mBinding.content.getVisibility());
    }

    private ClickableSpan clickableSpan(Result result) {
        return new ClickableSpan() {
            @Override
            public void onClick(@NonNull View view) {
                FolderActivity.start(getActivity(), getKey(), result);
                ((TextView) view).setMaxLines(Integer.MAX_VALUE);
                setRedirect(true);
            }
        };
    }

    /** 详情首屏只留一行元信息：更新备注 · 年份 · 地区 · 类型 */
    private void setMeta(TextView view, Vod item) {
        if (view == null) return;
        List<String> parts = new ArrayList<>();
        if (!TextUtils.isEmpty(item.getRemarks())) parts.add(item.getRemarks());
        if (!TextUtils.isEmpty(item.getYear())) parts.add(item.getYear());
        if (!TextUtils.isEmpty(item.getArea())) parts.add(item.getArea());
        if (!TextUtils.isEmpty(mTypeName)) parts.add(mTypeName.replace("/", " · "));
        String text = TextUtils.join(" · ", parts);
        view.setText(text);
        view.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onItemClick(Flag item) {
        mPlayConfirmed = true;
        mVod.selectFlag(item);
    }

    @Override
    public void onItemClick(Episode item) {
        if (shouldEnterFullscreen(item)) return;
        mPlayConfirmed = true;
        mVod.selectEpisode(item);
    }

    @Override
    public void onItemClick(Result result) {
        mPlayConfirmed = true;
        mVod.selectQuality(result);
    }

    @Override
    public void onItemClick(Vod item) {
        mPlayConfirmed = true;
        mVod.selectSource(item);
    }

    private void setEpisodeAdapter(List<Episode> items) {
        mBinding.control.action.episodes.setVisibility(items.size() < 2 ? View.GONE : View.VISIBLE);
        mBinding.control.next.setVisibility(items.size() < 2 ? View.GONE : View.VISIBLE);
        mBinding.control.prev.setVisibility(items.size() < 2 ? View.GONE : View.VISIBLE);
        mBinding.reverse.setVisibility(items.size() < 2 ? View.GONE : View.VISIBLE);
        mBinding.episode.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
        mBinding.more.setVisibility(items.size() < 10 ? View.GONE : View.VISIBLE);
        mEpisodeAdapter.addAll(items);
    }

    private void setQualityVisible(boolean visible) {
        mBinding.qualityText.setVisibility(visible ? View.VISIBLE : View.GONE);
        mBinding.quality.setVisibility(visible ? View.VISIBLE : View.GONE);
        boolean multi = visible && mQualityResult != null && mQualityResult.getUrl().isMulti();
        mBinding.control.action.qualityPick.setVisibility(multi ? View.VISIBLE : View.GONE);
    }

    /** 全屏（竖屏 / 横屏）时详情页被播放器盖住，画质档按钮放到底部控制栏，选档列表也从底部弹出 */
    private void onQualityPick(View view) {
        Result result = mQualityResult;
        if (result == null || !result.getUrl().isMulti()) return;
        int count = result.getUrl().getValues().size();
        String[] names = new String[count];
        for (int i = 0; i < count; i++) {
            String name = result.getUrl().n(i);
            names[i] = TextUtils.isEmpty(name) ? getString(R.string.quality_option, i + 1) : name;
        }
        int checked = Math.max(-1, Math.min(result.getUrl().getPosition(), count - 1));
        AlertDialog dialog = new GlassAlertDialogBuilder(this)
                .setTitle(R.string.detail_quality)
                .setSingleChoiceItems(names, checked, (d, which) -> {
                    result.getUrl().set(which);
                    mVod.selectQuality(result);
                    mQualityAdapter.addAll(result);
                    d.dismiss();
                })
                .setNegativeButton(R.string.dialog_negative, null)
                .create();
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setGravity(Gravity.BOTTOM);
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void onName() {
        String name = mBinding.name.getText().toString();
        Notify.show(getString(R.string.detail_search, name));
        mVod.search(name);
    }

    private void onMore() {
        EpisodeGridDialog.create().reverse(mHistory.isRevSort()).episodes(mEpisodeAdapter.getItems()).show(this);
    }

    private void onActor() {
        mBinding.actor.setMaxLines(mBinding.actor.getMaxLines() == 2 ? Integer.MAX_VALUE : 2);
    }

    private void onDirector() {
        mBinding.director.setMaxLines(mBinding.director.getMaxLines() == 1 ? Integer.MAX_VALUE : 1);
    }

    private void onContent() {
        mBinding.content.setMaxLines(mBinding.content.getMaxLines() == 3 ? Integer.MAX_VALUE : 3);
    }

    private void onReverse() {
        mVod.setRevSort(!mHistory.isRevSort());
        mVod.reverseEpisode(false);
    }

    private boolean onChange() {
        mVod.manualSwitchSource();
        return true;
    }

    private boolean onCopy() {
        Util.copy(mBinding.content.getText().toString());
        return true;
    }

    private void onBack() {
        if (isFullscreen()) exitFullscreen();
        else if (canReturnToDetail()) returnToDetail();
        else finish();
    }

    /** 起播后按返回不退出页面，回到详情态（暂停），与「点播放才起播」的闭环对称 */
    private boolean canReturnToDetail() {
        return mPlayConfirmed && !isFullscreen() && mBinding != null && mBinding.deferredCover != null && player() != null && !TextUtils.isEmpty(player().getUrl());
    }

    private void returnToDetail() {
        player().pause();
        hideControl();
        mPlayConfirmed = false;
        renderDeferredPlay(true);
    }

    private void onCast() {
        // 详情态还没起播，播放器里自然没有地址，直接告诉用户该怎么做
        if (mBinding != null && mBinding.deferredPlay != null && mBinding.deferredPlay.getVisibility() == View.VISIBLE) {
            Notify.show(R.string.cast_play_first);
            return;
        }
        if (TextUtils.isEmpty(player().getUrl())) {
            Notify.show(R.string.error_play_url);
            return;
        }
        CastDialog.create(player()).history(mHistory).show(this);
    }

    private void onInfo() {
        InfoDialog.create(player()).show(this);
    }

    private void onKeep() {
        // 播放器控制栏的收藏按钮也走这里，单独防连点：toggle 被触发两次会「加入→取消」互相抵消，
        // 表现就是图标没变化却提示「已取消收藏」。
        long now = SystemClock.uptimeMillis();
        if (now - mLastKeepTime < 350) return;
        mLastKeepTime = now;
        Keep keep = Keep.find(getHistoryKey());
        boolean kept = keep != null;
        if (kept) keep.delete();
        else createKeep();
        Notify.show(kept ? R.string.keep_del : R.string.keep_add);
        // 直接按本次结果刷 UI，不重新查库，避免写库时序导致图标不更新
        setKeepState(!kept);
        popKeep();
    }

    private void checkPlay() {
        setR1Callback();
        if (player().isPlaying()) onPaused();
        else if (player().isEmpty()) onRefresh();
        else onPlay();
    }

    private void checkNext() {
        checkNext(true);
    }

    private void checkNext(boolean notify) {
        setR1Callback();
        mVod.nextEpisode(notify);
    }

    private void checkPrev() {
        setR1Callback();
        mVod.prevEpisode(true);
    }

    private void onSetting() {
        ControlDialog.create().parent(mBinding.control.action.getRoot()).parse(isUseParse()).player(player()).show(this);
    }

    private void onLock() {
        setLock(!isLock());
        setRequestedOrientation(getLockOrient());
        mKeyDown.setLock(isLock());
        checkLockImg();
        showControl();
    }

    /** 成员 Runnable 版：原先每次 new 出来的匿名任务无法在 onDestroy 移除，销毁后仍会改屏幕方向 */
    private void runLock() {
        if (isDestroyed()) return;
        onLock();
    }

    private void onRotate() {
        setR1Callback();
        if (isFullscreen()) exitFullscreen();
        else enterFullscreen();
        checkFullscreenImg();
    }

    private void onTrack(View view) {
        TrackDialog.create().type(Integer.parseInt(view.getTag().toString())).player(player()).view(mBinding.player.getSubtitleView()).show(this);
        hideControl();
    }

    private void onEdition() {
        EditionDialog.create().player(player()).show(this);
        hideControl();
    }

    private void onChapter() {
        ChapterDialog.create().player(player()).show(this);
        hideControl();
    }

    private void onDanmaku() {
        DanmakuDialog.create().player(player()).show(this);
        hideControl();
    }

    private void onDanmakuShow() {
        DanmakuSetting.putShow(!DanmakuSetting.isShow());
        checkDanmakuImg();
        syncDanmakuEnabled();
    }

    private void onRepeat() {
        player().setRepeatOne(!player().isRepeatOne());
        mBinding.control.action.repeat.setSelected(player().isRepeatOne());
    }

    @Override
    public void onRepeatModeChanged(int repeatMode) {
        mBinding.control.action.repeat.setSelected(player().isRepeatOne());
    }

    private void onScale() {
        int index = getScale();
        String[] array = ResUtil.getStringArray(R.array.select_scale);
        if (mKeyDown.getScale() != 1.0f) mKeyDown.resetScale();
        else setScale(index == array.length - 1 ? 0 : ++index);
        setR1Callback();
    }

    private void onSpeed() {
        SpeedSettingDialog.create().player(player()).save(true).show(this);
        hideControl();
    }

    private boolean onSpeedLong() {
        SpeedSetting.putPlayback(PlaybackAction.toggleSpeed(player(), mBinding.widget.message));
        setR1Callback();
        return true;
    }

    private void onReset() {
        onRefresh();
    }

    private void onParse() {
        ParseDialog.create().show(this);
        hideControl();
    }

    private void onReplay() {
        mVod.replay();
    }

    private void onRefresh() {
        mVod.refresh();
    }

    private void onDecode() {
        mClock.setCallback(null);
        player().toggleDecode();
        setR1Callback();
    }

    private void onEnding() {
        long position = player().getPosition();
        long duration = player().getDuration();
        if (player().canSetEnding(position, duration)) setEnding(duration - position);
        setR1Callback();
    }

    private boolean onEndingReset() {
        setR1Callback();
        setEnding(0);
        return true;
    }

    private void setEnding(long ending) {
        mVod.setEnding(ending);
        mBinding.control.action.ending.setText(ending <= 0 ? getString(R.string.play_ed) : Util.timeMs(mHistory.getEnding()));
    }

    private void onOpening() {
        long position = player().getPosition();
        long duration = player().getDuration();
        if (player().canSetOpening(position, duration)) setOpening(position);
        setR1Callback();
    }

    private boolean onOpeningReset() {
        setR1Callback();
        setOpening(0);
        return true;
    }

    private void setOpening(long opening) {
        mVod.setOpening(opening);
        mBinding.control.action.opening.setText(opening <= 0 ? getString(R.string.play_op) : Util.timeMs(mHistory.getOpening()));
    }

    private void onEpisodes() {
        EpisodeListDialog.create().episodes(mEpisodeAdapter.getItems()).show(this);
    }

    private void onPlayer() {
        PlayerEngineDialog.show(this, mBinding.control.action.player, player());
        hideControl();
    }

    private boolean onActionTouch(View v, MotionEvent e) {
        setR1Callback();
        return false;
    }

    private void onSwipeRefresh() {
        if (mBinding.progressLayout.isEmpty()) mVod.requestDetail();
        else mVod.refresh();
    }

    private boolean shouldEnterFullscreen(Episode item) {
        boolean enter = !isFullscreen() && item.isSelected();
        if (enter) enterFullscreen();
        return enter;
    }

    private void enterFullscreen() {
        if (isFullscreen()) return;
        setFullscreen(true);
        if (isLand() && !player().isPortrait()) setTransition();
        mBinding.video.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
        setRequestedOrientation(PlaybackOrientation.getEnterFullscreenOrientation(player().isPortrait()));
        setRotate(player().isPortrait());
        mKeyDown.resetScale();
        App.post(mR3, 2000);
        hideControl();
    }

    private void exitFullscreen() {
        if (!isFullscreen()) return;
        setFullscreen(false);
        if (isLand() && !player().isPortrait()) setTransition();
        setRequestedOrientation(PlaybackOrientation.getExitFullscreenOrientation(isPort()));
        mEpisodeScroll.schedule();
        mBinding.video.setLayoutParams(mFrameParams);
        mKeyDown.resetScale();
        App.post(mR3, 2000);
        setRotate(false);
        hideControl();
    }

    private void setTransition() {
        ChangeBounds transition = new ChangeBounds();
        transition.setDuration(150);
        ViewGroup parent = (ViewGroup) mBinding.video.getParent();
        TransitionManager.beginDelayedTransition(parent, transition);
    }

    private int getLockOrient() {
        return PlaybackOrientation.getLockOrientation(this, isLock(), isRotate(), isPort() && isAutoRotate());
    }

    private void showProgress() {
        mBinding.progress.getRoot().setVisibility(View.VISIBLE);
        mOsd.startTraffic();
        hideError();
    }

    private void hideProgress() {
        mBinding.progress.getRoot().setVisibility(View.GONE);
        mOsd.stopTraffic();
    }

    private void showError(String text) {
        PlaybackAction.hideSpeedHint(mBinding.widget.message);
        mBinding.widget.error.setVisibility(View.VISIBLE);
        mBinding.widget.error.setText(text);
        hideProgress();
    }

    private void hideError() {
        mBinding.widget.error.setVisibility(View.GONE);
        mBinding.widget.error.setText("");
    }

    private void syncDanmakuEnabled() {
        player().setDanmakuEnabled(DanmakuSetting.isShow());
    }

    private void checkFullscreenImg() {
        mBinding.control.right.rotate.setImageResource(isFullscreen() ? R.drawable.ic_control_fullscreen_exit : R.drawable.ic_control_fullscreen);
    }

    private void hideDanmaku() {
        player().setDanmakuEnabled(false);
    }

    private void showControl() {
        if (service() == null || isInPictureInPictureMode()) return;
        mBinding.control.danmaku.setVisibility(isLock() || !player().haveDanmaku() ? View.GONE : View.VISIBLE);
        mBinding.control.right.rotate.setVisibility(isLock() ? View.GONE : View.VISIBLE);
        checkFullscreenImg();
        mBinding.control.action.getRoot().setVisibility(isFullscreen() ? View.VISIBLE : View.GONE);
        mBinding.control.right.lock.setVisibility(isFullscreen() ? View.VISIBLE : View.GONE);
        mBinding.control.action.parse.setVisibility(isUseParse() ? View.VISIBLE : View.GONE);
        mBinding.control.info.setVisibility(player().isEmpty() ? View.GONE : View.VISIBLE);
        mBinding.control.cast.setVisibility(isFullscreen() && !player().isEmpty() ? View.VISIBLE : View.GONE);
        mBinding.control.osd.setVisibility(isFullscreen() ? View.VISIBLE : View.GONE);
        mBinding.control.center.setVisibility(isLock() ? View.GONE : View.VISIBLE);
        mBinding.control.bottom.setVisibility(isLock() ? View.GONE : View.VISIBLE);
        mBinding.control.back.setVisibility(isLock() ? View.GONE : View.VISIBLE);
        mBinding.control.top.setVisibility(isLock() ? View.GONE : View.VISIBLE);
        mBinding.control.getRoot().setVisibility(View.VISIBLE);
        mBinding.control.getRoot().animate().cancel();
        mBinding.control.getRoot().setAlpha(0f);
        mBinding.control.getRoot().animate().alpha(1f).setDuration(200).start();
        setR1Callback();
        // 控制栏每次弹出都重取一次分辨率，避免尺寸回调早于元数据而被清掉
        updateTitle(null);
        mOsd.refreshOsd();
    }

    private void hideControl() {
        mOsd.stopOsd();
        View root = mBinding.control.getRoot();
        if (root.getVisibility() == View.GONE) {
            App.removeCallbacks(mR1);
            return;
        }
        root.animate().cancel();
        root.animate().alpha(0f).setDuration(180).withEndAction(() -> {
            if (root.getParent() != null) root.setVisibility(View.GONE);
            root.setAlpha(1f);
        }).start();
        App.removeCallbacks(mR1);
    }

    private void dismissDialogs() {
        for (Fragment fragment : getSupportFragmentManager().getFragments()) if (fragment instanceof DialogFragment dialog) dialog.dismiss();
    }

    private void setOrient() {
        if (isPort() && isAutoRotate()) setRequestedOrientation(PlaybackOrientation.getPortAutoRotateOrientation());
        if (isLand() && isAutoRotate()) setRequestedOrientation(PlaybackOrientation.getLandAutoRotateOrientation());
    }

    private void setR1Callback() {
        if (isScrubbing()) return;
        App.removeCallbacks(mR1);
        // 暂停/加载时常驻显示控制栏，仅播放中倒计时自动隐藏。
        if (player().isPlaying()) App.post(mR1, Constant.INTERVAL_HIDE);
    }

    @Override
    protected void onScrubbingChanged(boolean scrubbing) {
        if (scrubbing) App.removeCallbacks(mR1);
        else if (isVisible(mBinding.control.getRoot())) setR1Callback();
    }

    private void setArtwork(String url) {
        mHistory.setVodPic(url);
        setArtwork();
    }

    private void setArtwork() {
        ImgUtil.load(this, mHistory.getVodPic(), mArtworkTarget);
    }

    private final CustomTarget<Drawable> mArtworkTarget = new CustomTarget<>() {
        @Override
        public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
            mBinding.player.setDefaultArtwork(resource);
        }

        @Override
        public void onLoadFailed(@Nullable Drawable errorDrawable) {
            mBinding.player.setDefaultArtwork(errorDrawable);
        }

        @Override
        public void onLoadCleared(@Nullable Drawable placeholder) {
            mBinding.player.setDefaultArtwork(null);
        }
    };

    private void saveHistory(boolean exit) {
        PlaybackService service = service();
        boolean owner = service != null && getPlaybackKey().equals(service.player().getKey());
        long position = owner ? service.player().getPosition() : C.TIME_UNSET;
        long duration = owner ? service.player().getDuration() : C.TIME_UNSET;
        if (mVod != null) mVod.saveHistory(exit, System.currentTimeMillis(), position, duration);
    }

    private void checkControl() {
        if (isVisible(mBinding.control.getRoot())) showControl();
    }

    private void checkKeepImg() {
        setKeepState(Keep.find(getHistoryKey()) != null);
    }

    /** 收藏态要一眼可见：实心星 + 暖金圆底 / 空心星 + 半透明圆底，不跟其余圆钮一个表情 */
    private void setKeepState(boolean kept) {
        if (mBinding.keepBtn instanceof android.widget.ImageView) {
            android.widget.ImageView keep = (android.widget.ImageView) mBinding.keepBtn;
            keep.setImageResource(kept ? R.drawable.ic_control_keep_on : R.drawable.ic_control_keep_off);
            if (mTheme != null) {
                keep.setBackground(kept ? mTheme.circle(KEEP_GOLD) : mTheme.actionCircle());
                keep.setImageTintList(ColorStateList.valueOf(kept ? KEEP_GOLD_ON : mTheme.onSurface));
            }
        }
    }

    private void popKeep() {
        if (!(mBinding.keepBtn instanceof View)) return;
        View view = mBinding.keepBtn;
        view.animate().cancel();
        view.setScaleX(0.84f);
        view.setScaleY(0.84f);
        view.animate().scaleX(1f).scaleY(1f).setInterpolator(new OvershootInterpolator(2.4f)).setDuration(240).start();
    }

    private void checkLockImg() {
        mBinding.control.right.lock.setImageResource(isLock() ? R.drawable.ic_control_lock_on : R.drawable.ic_control_lock_off);
    }

    private void checkDanmakuImg() {
        mBinding.control.danmaku.setImageResource(DanmakuSetting.isShow() ? R.drawable.ic_control_danmaku_on : R.drawable.ic_control_danmaku_off);
    }

    private void createKeep() {
        Keep keep = new Keep();
        keep.setKey(getHistoryKey());
        keep.setCid(VodConfig.getCid());
        // 详情态尚未起播时可能还没有历史记录，用列表页带过来的片名/海报兜底
        keep.setVodPic(mHistory != null ? mHistory.getVodPic() : getPic());
        keep.setVodName(mHistory != null ? mHistory.getVodName() : getName());
        keep.setSiteName(getSite().getName());
        keep.setCreateTime(System.currentTimeMillis());
        keep.save();
    }

    private void updateKeep() {
        Keep keep = Keep.find(getHistoryKey());
        if (keep != null) {
            keep.setVodName(mHistory.getVodName());
            keep.setVodPic(mHistory.getVodPic());
            keep.save();
        }
    }

    private final PlaybackService.NavigationCallback mNavigationCallback = new PlaybackService.NavigationCallback() {
        @Override
        public void onNext() {
            checkNext();
        }

        @Override
        public void onPrev() {
            checkPrev();
        }

        @Override
        public void onStop() {
            finish();
        }

        @Override
        public void onReplay() {
            VideoActivity.this.onReplay();
        }

        @Override
        public void onAudio() {
            Util.moveToBackground(VideoActivity.this);
            setAudioOnly(true);
        }
    };

    @Override
    protected String getPlaybackKey() {
        return getHistoryKey();
    }

    @Override
    protected void onPrepare() {
        setPlaybackMode();
        checkControl();
    }

    @Override
    protected void onDecodeChanged() {
        setPlaybackMode();
    }

    @Override
    protected void onTracksChanged() {
        setTrackVisible();
        applyTrackSize();
    }

    @Override
    protected void onMediaOptionsChanged() {
        setMediaOptionVisible();
    }

    @Override
    protected void onError(String msg) {
        mVod.playbackError(msg);
    }

    @Override
    protected void onReclaim() {
        mVod.refresh();
    }

    @Override
    protected void onStateChanged(int state) {
        switch (state) {
            case Player.STATE_BUFFERING:
                showProgress();
                mClock.setCallback(null);
                break;
            case Player.STATE_READY:
                hideProgress();
                checkControl();
                player().reset();
                mClock.setCallback(this);
                break;
            case Player.STATE_ENDED:
                hideProgress();
                setR1Callback();
                mVod.playbackEnded();
                updatePlayControl(false);
                mClock.setCallback(null);
                break;
        }
    }

    @Override
    protected void onPlayingChanged(boolean isPlaying) {
        if (isPlaying || isPaused()) updatePlayControl(isPlaying);
    }

    private void updatePlayControl(boolean isPlaying) {
        mBinding.control.play.setImageResource(isPlaying ? androidx.media3.ui.R.drawable.exo_icon_pause : androidx.media3.ui.R.drawable.exo_icon_play);
        mPiP.update(this, isPlaying);
        // 播放/暂停切换时重置控制栏自动隐藏计时。
        if (mBinding != null && isVisible(mBinding.control.getRoot())) setR1Callback();
    }

    @Override
    protected void onSizeChanged(VideoSize size) {
        changeHeight();
        checkOrientation();
        updateTitle(size);
    }

    private void updateTitle(VideoSize size) {
        if (mBinding == null) return;
        mBinding.control.title.setVisibility(View.VISIBLE);
        mBinding.control.title.setText(mPlayTitle);
        mBinding.control.title.setSelected(true);
        int[] video = currentVideoSize(size);
        if (video == null) {
            mBinding.control.resolution.setVisibility(View.GONE);
        } else {
            mBinding.control.resolution.setText(video[0] + "x" + video[1]);
            mBinding.control.resolution.setVisibility(View.VISIBLE);
        }
    }

    /** 标题下方的分辨率不能只依赖一次尺寸回调：起播后的元数据刷新会传 null 重刷标题，
     *  旧写法会顺势把已显示的分辨率清掉且不再恢复。
     *  优先用回调尺寸，其次播放器当前视频尺寸，最后退回已选视频轨的 Format。 */
    private int[] currentVideoSize(VideoSize size) {
        if (size != null && size.width > 0 && size.height > 0) return new int[]{size.width, size.height};
        if (service() == null) return null;
        int width = player().getVideoWidth();
        int height = player().getVideoHeight();
        if (width > 0 && height > 0) return new int[]{width, height};
        return trackVideoSize();
    }

    @Override
    public void onTimeChanged(long time) {
        if (!isOwner() || !player().isVod()) return;
        long position = player().getPosition();
        long duration = player().getDuration();
        if (position < 0 || duration <= 0) return;
        mVod.onTimeChanged(time, position, duration);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onCastEvent(CastEvent event) {
        if (isRedirect()) return;
        ReceiveDialog.create().event(event).show(this);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        if (isRedirect()) return;
        if (event.getType() == RefreshEvent.Type.DETAIL) mVod.requestDetail();
        else if (event.getType() == RefreshEvent.Type.PLAYER) mVod.refresh();
        else if (event.getType() == RefreshEvent.Type.VOD) mVod.updateVod(event.getVod());
        else if (event.getType() == RefreshEvent.Type.SUBTITLE) player().setSub(Sub.from(event.getPath()));
        else if (event.getType() == RefreshEvent.Type.DANMAKU) player().setDanmaku(Danmaku.from(event.getPath()));
    }

    @Override
    protected long startPositionMs() {
        return mVod == null ? C.TIME_UNSET : mVod.startPositionMs();
    }

    private void checkOrientation() {
        if (isFullscreen() && !isRotate() && player().isPortrait()) {
            setRequestedOrientation(PlaybackOrientation.getPortraitVideoSizeOrientation());
            setRotate(true);
        } else if (isFullscreen() && isRotate() && player().isLandscape()) {
            setRequestedOrientation(PlaybackOrientation.getLandscapeVideoSizeOrientation());
            setRotate(false);
        }
    }

    private void changeHeight() {
        if (isFullscreen() || isInPictureInPictureMode()) return;
        int videoWidth = player().getVideoWidth();
        int videoHeight = player().getVideoHeight();
        if (videoWidth == 0 || videoHeight == 0) return;
        applyVideoHeight(calcVideoHeight(videoWidth, videoHeight), true);
    }

    private int calcVideoHeight(int videoWidth, int videoHeight) {
        int viewWidth = ResUtil.getScreenWidth();
        int minHeight = ResUtil.dp2px(150);
        int maxHeight = ResUtil.getScreenHeight() / 2;
        int wideHeight = (int) (viewWidth * 9f / 16f);
        int calculated = (int) (viewWidth * ((float) videoHeight / videoWidth));
        // 宽画幅（比 16:9 宽或相同）统一用 16:9 窗口，上下黑边承载控制栏；
        // 竖屏/4:3 等比 16:9 高，仍按内容贴合，避免两侧大黑边
        if (calculated < wideHeight) calculated = wideHeight;
        return Math.clamp(calculated, minHeight, Math.max(minHeight, maxHeight));
    }

    private void applyVideoHeight(int finalHeight, boolean animate) {
        if (finalHeight == mBinding.video.getHeight()) return;
        // 详情↔播放过渡中先记下尺寸，等动画结束再平滑应用，避免把下方详情一起拽动
        if (mDeferredAnimator != null || mLockedVideoHeight > 0) {
            mPendingVideoHeight = finalHeight;
            return;
        }
        if (mAnimator.isRunning()) mAnimator.cancel();
        if (!animate) {
            mFrameParams.height = finalHeight;
            mBinding.video.setLayoutParams(mFrameParams);
            return;
        }
        mAnimator.setIntValues(mBinding.video.getHeight(), finalHeight);
        mAnimator.setDuration(300);
        mAnimator.start();
    }

    /** 视频轨已就绪、首帧还没到时（缓冲中）先把窗口定到最终尺寸，
     *  否则占位是 16:9，遇到竖屏/非 16:9 片源起播就会缩一下。 */
    private void applyTrackSize() {
        if (service() == null) return;
        if (isFullscreen() || isInPictureInPictureMode()) return;
        if (player().getVideoWidth() > 0 && player().getVideoHeight() > 0) return;
        int[] size = trackVideoSize();
        if (size == null) return;
        applyVideoHeight(calcVideoHeight(size[0], size[1]), false);
    }

    /** 部分片源把竖屏写在旋转角里，宽高、旋转角、像素宽高比三者一起才能还原出显示尺寸。 */
    private int[] trackVideoSize() {
        Tracks tracks = player().getCurrentTracks();
        for (Tracks.Group group : tracks.getGroups()) {
            if (group.getType() != C.TRACK_TYPE_VIDEO || !group.isSelected()) continue;
            for (int i = 0; i < group.length; i++) {
                if (!group.isTrackSupported(i)) continue;
                Format format = group.getTrackFormat(i);
                int width = format.width;
                int height = format.height;
                if (width <= 0 || height <= 0) continue;
                if (format.pixelWidthHeightRatio > 0) width = Math.round(width * format.pixelWidthHeightRatio);
                if (format.rotationDegrees == 90 || format.rotationDegrees == 270) {
                    int swap = width;
                    width = height;
                    height = swap;
                }
                return new int[]{width, height};
            }
        }
        return null;
    }

    private void setTrackVisible() {
        PlaybackAction.setTracks(player(), mBinding.control.action.text, mBinding.control.action.audio, mBinding.control.action.video);
    }

    private void setMediaOptionVisible() {
        PlaybackAction.setMediaOptions(player(), mBinding.control.action.edition, mBinding.control.action.chapter);
    }

    private void onPaused() {
        controller().pause();
    }

    private void onPlay() {
        if (mHistory != null && isEnded()) controller().seekTo(mHistory.getOpening());
        if (!player().isEmpty() && isIdle()) controller().prepare();
        controller().play();
    }

    private boolean isFullscreen() {
        return fullscreen;
    }

    private void setFullscreen(boolean fullscreen) {
        Util.toggleFullscreen(this, this.fullscreen = fullscreen);
    }

    public boolean isUseParse() {
        return useParse;
    }

    public void setUseParse(boolean useParse) {
        this.useParse = useParse;
    }

    public boolean isRotate() {
        return rotate;
    }

    public void setRotate(boolean rotate) {
        this.rotate = rotate;
        if (fullscreen && !rotate) setPadding(mBinding.control.getRoot());
        else noPadding(mBinding.control.getRoot());
    }

    private void notifyItemChanged(RecyclerView view, RecyclerView.Adapter<?> adapter) {
        view.post(() -> adapter.notifyItemRangeChanged(0, adapter.getItemCount()));
    }

    private void scrollToPosition(RecyclerView view, int position) {
        view.post(() -> view.scrollToPosition(position));
    }

    @Override
    public void onCasted() {
        player().stop();
    }

    @Override
    public void onScale(int tag) {
        mKeyDown.resetScale();
        setScale(tag);
    }

    @Override
    public void onParse(Parse item) {
        mVod.selectParse(item);
    }

    @Override
    public void onSpeedUp() {
        if (!player().isPlaying()) return;
        PlaybackAction.startSpeedPress(player(), mBinding.widget.message);
    }

    @Override
    public void onSpeedEnd() {
        PlaybackAction.hideSpeedHint(mBinding.widget.message);
        player().setSpeed(SpeedSetting.getPlayback());
    }

    @Override
    public void onBright(int progress) {
        mBinding.widget.bright.setVisibility(View.VISIBLE);
        mBinding.widget.brightProgress.setProgress(progress);
        if (progress < 35) mBinding.widget.brightIcon.setImageResource(R.drawable.ic_widget_bright_low);
        else if (progress < 70) mBinding.widget.brightIcon.setImageResource(R.drawable.ic_widget_bright_medium);
        else mBinding.widget.brightIcon.setImageResource(R.drawable.ic_widget_bright_high);
    }

    @Override
    public void onVolume(int progress) {
        mBinding.widget.volume.setVisibility(View.VISIBLE);
        mBinding.widget.volumeProgress.setProgress(progress);
        if (progress < 35) mBinding.widget.volumeIcon.setImageResource(R.drawable.ic_widget_volume_low);
        else if (progress < 70) mBinding.widget.volumeIcon.setImageResource(R.drawable.ic_widget_volume_medium);
        else mBinding.widget.volumeIcon.setImageResource(R.drawable.ic_widget_volume_high);
    }

    @Override
    public void onFlingUp() {
        if (mEpisodeAdapter.getItemCount() == 1) onRefresh();
        else mVod.nextEpisode(true);
    }

    @Override
    public void onFlingDown() {
        if (mEpisodeAdapter.getItemCount() == 1) onRefresh();
        else mVod.prevEpisode(true);
    }

    @Override
    public void onSeeking(long time) {
        mBinding.widget.action.setImageResource(time > 0 ? R.drawable.ic_widget_forward : R.drawable.ic_widget_rewind);
        mBinding.widget.time.setText(player().getPositionTime(time));
        mBinding.widget.seek.setVisibility(View.VISIBLE);
        hideProgress();
    }

    @Override
    public void onSeekEnd(long time) {
        seekTo(time);
    }

    @Override
    public void onSingleTap() {
        if (isVisible(mBinding.control.getRoot())) hideControl();
        else showControl();
    }

    @Override
    public void onDoubleTap() {
        if (isLock()) return;
        if (!isFullscreen()) {
            enterFullscreen();
        } else if (player().isPlaying()) {
            showControl();
            onPaused();
        } else {
            hideControl();
            onPlay();
        }
    }

    @Override
    public void onTouchEnd() {
        mBinding.widget.seek.setVisibility(View.GONE);
        mBinding.widget.bright.setVisibility(View.GONE);
        mBinding.widget.volume.setVisibility(View.GONE);
        PlaybackAction.hideSpeedHint(mBinding.widget.message);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // 不能用 service()::dispatchNext / controller()::seekTo：方法引用在传参时立即对目标求值，
        // 服务尚未重绑或 controller 未就绪时会当场抛 NPE，且发生在 onExternalResult 的 try 之外。
        if (resultCode == RESULT_OK && requestCode == 1001) {
            var svc = service();
            var ctrl = controller();
            PlaybackIntent.onExternalResult(data, () -> {
                if (svc != null) svc.dispatchNext();
            }, position -> {
                if (ctrl != null) ctrl.seekTo(position);
            });
        }
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (isRedirect()) return;
        if (isLock()) App.post(mR6, 500);
        if (service() != null && player().haveTrack(C.TRACK_TYPE_VIDEO)) mPiP.enter(this, player().getVideoWidth(), player().getVideoHeight(), getScale());
    }

    @Override
    public void onPictureInPictureModeChanged(boolean inPictureInPictureMode, @NonNull Configuration newConfig) {
        super.onPictureInPictureModeChanged(inPictureInPictureMode, newConfig);
        if (!isBindingOwner()) return;
        if (inPictureInPictureMode) onEnterPictureInPicture();
        else onExitPictureInPicture();
    }

    private void onEnterPictureInPicture() {
        if (!isFullscreen()) setVideoView(true);
        dismissDialogs();
        hideControl();
        hideDanmaku();
    }

    private void onExitPictureInPicture() {
        if (!isFullscreen()) setVideoView(false);
        syncDanmakuEnabled();
        if (isStop()) finish();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (isAutoRotate() && isPort() && newConfig.orientation == Configuration.ORIENTATION_PORTRAIT && !isRotate() && !isLock()) exitFullscreen();
        if (isAutoRotate() && isPort() && newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) enterFullscreen();
        if (isFullscreen()) Util.hideSystemUI(this);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (isFullscreen() && hasFocus && !Util.isFullscreen(this)) Util.hideSystemUI(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mClock.stop().start();
        setAudioOnly(false);
        setStop(false);
    }

    @Override
    protected void onStop() {
        super.onStop();
        saveHistory(false);
        if (PlayerSetting.isBackgroundOff()) mClock.stop();
        if (!isAudioOnly()) setStop(true);
    }

    @Override
    protected void onBackInvoked() {
        if (isVisible(mBinding.control.getRoot())) {
            hideControl();
        } else if (isFullscreen() && !isLock()) {
            exitFullscreen();
        } else if (!isLock() && canReturnToDetail()) {
            returnToDetail();
        } else if (!isLock()) {
            mViewModel.stopSearch();
            if (isTaskRoot()) startActivity(new Intent(this, HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            super.onBackInvoked();
        }
    }

    @Override
    protected void onDestroy() {
        mClock.release();
        saveHistory(true);
        Timer.get().reset();
        DanmakuApi.cancel();
        RefreshEvent.keep();
        App.removeCallbacks(mR1, mR3, mR4, mR6);
        mOsd.release();
        mEpisodeScroll.release();
        super.onDestroy();
    }
}
