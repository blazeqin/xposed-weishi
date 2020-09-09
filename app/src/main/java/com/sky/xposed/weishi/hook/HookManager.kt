/*
 * Copyright (c) 2018. The sky Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sky.xposed.weishi.hook

import android.content.Context
import android.content.Intent
import android.os.Handler
import com.sky.xposed.common.data.CachePreferences
import com.sky.xposed.common.helper.ReceiverHelper
import com.sky.xposed.common.util.Alog
import com.sky.xposed.common.util.Pair
import com.sky.xposed.common.util.ToastUtil
import com.sky.xposed.weishi.BuildConfig
import com.sky.xposed.weishi.Constant
import com.sky.xposed.weishi.data.ObjectManager
import com.sky.xposed.weishi.data.UserConfigManager
import com.sky.xposed.weishi.hook.support.WeiShiHook
import com.squareup.picasso.Picasso
import com.tencent.bugly.crashreport.CrashReport
import de.robv.android.xposed.callbacks.XC_LoadPackage

class HookManager private constructor() {

    private lateinit var mContext: Context
    private lateinit var mHandler: Handler
    private lateinit var mLoadPackageParam: XC_LoadPackage.LoadPackageParam
    private lateinit var mCachePreferences: CachePreferences
    private lateinit var mUserConfigManager: UserConfigManager
    private lateinit var mObjectManager: ObjectManager
    private lateinit var mVersionManager: VersionManager
    private lateinit var mReceiverHelper: ReceiverHelper

    private var mWeiShiHook: WeiShiHook? = null

    companion object {

        private val HOOK_MANAGER by lazy { HookManager() }

        fun getInstance()= HOOK_MANAGER
    }

    //onCreate方法之前的操作
    fun initialization(context: Context, param: XC_LoadPackage.LoadPackageParam): HookManager {

        Picasso.setSingletonInstance(Picasso.Builder(context).build())

        mContext = context
        mHandler = AppHandler()
        mLoadPackageParam = param
        //获取sharedPreference里的所有数据
        mCachePreferences = CachePreferences(context, Constant.Name.WEI_SHI)
        //将sharedPreference里的数据取出来
        mUserConfigManager = UserConfigManager(this)
        //配置版本信息
        mVersionManager = VersionManager(this)
        mObjectManager = ObjectManager()

        // 注册监听 动态注册广播，给自己用
        mReceiverHelper = ReceiverHelper(context,
                ReceiverHelper.ReceiverCallback {
                    action, intent ->  onReceive(action, intent)
                },
                com.sky.xposed.common.Constant.Action.REFRESH_PREFERENCE)
        mReceiverHelper.registerReceiver()

        Alog.setDebug(BuildConfig.DEBUG)
        ToastUtil.getInstance().init(context)

        // 添加统计 添加会有问题，导致插件注入失败
//        CrashReport.initCrashReport(context, Constant.Bugly.APP_ID, BuildConfig.DEBUG)
//        CrashReport.setAppChannel(context, BuildConfig.FLAVOR)

        return this
    }

    fun getContext(): Context {
        return mContext
    }

    fun getHandler(): Handler {
        return mHandler
    }

    fun getLoadPackageParam(): XC_LoadPackage.LoadPackageParam {
        return mLoadPackageParam
    }

    fun getCachePreferences(): CachePreferences {
        return mCachePreferences
    }

    fun getUserConfigManager(): UserConfigManager {
        return mUserConfigManager
    }

    fun getObjectManager(): ObjectManager {
        return mObjectManager
    }

    fun getVersionManager(): VersionManager {
        return mVersionManager
    }

    fun handleLoadPackage() {

        if (!mVersionManager.isSupportVersion()) return

        mWeiShiHook = mVersionManager.getSupportWeiShiHook()
        mWeiShiHook!!.handleLoadPackage(getLoadPackageParam())
    }

    fun release() {

        // 释放监听
        mReceiverHelper.unregisterReceiver()
    }

    private fun onReceive(action: String, intent: Intent) {

        if (com.sky.xposed.common.Constant.Action.REFRESH_PREFERENCE == action) {

            // 获取刷新的值
            val data = intent.getSerializableExtra(
                    com.sky.xposed.common.Constant.Key.DATA) as ArrayList<Pair<String, Any>>

            for (item in data) {
                // 重新设置值
                mCachePreferences.putObject(item.first, item.second)
                mWeiShiHook?.onModifyValue(item.first, item.second)
            }
        }
    }

    private class AppHandler : Handler()
}