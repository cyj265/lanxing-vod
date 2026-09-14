package com.xunlei.downloadlib.parameter;

public class XLTaskInfo {

    public String mCid;
    public String mFileName;
    public String mGcid;
    public int mAddedHighSourceState;
    public int mAdditionalResCount;
    public int mAdditionalResType;
    public int mDcdnState;
    public int mErrorCode;
    public int mInfoLen;
    public int mLanPeerResState;
    public int mOriginErrcode;
    public int mQueryIndexStatus;
    public int mTaskStatus;
    public long mAdditionalResDCDNBytes;
    public long mAdditionalResDCDNSpeed;
    public long mAdditionalResPeerBytes;
    public long mAdditionalResPeerSpeed;
    public long mAdditionalResVipRecvBytes;
    public long mAdditionalResVipSpeed;
    public long mCheckedSize;
    public long mDownloadFileCount;
    public long mDownloadSize;
    public long mDownloadSpeed;
    public long mFileSize;
    public long mOriginRecvBytes;
    public long mOriginSpeed;
    public long mP2PRecvBytes;
    public long mP2PSpeed;
    public long mP2SRecvBytes;
    public long mP2SSpeed;
    public long mScdnRecvBytes;
    public long mScdnSpeed;
    public long mTaskId;
    public long mTotalFileCount;

    public int getTaskStatus() {
        return mTaskStatus;
    }

    public void setTaskStatus(int taskStatus) {
        this.mTaskStatus = taskStatus;
    }

    public String getErrorMsg() {
        return ErrorCode.get(mErrorCode);
    }
}
