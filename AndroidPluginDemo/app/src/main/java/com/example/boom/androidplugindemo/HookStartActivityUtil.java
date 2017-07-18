package com.example.boom.androidplugindemo;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Created by Boom on 2017/7/15.
 */

public class HookStartActivityUtil {
    private static final String EXTRA_ORGIN_INTENT = "EXTRA_ORGIN_INTENT";
    private String TAG = "HookStartActivityUtil";
    private Context mContext;
    private Class<?> mProxyClass;

    public HookStartActivityUtil(Context context, Class<?> proxyClass) {
        this.mContext = context.getApplicationContext();
        this.mProxyClass = proxyClass;
    }

    public void hookLaunchActivity() throws Exception {
        //3.4.1获取ActivityThread实例
        Class<?> atClass = Class.forName("android.app.ActivityThread");
        Field scatField = atClass.getDeclaredField("sCurrentActivityThread");
        scatField.setAccessible(true);
        Object sCurrentActivityThread = scatField.get(null);
        //3.4.1获取ActivityThread里面的mH
        Field mHField = atClass.getDeclaredField("mH");
        mHField.setAccessible(true);
        Object mHandler= mHField.get(sCurrentActivityThread);
        //3.4.4hook  handleLanchActivity()
        //给Handler设置CallBack回掉，也只能通过反射
        Class<?> handlerClass = Class.forName("android.os.Handler");
        Field mCallBackField= handlerClass.getDeclaredField("mCallback");
        mCallBackField.setAccessible(true);
        mCallBackField.set(mHandler,new HandlerCallBack());
    }
    private class HandlerCallBack implements Handler.Callback{
        @Override
        public boolean handleMessage(Message msg) {
            //每发一个消息都会走一次这个callBack发送
            if (msg.what==100){
                handleLaunchActivity(msg);
            }
            return false;
        }

        private void handleLaunchActivity(Message msg) {


            try {
                Object record = msg.obj;
                //1.从record获取过了安检的Intent
                Field intentField = record.getClass().getDeclaredField("intent");
                Intent safeIntent = (Intent) intentField.get(record);
                //2.从safeIntent中获取原来的orginIntent
                     Intent orginIntent = safeIntent.getParcelableExtra(EXTRA_ORGIN_INTENT);
                //3.重新设置回去
                if (orginIntent!=null){
                    intentField.set(record,orginIntent);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void hookStartActivity() throws Exception {
        //获取ActivityManagerNative里面的getDefault
        Class<?> amnClass = Class.forName("android.app.ActivityManagerNative");
        //获取属性
        Field gDefaultField = amnClass.getDeclaredField("gDefault");
        //设置权限
        gDefaultField.setAccessible(true);
        //因为是static所以可以设置为NULL
        Object gDefault = gDefaultField.get(null);

        //获取getDefault()中的mInstance属性
        Class<?> singletonClass = Class.forName("android.util.Singleton");
        Field mInstanceField = singletonClass.getDeclaredField("mInstance");
        mInstanceField.setAccessible(true);
        //得到属性的值  因为不是static 所以不能设置为空
        Object iamInstance = mInstanceField.get(gDefault);

        Class<?> iamClass = Class.forName("android.app.IActivityManager");
        Proxy.newProxyInstance(HookStartActivityUtil.class.getClassLoader()
                , new Class[]{iamClass}
                , new StartActivityInvocationHandler(iamInstance));

        //重新指定
        mInstanceField.set(gDefault, iamInstance);
    }

    private class StartActivityInvocationHandler implements InvocationHandler {
        //方法执行者
        private Object object;

        public StartActivityInvocationHandler(Object object) {
            this.object = object;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            Log.e(TAG, method.getName());
            //替换Intent,过AndroidMainfest.xml检测
            if (method.getName().equals("startActivity")) {
                //1.首先获取原来的Intent
                Intent orginIntent = (Intent) args[2];
                //2.创建一个安全的
                Intent safeIntent = new Intent(mContext, mProxyClass);
                args[2] = safeIntent;
                //3.绑定原来的Intent
                safeIntent.putExtra(EXTRA_ORGIN_INTENT, orginIntent);
            }
            return method.invoke(object, args);
        }
    }
}
