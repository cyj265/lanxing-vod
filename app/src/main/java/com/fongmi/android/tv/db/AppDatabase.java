package com.fongmi.android.tv.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Device;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Keep;
import com.fongmi.android.tv.bean.Live;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.db.dao.ConfigDao;
import com.fongmi.android.tv.db.dao.DeviceDao;
import com.fongmi.android.tv.db.dao.HistoryDao;
import com.fongmi.android.tv.db.dao.KeepDao;
import com.fongmi.android.tv.db.dao.LiveDao;
import com.fongmi.android.tv.db.dao.SiteDao;
import com.fongmi.android.tv.db.dao.TrackDao;
import com.fongmi.android.tv.utils.FileUtil;
import com.github.catvod.utils.Path;

import java.io.File;

@Database(entities = {Keep.class, Site.class, Live.class, Track.class, Config.class, Device.class, History.class}, version = AppDatabase.VERSION)
public abstract class AppDatabase extends RoomDatabase {

    public static final int VERSION = 35;
    public static final String NAME = "tv";
    public static final String SYMBOL = "@@@";

    private static volatile AppDatabase instance;

    public static synchronized AppDatabase get() {
        if (instance == null) instance = create(App.get());
        return instance;
    }

    /** Compatibility shim for newer HomeActivity call sites. */
    public static void backup() {
        BackupManager.backup();
    }

    private static AppDatabase create(Context context) {
        backupBeforeDestructiveMigration(context);
        return Room.databaseBuilder(context, AppDatabase.class, NAME)
                .addMigrations(Migrations.MIGRATION_30_31)
                .addMigrations(Migrations.MIGRATION_31_32)
                .addMigrations(Migrations.MIGRATION_32_33)
                .addMigrations(Migrations.MIGRATION_33_34)
                .addMigrations(Migrations.MIGRATION_34_35)
                .fallbackToDestructiveMigration(true)
                .allowMainThreadQueries().build();
    }

    /**
     * Room 只提供了 30 之后的迁移链，同时开了 fallbackToDestructiveMigration。
     * 低于 30 的旧库升级时没有迁移路径，历史/收藏/直播源会被静默清空，
     * 因此这里在开库前先把原始 db 文件备份一份，至少数据不会真的丢失。
     */
    private static void backupBeforeDestructiveMigration(Context context) {
        File db = context.getDatabasePath(NAME);
        if (db == null || !db.exists()) return;
        int version = readDbVersion(db);
        if (version <= 0 || version >= Migrations.EARLIEST_VERSION) return;
        try {
            File target = new File(Path.backup(), NAME + "-v" + version + ".db");
            FileUtil.copyAtomically(db, target);
        } catch (Throwable ignored) {
        }
    }

    private static int readDbVersion(File db) {
        try (SQLiteDatabase sqlite = SQLiteDatabase.openDatabase(db.getPath(), null, SQLiteDatabase.OPEN_READONLY)) {
            return sqlite.getVersion();
        } catch (Throwable e) {
            return 0;
        }
    }

    public abstract KeepDao getKeepDao();

    public abstract SiteDao getSiteDao();

    public abstract LiveDao getLiveDao();

    public abstract TrackDao getTrackDao();

    public abstract ConfigDao getConfigDao();

    public abstract DeviceDao getDeviceDao();

    public abstract HistoryDao getHistoryDao();
}
