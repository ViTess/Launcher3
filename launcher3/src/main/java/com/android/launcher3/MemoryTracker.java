/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.launcher3;

import android.app.ActivityManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.*;
import android.util.Log;
import android.util.LongSparseArray;

import java.util.ArrayList;
import java.util.List;

/**
 * 内存监控服务
 */
public class MemoryTracker extends Service {
    public static final String TAG = MemoryTracker.class.getSimpleName();
    public static final String ACTION_START_TRACKING = "com.android.launcher3.action.START_TRACKING";

    private static final long UPDATE_RATE = 5000;

    private static final int MSG_START = 1;
    private static final int MSG_STOP = 2;
    private static final int MSG_UPDATE = 3;

    /**
     * 静态内部类，记录进程内存信息的bean
     */
    public static class ProcessMemInfo {
        public int pid;//进程id
        public String name;//进程名
        public long startTime;//开始时间..?
        public long currentPss, currentUss;//当前的pss和uss..?
        //pss理解为uss+比例分配共享库中占用的内存
        public long[] pss = new long[256];//Proportional Set Size 实际使用的物理内存（比例分配共享库占用的内存）
        public long[] uss = new long[256];//Unique Set Size 进程独自占用的物理内存（不包含共享库占用的内存）
            //= new Meminfo[(int) (30 * 60 / (UPDATE_RATE / 1000))]; // 30 minutes
        public long max = 1;
        public int head = 0;
        public ProcessMemInfo(int pid, String name, long start) {
            this.pid = pid;
            this.name = name;
            this.startTime = start;
        }
        public long getUptime() {
            return System.currentTimeMillis() - startTime;
        }
    };
    public final LongSparseArray<ProcessMemInfo> mData = new LongSparseArray<ProcessMemInfo>();
    //这里保存了Long和int类型的pid，为什么？
    public final ArrayList<Long> mPids = new ArrayList<Long>();
    private int[] mPidsArray = new int[0];//暂时不理解这里设置一个0长度数组的意义

    private final Object mLock = new Object();//线程同步锁用的

    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message m) {
            //注意这里多处的removeMessages(MSG_UPDATE)
            //是为了避免消息队列中出现多次的MSG_UPDATE
            //确保MSG_UPDATE不被多次调用
            switch (m.what) {
                case MSG_START:
                    mHandler.removeMessages(MSG_UPDATE);
                    mHandler.sendEmptyMessage(MSG_UPDATE);
                    break;
                case MSG_STOP:
                    mHandler.removeMessages(MSG_UPDATE);
                    break;
                case MSG_UPDATE:
                    update();
                    mHandler.removeMessages(MSG_UPDATE);
                    mHandler.sendEmptyMessageDelayed(MSG_UPDATE, UPDATE_RATE);
                    break;
            }
        }
    };

    ActivityManager mAm;

    /**
     * 这个静态方法用于开启当前这个内存监测的服务
     * @param context
     * @param name
     */
    public static void startTrackingMe(Context context, String name) {
        //首次startService会调用service的onCreate方法
        //多次调用startService会调用onStartCommand()
        context.startService(new Intent(context, MemoryTracker.class)
                .setAction(MemoryTracker.ACTION_START_TRACKING)
                .putExtra("pid", android.os.Process.myPid())
                .putExtra("name", name)
        );
    }

    /**
     * 根据进程id获取其内存消息
     * @param pid
     * @return
     */
    public ProcessMemInfo getMemInfo(int pid) {
        return mData.get(pid);
    }

    public int[] getTrackedProcesses() {
        return mPidsArray;
    }

    /**
     * 就是将对应的进程信息放进list和数组里
     * @param pid
     * @param name
     * @param start
     */
    public void startTrackingProcess(int pid, String name, long start) {
        synchronized (mLock) {
            final Long lpid = new Long(pid);

            if (mPids.contains(lpid)) return;

            mPids.add(lpid);
            updatePidsArrayL();

            mData.put(pid, new ProcessMemInfo(pid, name, start));
        }
    }

    void updatePidsArrayL() {
        final int N = mPids.size();
        mPidsArray = new int[N];
        StringBuffer sb = new StringBuffer("Now tracking processes: ");
        for (int i=0; i<N; i++) {
            final int p = mPids.get(i).intValue();
            mPidsArray[i] = p;
            sb.append(p); sb.append(" ");
        }
        Log.v(TAG, sb.toString());
    }

    /**
     * 最核心的方法，根据pid获取内存数据
     * 然而用到了synchronized不知道是为什么
     */
    void update() {
        synchronized (mLock) {
            Debug.MemoryInfo[] dinfos = mAm.getProcessMemoryInfo(mPidsArray);
            for (int i=0; i<dinfos.length; i++) {
                Debug.MemoryInfo dinfo = dinfos[i];
                if (i > mPids.size()) {
                    Log.e(TAG, "update: unknown process info received: " + dinfo);
                    break;
                }
                final long pid = mPids.get(i).intValue();
                final ProcessMemInfo info = mData.get(pid);
                info.head = (info.head+1) % info.pss.length;
                info.pss[info.head] = info.currentPss = dinfo.getTotalPss();
                info.uss[info.head] = info.currentUss = dinfo.getTotalPrivateDirty();
                if (info.currentPss > info.max) info.max = info.currentPss;
                if (info.currentUss > info.max) info.max = info.currentUss;
                // Log.v(TAG, "update: pid " + pid + " pss=" + info.currentPss + " uss=" + info.currentUss);
                if (info.currentPss == 0) {
                    Log.v(TAG, "update: pid " + pid + " has pss=0, it probably died");
                    mData.remove(pid);
                }
            }
            for (int i=mPids.size()-1; i>=0; i--) {
                final long pid = mPids.get(i).intValue();
                if (mData.get(pid) == null) {
                    mPids.remove(i);
                    updatePidsArrayL();
                }
            }
        }
    }

    /**
     * 这里是把跟当前包名相同的service和process的信息都提取出来记录到内部的list和数组里
     */
    @Override
    public void onCreate() {
        mAm = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);

        // catch up in case we crashed but other processes are still running
        List<ActivityManager.RunningServiceInfo> svcs = mAm.getRunningServices(256);
        for (ActivityManager.RunningServiceInfo svc : svcs) {
            if (svc.service.getPackageName().equals(getPackageName())) {
                Log.v(TAG, "discovered running service: " + svc.process + " (" + svc.pid + ")");
                startTrackingProcess(svc.pid, svc.process,
                        System.currentTimeMillis() - (SystemClock.elapsedRealtime() - svc.activeSince));
            }
        }

        List<ActivityManager.RunningAppProcessInfo> procs = mAm.getRunningAppProcesses();
        for (ActivityManager.RunningAppProcessInfo proc : procs) {
            final String pname = proc.processName;
            if (pname.startsWith(getPackageName())) {
                Log.v(TAG, "discovered other running process: " + pname + " (" + proc.pid + ")");
                startTrackingProcess(proc.pid, pname, System.currentTimeMillis());
            }
        }
    }

    @Override
    public void onDestroy() {
        mHandler.sendEmptyMessage(MSG_STOP);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.v(TAG, "Received start id " + startId + ": " + intent);

        if (intent != null) {
            if (ACTION_START_TRACKING.equals(intent.getAction())) {
                final int pid = intent.getIntExtra("pid", -1);
                final String name = intent.getStringExtra("name");
                final long start = intent.getLongExtra("start", System.currentTimeMillis());
                startTrackingProcess(pid, name, start);
            }
        }

        mHandler.sendEmptyMessage(MSG_START);

        return START_STICKY;
    }

    public class MemoryTrackerInterface extends Binder {
        MemoryTracker getService() {
            return MemoryTracker.this;
        }
    }

    private final IBinder mBinder = new MemoryTrackerInterface();

    public IBinder onBind(Intent intent) {
        mHandler.sendEmptyMessage(MSG_START);

        return mBinder;
    }
}
