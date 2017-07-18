package com.example.boom.androidplugindemo;

import android.app.Application;

/**
 * Created by Boom on 2017/7/15.
 */

public class BaseApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        HookStartActivityUtil hookStartActivityUtil = new HookStartActivityUtil(this,ProxyActivity.class);
        try {
            hookStartActivityUtil.hookStartActivity();
            hookStartActivityUtil.hookLaunchActivity();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
