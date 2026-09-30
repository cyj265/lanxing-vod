package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.media3.common.C;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.CastVideo;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Device;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.databinding.DialogDeviceBinding;
import com.fongmi.android.tv.dlna.DLNACast;
import com.fongmi.android.tv.dlna.DLNACastManager;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.ui.activity.ScanActivity;
import com.fongmi.android.tv.ui.adapter.DeviceAdapter;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ScanTask;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;
import com.github.catvod.utils.Prefers;
import com.github.catvod.utils.Util;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Response;

/**
 * 选择投屏设备对话框：居中弹窗、搜索 loading、设备列表、点击连接、已连接标记（对齐影视仓）。
 */
public class CastDialog extends BaseAlertDialog implements DeviceAdapter.OnClickListener, ScanTask.Listener, DLNACastManager.DeviceListener, Callback {

    private final FormBody.Builder body;
    private final OkHttpClient client;

    private DialogDeviceBinding binding;
    private DeviceAdapter adapter;
    private ScanTask scanTask;
    private CastVideo video;
    private Device casting;
    private boolean fm;

    private final Runnable mEmpty = this::showEmpty;

    private final ActivityResultLauncher<String> permLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            granted -> {
                // 无论授予与否都照常启动发现：授予后组播发送才可能被放行；
                // 拒绝时仍尝试(部分 ROM 不靠该权限拦发送)，只是大概率 EPERM
                if (!granted) Notify.show(R.string.cast_perm_nearby_hint);
                startCast();
            });

    public CastDialog() {
        scanTask = new ScanTask(this);
        body = new FormBody.Builder();
        body.add("device", Device.get().toString());
        body.add("config", Config.vod().toString());
        client = OkHttp.client(Constant.TIMEOUT_SYNC);
    }

    public static CastDialog create(PlayerManager player) {
        CastDialog dialog = new CastDialog();
        dialog.fm = player.isVod();
        dialog.video = CastVideo.create(player, dialog.fm ? player.getPosition() : C.TIME_UNSET);
        return dialog;
    }

    public CastDialog history(History history) {
        String id = history.getVodId();
        String fd = history.getVodId();
        if (fd.startsWith("/")) fd = Server.get().getAddress() + "/file" + fd.replace(Path.rootPath(), "");
        if (fd.startsWith("file")) fd = Server.get().getAddress() + "/" + fd.replace(Path.rootPath(), "").replace("://", "");
        if (fd.contains("127.0.0.1")) fd = fd.replace("127.0.0.1", Util.getIp());
        body.add("history", history.toString().replace(id, fd));
        return this;
    }

    public void show(FragmentActivity activity) {
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) if (f instanceof CastDialog) return;
        show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogDeviceBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        binding.scan.setVisibility(fm ? View.VISIBLE : View.GONE);
        setWidth(0.85f);
        ensureNearbyPermission();
        setRecyclerView();
        getDevice();
        showLoading();
    }

    /** Android 13+ 把 DLNA 投屏发现归到「附近的设备」权限组(NEARBY_WIFI_DEVICES)。
     *  未授权时 ColorOS/部分 ROM 会在内核层掐掉组播发送(sendto EPERM)。
     *  关键：必须在权限授予后再启动发现(init 会触发探针/组播发送)，
     *  否则探针永远在"未授权"态跑，测不出该权限到底能不能解 EPERM。 */
    private void ensureNearbyPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
            permLauncher.launch(Manifest.permission.NEARBY_WIFI_DEVICES);
        } else {
            startCast();
        }
    }

    private void startCast() {
        DLNACastManager.get().init(requireActivity());
        DLNACastManager.get().setDeviceListener(this);
    }

    @Override
    protected void initEvent() {
        binding.scan.setOnClickListener(v -> onScan());
        binding.refresh.setOnClickListener(v -> onRefresh());
        binding.empty.setOnClickListener(v -> onRefresh());
    }

    private void setRecyclerView() {
        binding.recycler.setHasFixedSize(false);
        binding.recycler.setAdapter(adapter = new DeviceAdapter(this).connected(Prefers.getString("cast_last_device")));
        binding.recycler.addItemDecoration(new SpaceItemDecoration(1, 16));
    }

    private void setRecyclerVisible() {
        boolean has = adapter.getItemCount() > 0;
        binding.recycler.setVisibility(has ? View.VISIBLE : View.GONE);
        if (has) {
            binding.progress.setVisibility(View.GONE);
            binding.empty.setVisibility(View.GONE);
            App.removeCallbacks(mEmpty);
        }
    }

    private void showLoading() {
        binding.progress.setVisibility(View.VISIBLE);
        binding.empty.setVisibility(View.GONE);
        App.removeCallbacks(mEmpty);
        App.post(mEmpty, 8000);
    }

    private void showEmpty() {
        if (binding == null || adapter.getItemCount() > 0) return;
        binding.progress.setVisibility(View.GONE);
        binding.empty.setVisibility(View.VISIBLE);
    }

    private void getDevice() {
        adapter.setItems(Device.getAll(), () -> {
            adapter.sort(DLNACastManager.get().getRegistered(), this::setRecyclerVisible);
            if (adapter.getItemCount() == 0) onRefresh();
            else DLNACastManager.get().search();
        });
    }

    private void onScan() {
        launcher.launch(new Intent(requireActivity(), ScanActivity.class));
    }

    private void onRefresh() {
        adapter.clear(() -> {
            Device.delete();
            if (fm) scanTask.start();
            DLNACastManager.get().search();
            adapter.sort(DLNACastManager.get().getRegistered(), this::setRecyclerVisible);
        });
        App.post(() -> DLNACastManager.get().search(), 2000);
        App.post(() -> DLNACastManager.get().search(), 4500);
        showLoading();
    }

    private void onCasted() {
        if (casting != null) {
            Prefers.put("cast_last_device", casting.getName());
            adapter.setConnected(casting.getName());
        }
        ((CastDialog.Listener) requireActivity()).onCasted();
        dismiss();
    }

    @Override
    public void onDeviceAdded(Device device) {
        binding.recycler.setVisibility(View.VISIBLE);
        adapter.sort(device);
    }

    @Override
    public void onDeviceRemoved(Device device) {
        adapter.remove(device);
    }

    @Override
    public void onFind(Device device) {
        binding.recycler.setVisibility(View.VISIBLE);
        adapter.sort(device);
    }

    @Override
    public void onFailure(@NonNull Call call, @NonNull IOException e) {
        App.post(() -> Notify.show(e.getMessage()));
    }

    @Override
    public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
        try (Response res = response) {
            if (res.body().string().equals("OK")) App.post(this::onCasted);
            else App.post(() -> Notify.show(R.string.device_offline));
        }
    }

    @Override
    public void onItemClick(Device item) {
        casting = item;
        if (item.isDLNA()) new DLNACast(video, this::onCasted).cast(item);
        else OkHttp.newCall(client, item.getIp().concat("/action?do=cast"), body.build()).enqueue(this);
    }

    @Override
    public boolean onLongClick(Device item) {
        return false;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        App.removeCallbacks(mEmpty);
        DLNACastManager.get().setDeviceListener(null);
        scanTask.stop();
    }

    private final ActivityResultLauncher<Intent> launcher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) scanTask.start(result.getData().getStringExtra("address"));
    });

    public interface Listener {

        void onCasted();
    }
}
