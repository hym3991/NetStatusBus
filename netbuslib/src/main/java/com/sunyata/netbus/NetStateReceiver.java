package com.sunyata.netbus;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.sunyata.netbus.annotation.Network;
import com.sunyata.netbus.type.NetType;
import com.sunyata.netbus.utils.Constrants;
import com.sunyata.netbus.utils.NetworkUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by 「孙晨」 on 2019/3/31 0031   18:31.
 * <p>
 * God bless me only
 * <p>
 * NetStateReceiver
 */

public class NetStateReceiver extends BroadcastReceiver {

    private NetType netType;//网络类型

    private Map<Object, List<MethodManager>> networkList;


    public NetStateReceiver() {
        netType = NetType.NONE;
        networkList = new HashMap<>();
    }


    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }
//        处理广播事件
        if (intent.getAction().equalsIgnoreCase(Constrants.ANDROID_NET_CHANGE_ACTION)) {
            netType = NetworkUtils.getNetType();
            post(netType);
        }
    }


    /**
     * 分发
     *
     * @param netType
     */
    private void post(NetType netType) {
        //所有的注册类
        Set<Object> set = networkList.keySet();
        for (Object clazz : set) {
            List<MethodManager> methodManagerList = networkList.get(clazz);
            executeInvoke(clazz, methodManagerList);
        }
    }

    private void executeInvoke(Object clazz, List<MethodManager> methodManagerList) {
        if (methodManagerList != null) {
            for (MethodManager method : methodManagerList) {
                if (method.getParameterClazz().isAssignableFrom(netType.getClass())) {
                    switch (method.getAnnotationNetType()) {
                        case AUTO:
                            invoke(method, clazz, netType);
                            break;

                        case WIFI:
                            if (netType == NetType.WIFI || netType == NetType.NONE)
                                invoke(method, clazz, netType);
                            break;

                        case MOBILE:
                            if (netType == NetType.MOBILE || netType == NetType.MOBILE)
                                invoke(method, clazz, netType);

                            break;

                        case NONE:
                            if (netType == NetType.NONE)
                                invoke(method, clazz, netType);

                        default:
                    }
                }
            }
        }
    }

    private void invoke(MethodManager method, Object getter, NetType netType) {
        Method execute = method.getMethod();
        try {
            execute.invoke(getter, netType);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void registerObserver(Object mContext) {
        List<MethodManager> methodList = networkList.get(mContext);
        if (methodList == null) {
//        开始添加
            methodList = findAnnotationMethod(mContext);
            networkList.put(mContext, methodList);
        }

//      若非LAUNCHER Activity则先执一遍方法
        if (mContext instanceof Activity) {
            Set<String> categories = ((Activity) mContext).getIntent().getCategories();
            if (categories != null) {
                for (String category : categories) {
                    //若为mainActivity
                    if (category.equalsIgnoreCase("android.intent.category.LAUNCHER")) {
                        return;
                    }
                }
            }
        }
        executeInvoke(mContext, networkList.get(mContext));
    }

    private List<MethodManager> findAnnotationMethod(Object mContext) {
        List<MethodManager> methodManagerList = new ArrayList<>();
//        获取到activity fragment
        Class<?> clazz = mContext.getClass();
        Method[] methods = clazz.getMethods();

        for (Method method : methods) {
            Network networkAnnotation = method.getAnnotation(Network.class);
            if (networkAnnotation == null) {
                continue;
            }

//            注解方法校验
            Type genericReturnType = method.getGenericReturnType();
            if (!"void".equalsIgnoreCase(genericReturnType.toString())) {
                Log.e(Constrants.LOG_TAG, "方法不是void");
                throw new IllegalArgumentException("方法不是void");
            }

            Class<?>[] parameterTypes = method.getParameterTypes();
            if (parameterTypes.length != 1) {
                throw new IllegalArgumentException(method.getName() + "方法只能有一个参数");
            }

            MethodManager methodManager = new MethodManager(parameterTypes[0], networkAnnotation.netType(), method);
            methodManagerList.add(methodManager);

        }
        return methodManagerList;
    }

    public void unRegisterObserver(Object mContext) {
        if (!networkList.isEmpty()) {
            networkList.remove(mContext);
        }
    }

    public void unRegisterAllObserver() {
        if (!networkList.isEmpty()) {
            networkList.clear();
        }
        NetStateBus.getDefault().getApplication().unregisterReceiver(this);
    }
}