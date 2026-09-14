package com.xunlei.downloadlib.parameter;

import com.xunlei.downloadlib.Util;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class TorrentInfo {

    public TorrentFileInfo[] mSubFileInfo;
    public String mMultiFileBaseFolder;
    public boolean mIsMultiFiles;
    public String mInfoHash;
    public int mFileCount;
    public File mFile;

    public TorrentInfo(File file) {
        this.mFile = file;
    }

    public File getFile() {
        return mFile;
    }

    private List<TorrentFileInfo> getSubFileList() {
        return mSubFileInfo == null ? Collections.emptyList() : Arrays.asList(mSubFileInfo);
    }

    public List<TorrentFileInfo> getMedias() {
        return getSubFileList().stream().filter(item -> Util.isMedia(item.getExt(), item.getFileSize())).map(item -> item.file(getFile())).sorted().toList();
    }
}
