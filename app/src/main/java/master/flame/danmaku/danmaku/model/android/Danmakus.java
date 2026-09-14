/*
 * Copyright (C) 2013 Chen Hui <calmer91@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package master.flame.danmaku.danmaku.model.android;

import android.util.Log;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import master.flame.danmaku.danmaku.model.BaseDanmaku;
import master.flame.danmaku.danmaku.model.Danmaku;
import master.flame.danmaku.danmaku.model.IDanmakus;
import master.flame.danmaku.danmaku.util.function.Supplier;

public class Danmakus implements IDanmakus {
    public static final String TAG = "Danmakus";

    private ReadWriteLock itemsLock;
    private Collection<BaseDanmaku> items;

    private Danmakus subItems;

    private BaseDanmaku startItem, endItem;

    private BaseDanmaku endSubItem;

    private BaseDanmaku startSubItem;

    private int mSortType = ST_BY_TIME;

    private BaseComparator mComparator;

    private boolean mDuplicateMergingEnabled;

    public Danmakus() {
        this(ST_BY_TIME, false);
    }

    public Danmakus(int sortType) {
        this(sortType, false);
    }

    public Danmakus(int sortType, boolean duplicateMergingEnabled) {
        this(sortType, duplicateMergingEnabled, null);
    }

    public Danmakus(int sortType, boolean duplicateMergingEnabled, BaseComparator baseComparator) {
        this(sortType, duplicateMergingEnabled, baseComparator, true);
    }

    public Danmakus(int sortType, boolean duplicateMergingEnabled, BaseComparator baseComparator, boolean needLock) {
        BaseComparator comparator = null;
        if (sortType == ST_BY_TIME) {
            comparator = baseComparator == null ? new TimeComparator(duplicateMergingEnabled) : baseComparator;
        } else if (sortType == ST_BY_YPOS) {
            comparator = new YPosComparator(duplicateMergingEnabled);
        } else if (sortType == ST_BY_YPOS_DESC) {
            comparator = new YPosDescComparator(duplicateMergingEnabled);
        }
        if (comparator == null) {
            comparator = new TimeComparator(duplicateMergingEnabled);
        }

        if (sortType == ST_BY_LIST) {
            items = new LinkedList<>();
        } else {
            mDuplicateMergingEnabled = duplicateMergingEnabled;
            comparator.setDuplicateMergingEnabled(duplicateMergingEnabled);
            items = new TreeSet<>(comparator);
            mComparator = comparator;
        }

        if (needLock) {
            itemsLock = new ReentrantReadWriteLock();
        }

        mSortType = sortType;
    }

    public void setItemsLock(ReadWriteLock itemsLock) {
        this.itemsLock = itemsLock;
    }

    public Danmakus(Collection<BaseDanmaku> items) {
        setItems(items);
    }

    public Danmakus(boolean duplicateMergingEnabled) {
        this(ST_BY_TIME, duplicateMergingEnabled);
    }

    public void setItems(Collection<BaseDanmaku> items) {
        writeLock(() -> {
            if (mDuplicateMergingEnabled && mSortType != ST_BY_LIST) {
                this.items.clear();
                this.items.addAll(items);
            } else {
                this.items = items;
            }
            if (items instanceof List) {
                mSortType = ST_BY_LIST;
            }
        });
    }

    @Override
    public boolean addItem(BaseDanmaku item) {
        return writeLock(() -> {
            if (items != null) {
                try {
                    return items.add(item);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return false;
        });
    }

    @Override
    public boolean addAllItem(Collection<BaseDanmaku> items) {
        return writeLock(() -> {
            if (items != null) {
                try {
                    return this.items.addAll(items);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return false;
        });
    }

    @Override
    public boolean removeItem(BaseDanmaku item) {
        if (item == null) {
            return false;
        }
        if (item.isOutside()) {
            item.setVisibility(false);
        }

        return writeLock(()-> items.remove(item));
    }

    private Collection<BaseDanmaku> subset(long startTime, long endTime) {
        if (mSortType == ST_BY_LIST || items == null || items.isEmpty()) {
            return null;
        }
        if (subItems == null) {
            subItems = new Danmakus(mDuplicateMergingEnabled);
        }
        if (startSubItem == null) {
            startSubItem = createItem("start");
        }
        if (endSubItem == null) {
            endSubItem = createItem("end");
        }

        startSubItem.setTime(startTime);
        endSubItem.setTime(endTime);
        return readLock(()-> ((SortedSet<BaseDanmaku>) items).subSet(startSubItem, endSubItem));
    }

    @Override
    public IDanmakus subnew(long startTime, long endTime) {
        Collection<BaseDanmaku> subset = subset(startTime, endTime);
        if (subset == null || subset.isEmpty()) {
            return null;
        }
        LinkedList<BaseDanmaku> newSet = new LinkedList<>(subset);
        return new Danmakus(newSet);
    }

    @Override
    public IDanmakus sub(long startTime, long endTime) {
        if (items == null || items.isEmpty()) {
            return null;
        }
        if (subItems == null) {
            if(mSortType == ST_BY_LIST) {
                subItems = new Danmakus(Danmakus.ST_BY_LIST);
                subItems.setItems(items);
            } else {
                subItems = new Danmakus(mDuplicateMergingEnabled);
            }
        }
        if (mSortType == ST_BY_LIST) {
            return subItems;
        }
        if (startItem == null) {
            startItem = createItem("start");
        }
        if (endItem == null) {
            endItem = createItem("end");
        }

        startItem.setTime(startTime);
        endItem.setTime(endTime);
        SortedSet<BaseDanmaku> subNewItems = readLock(() -> ((SortedSet<BaseDanmaku>) items).subSet(startItem, endItem));
        subItems.setItems(subNewItems);
        return subItems;
    }

    private BaseDanmaku createItem(String text) {
        return new Danmaku(text);
    }

    public int size() {
        return items.size();
    }

    @Override
    public void clear() {
        writeLock(() -> {
            if (items != null) {
                items.clear();
            }

            if (subItems != null) {
                subItems = null;
                startItem = createItem("start");
                endItem = createItem("end");
            }
        });
    }

    @Override
    public BaseDanmaku first() {
        if (items == null || items.isEmpty()) {
            return null;
        }

        if (mSortType == ST_BY_LIST) {
            return ((LinkedList<BaseDanmaku>) items).peek();
        }
        return readLock(() -> ((SortedSet<BaseDanmaku>) items).first());
    }

    @Override
    public BaseDanmaku last() {
        if (items == null || items.isEmpty()) {
            return null;
        }

        if (mSortType == ST_BY_LIST) {
            return ((LinkedList<BaseDanmaku>) items).peekLast();
        }
        return readLock(() -> ((SortedSet<BaseDanmaku>) items).last());
    }

    @Override
    public boolean contains(BaseDanmaku item) {
        return this.items != null && this.items.contains(item);
    }

    @Override
    public boolean isEmpty() {
        return this.items == null || this.items.isEmpty();
    }

    private void setDuplicateMergingEnabled(boolean enable) {
        mComparator.setDuplicateMergingEnabled(enable);
        mDuplicateMergingEnabled = enable;
    }

    @Override
    public void setSubItemsDuplicateMergingEnabled(boolean enable) {
        mDuplicateMergingEnabled = enable;
        startItem = endItem = null;
        if (subItems == null) {
            subItems = new Danmakus(enable);
        }
        subItems.setDuplicateMergingEnabled(enable);
    }

    @Override
    public Collection<BaseDanmaku> getCollection() {
        return this.items;
    }

    @Override
    public void forEachSync(Consumer<? super BaseDanmaku, ?> consumer) {
        if (items instanceof ConcurrentSkipListSet) {
            forEach(consumer);
        } else {
            readLock(() -> forEach(consumer));
        }
    }

    @Override
    public void forEach(Consumer<? super BaseDanmaku, ?> consumer) {
        consumer.before();
        Iterator<BaseDanmaku> it = items.iterator();

        while (it.hasNext()) {
            BaseDanmaku next = it.next();
            if (next == null) {
                continue;
            }
            int action = consumer.accept(next);
            if (action == DefaultConsumer.ACTION_BREAK) {
                break;
            } else if (action == DefaultConsumer.ACTION_REMOVE) {
                it.remove();
            } else if (action == DefaultConsumer.ACTION_REMOVE_AND_BREAK) {
                it.remove();
                break;
            }
        }

        consumer.after();
    }

    protected void readLock(Runnable runnable) {
        Lock lock = getReadLock();
        lockRun(lock, runnable);
    }

    protected <T> T readLock(Supplier<T> supplier) {
        Lock lock = getReadLock();
        return lockRun(lock, supplier);
    }

    protected void writeLock(Runnable runnable) {
        Lock lock = getWriteLock();
        lockRun(lock, runnable);
    }

    protected <T> T writeLock(Supplier<T> supplier) {
        Lock lock = getWriteLock();
        return lockRun(lock, supplier);
    }

    protected Lock getReadLock() {
        if (itemsLock == null) {
            return null;
        }

        return itemsLock.readLock();
    }

    protected Lock getWriteLock() {
        if (itemsLock == null) {
            return null;
        }

        return itemsLock.writeLock();
    }
}