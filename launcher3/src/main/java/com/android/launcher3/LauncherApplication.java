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

import android.app.Application;

public class LauncherApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        //初始化LauncherAppState
        LauncherAppState.setApplicationContext(this);
        LauncherAppState.getInstance();
    }

    /**
     * 当终止应用程序对象时调用，不保证一定被调用
     * 即当被系统内核为了分配内存而终止该app时，这个方法不会被调用
     */
    @Override
    public void onTerminate() {
        super.onTerminate();
        //释放LauncherAppState中的资源
        LauncherAppState.getInstance().onTerminate();
    }
}