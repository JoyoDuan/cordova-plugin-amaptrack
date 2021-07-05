package com.joyo.cordova.track;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.amap.api.track.AMapTrackClient;
import com.amap.api.track.ErrorCode;
import com.amap.api.track.OnTrackLifecycleListener;
import com.amap.api.track.TrackParam;
import com.amap.api.track.query.model.AddTerminalRequest;
import com.amap.api.track.query.model.AddTerminalResponse;
import com.amap.api.track.query.model.AddTrackRequest;
import com.amap.api.track.query.model.AddTrackResponse;
import com.amap.api.track.query.model.QueryTerminalRequest;
import com.amap.api.track.query.model.QueryTerminalResponse;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * 轨迹服务Cordova插件
 *
 * @Author JoyoDuan
 * @Date 2021/6/30
 * @Description:
 */
public class AMapTrackPlugin extends CordovaPlugin {
    private static final String TAG = "AMapTrackPlugin";
    private static final String CHANNEL_ID_SERVICE_RUNNING = "CHANNEL_ID_SERVICE_RUNNING";

    // 上下文
    Context context = null;
    // 回调内容上下文
    private CallbackContext callbackContext;

    // 高德地图轨迹Client
    private AMapTrackClient aMapTrackClient;

    // 服务id，必填，每个方法都需要
    private long serviceId;
    // 设备id，无需传入，因为查询终端是根据name查询的
    private long terminalId;
    // 设备名称，必填，如果传入的name在高德没有注册，那么会自动注册并返回terminalId
    private String terminalName;
    // 轨迹id，非必填，如果没有请传入0，会自动注册一条轨迹
    private long trackId;
    // 定位时间间隔，单位：秒
    private int locationInterval = 2;
    // 上传时间间隔，单位：秒
    private int uploadInterval = 20;
    // 是否上传轨迹到指定轨迹，false则上传为终端的散点位置
    private boolean uploadToTrack = false;


    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        // 上下文
        context = cordova.getActivity().getApplicationContext();

        // 初始化轨迹服务
        initTrack();

        super.initialize(cordova, webView);
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        this.callbackContext = callbackContext;

        switch (action) {
            case "startTrack":
                // 服务id
                serviceId = args.getLong(0);
                // 终端名称
                terminalName = args.getString(1);
                // 轨迹id，没有可为0
                trackId = args.getLong(2);
                // 定位间隔
                locationInterval = args.getInt(3);
                // 上传间隔
                uploadInterval = args.getInt(4);
                // 是否上传轨迹到指定轨迹，false则上传为终端的散点位置
                uploadToTrack = args.getBoolean(5);

                startTrack();
                return true;
            case "stopTrack":
                stopTrack();
                return true;
            case "startGather":
                // 轨迹id，没有可为0
                trackId = args.getLong(0);

                startGather();
                return true;
            case "stopGather":
                stopGather();
                return true;
        }

        return false;
    }

    /**
     * 初始化猎鹰服务
     *
     * @Author JoyoDuan
     * @Date 2021/6/30
     * @Description:
     */
    private void initTrack() {
        // 没有创建轨迹服务
        if (aMapTrackClient == null) {
            aMapTrackClient = new AMapTrackClient(context);
            aMapTrackClient.setInterval(locationInterval, uploadInterval);
        }
    }

    /**
     * 启动猎鹰服务
     *
     * @Author JoyoDuan
     * @Date 2021/6/30
     * @Description:
     */
    private void startTrack() {
        aMapTrackClient.queryTerminal(new QueryTerminalRequest(serviceId, terminalName), new SimpleOnTrackListener() {
            @Override
            public void onQueryTerminalCallback(QueryTerminalResponse queryTerminalResponse) {
                if (queryTerminalResponse.isSuccess()) {
                    if (queryTerminalResponse.isTerminalExist()) {
                        // 当前终端已经创建过，直接使用查询到的terminal id
                        terminalId = queryTerminalResponse.getTid();

                        // 上传轨迹点到轨迹
                        if (uploadToTrack) {
                            // 没有轨迹id，新增轨迹
                            if (trackId == 0) {
                                aMapTrackClient.addTrack(new AddTrackRequest(serviceId, terminalId), new SimpleOnTrackListener() {
                                    @Override
                                    public void onAddTrackCallback(AddTrackResponse addTrackResponse) {
                                        if (addTrackResponse.isSuccess()) {
                                            // trackId需要在启动服务后设置才能生效，因此这里不设置，而是在startGather之前设置了track id
                                            trackId = addTrackResponse.getTrid();
                                            TrackParam trackParam = new TrackParam(serviceId, terminalId);
                                            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                trackParam.setNotification(createNotification());
                                            }
                                            aMapTrackClient.startTrack(trackParam, onTrackListener);
                                        } else {
                                            // Toast.makeText(cordova.getActivity(), "网络请求失败，" + addTrackResponse.getErrorMsg(), Toast.LENGTH_SHORT).show();
                                            callbackContext.error("AMapTrackClient addTrackResponse is error: " + addTrackResponse.getErrorMsg());
                                        }
                                    }
                                });
                            } else {
                                // 上传到指定trackId的轨迹
                                TrackParam trackParam = new TrackParam(serviceId, terminalId);
                                if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    trackParam.setNotification(createNotification());
                                }
                                aMapTrackClient.startTrack(trackParam, onTrackListener);
                            }
                        } else {
                            // 不指定track id，上报的轨迹点是该终端的散点轨迹
                            TrackParam trackParam = new TrackParam(serviceId, terminalId);
                            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                trackParam.setNotification(createNotification());
                            }
                            aMapTrackClient.startTrack(trackParam, onTrackListener);
                        }
                    } else {
                        // 当前终端是新终端，还未创建过，创建该终端并使用新生成的terminal id
                        aMapTrackClient.addTerminal(new AddTerminalRequest(terminalName, serviceId), new SimpleOnTrackListener() {
                            @Override
                            public void onCreateTerminalCallback(AddTerminalResponse addTerminalResponse) {
                                if (addTerminalResponse.isSuccess()) {
                                    terminalId = addTerminalResponse.getTid();
                                    TrackParam trackParam = new TrackParam(serviceId, terminalId);
                                    if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        trackParam.setNotification(createNotification());
                                    }
                                    aMapTrackClient.startTrack(trackParam, onTrackListener);
                                } else {
                                    // Toast.makeText(cordova.getActivity(), "网络请求失败，" + addTerminalResponse.getErrorMsg(), Toast.LENGTH_SHORT).show();
                                    callbackContext.error("MapTrackClient addTerminal is error: " + addTerminalResponse.getErrorMsg());
                                }
                            }
                        });
                    }
                } else {
                    // Toast.makeText(cordova.getActivity(), "网络请求失败，" + queryTerminalResponse.getErrorMsg(), Toast.LENGTH_SHORT).show();
                    callbackContext.error("MapTrackClient QueryTerminal is error: " + queryTerminalResponse.getErrorMsg());
                }
            }
        });
    }

    /**
     * 停止猎鹰服务
     *
     * @Author JoyoDuan
     * @Date 2021/6/30
     * @Description:
     */
    private void stopTrack() {
        aMapTrackClient.stopTrack(new TrackParam(serviceId, terminalId), onTrackListener);
    }

    /**
     * 开始收集轨迹点
     *
     * @Author JoyoDuan
     * @Date 2021/6/30
     * @Description:
     */
    private void startGather() {
        aMapTrackClient.setTrackId(trackId);
        aMapTrackClient.startGather(onTrackListener);
    }


    /**
     * 停止收集轨迹点
     *
     * @Author JoyoDuan
     * @Date 2021/6/30
     * @Description:
     */
    private void stopGather() {
        aMapTrackClient.stopGather(onTrackListener);
    }

    /**
     * 轨迹生命周期监听器
     *
     * @Author JoyoDuan
     * @Date 2021/6/30
     * @Description:
     */
    public OnTrackLifecycleListener onTrackListener = new SimpleOnTrackLifecycleListener() {
        @Override
        public void onBindServiceCallback(int status, String msg) {
            Log.w(TAG, "onBindServiceCallback, status: " + status + ", msg: " + msg);
        }

        @Override
        public void onStartTrackCallback(int status, String msg) {
            if (status == ErrorCode.TrackListen.START_TRACK_SUCEE || status == ErrorCode.TrackListen.START_TRACK_SUCEE_NO_NETWORK) {
                // 成功启动
                // Toast.makeText(cordova.getActivity(), "启动服务成功", Toast.LENGTH_SHORT).show();

                JSONObject jsonObject = new JSONObject();
                try {
                    jsonObject.put("status", status);
                    jsonObject.put("msg", msg);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                // 返回成功状态给js
                callbackContext.success(jsonObject);
            } else if (status == ErrorCode.TrackListen.START_TRACK_ALREADY_STARTED) {
                // 已经启动
                // Toast.makeText(cordova.getActivity(), "服务已经启动", Toast.LENGTH_SHORT).show();

                JSONObject jsonObject = new JSONObject();
                try {
                    jsonObject.put("status", status);
                    jsonObject.put("msg", msg);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                // 返回成功状态给js
                callbackContext.success(jsonObject);
            } else {
                Log.w(TAG, "error onStartTrackCallback, status: " + status + ", msg: " + msg);
                // Toast.makeText(cordova.getActivity(),
                //         "error onStartTrackCallback, status: " + status + ", msg: " + msg,
                //         Toast.LENGTH_LONG).show();
                callbackContext.error("error onStartTrackCallback, status: " + status + ", msg: " + msg);
            }
        }

        @Override
        public void onStopTrackCallback(int status, String msg) {
            if (status == ErrorCode.TrackListen.STOP_TRACK_SUCCE) {
                // 成功停止
                // Toast.makeText(cordova.getActivity(), "停止服务成功", Toast.LENGTH_SHORT).show();

                JSONObject jsonObject = new JSONObject();
                try {
                    jsonObject.put("status", status);
                    jsonObject.put("msg", msg);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                // 返回成功状态给js
                callbackContext.success(jsonObject);
            } else {
                Log.w(TAG, "error onStopTrackCallback, status: " + status + ", msg: " + msg);
                // Toast.makeText(cordova.getActivity(),
                //         "error onStopTrackCallback, status: " + status + ", msg: " + msg,
                //         Toast.LENGTH_LONG).show();
                callbackContext.error("error onStopTrackCallback, status: " + status + ", msg: " + msg);

            }
        }

        @Override
        public void onStartGatherCallback(int status, String msg) {
            if (status == ErrorCode.TrackListen.START_GATHER_SUCEE) {
                // Toast.makeText(cordova.getActivity(), "定位采集开启成功", Toast.LENGTH_SHORT).show();

                JSONObject jsonObject = new JSONObject();
                try {
                    jsonObject.put("status", status);
                    jsonObject.put("msg", msg);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                // 返回成功状态给js
                callbackContext.success(jsonObject);
            } else if (status == ErrorCode.TrackListen.START_GATHER_ALREADY_STARTED) {
                // Toast.makeText(cordova.getActivity(), "定位采集已经开启", Toast.LENGTH_SHORT).show();

                JSONObject jsonObject = new JSONObject();
                try {
                    jsonObject.put("status", status);
                    jsonObject.put("msg", msg);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                // 返回成功状态给js
                callbackContext.success(jsonObject);
            } else {
                Log.w(TAG, "error onStartGatherCallback, status: " + status + ", msg: " + msg);
                // Toast.makeText(cordova.getActivity(),
                //         "error onStartGatherCallback, status: " + status + ", msg: " + msg,
                //         Toast.LENGTH_LONG).show();
                callbackContext.error("error onStartGatherCallback, status: " + status + ", msg: " + msg);
            }
        }

        @Override
        public void onStopGatherCallback(int status, String msg) {
            if (status == ErrorCode.TrackListen.STOP_GATHER_SUCCE) {
                // Toast.makeText(cordova.getActivity(), "定位采集停止成功", Toast.LENGTH_SHORT).show();

                JSONObject jsonObject = new JSONObject();
                try {
                    jsonObject.put("status", status);
                    jsonObject.put("msg", msg);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                // 返回成功状态给js
                callbackContext.success(jsonObject);
            } else {
                Log.w(TAG, "error onStopGatherCallback, status: " + status + ", msg: " + msg);
                // Toast.makeText(cordova.getActivity(),
                //         "error onStopGatherCallback, status: " + status + ", msg: " + msg,
                //         Toast.LENGTH_LONG).show();
                callbackContext.error("error onStopGatherCallback, status: " + status + ", msg: " + msg);
            }
        }
    };

    /**
     * 在8.0以上手机，如果app切到后台，系统会限制定位相关接口调用频率
     * 可以在启动轨迹上报服务时提供一个通知，这样Service启动时会使用该通知成为前台Service，可以避免此限制
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private Notification createNotification() {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) cordova.getActivity().getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID_SERVICE_RUNNING, "app service", NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(channel);
            builder = new Notification.Builder(context, CHANNEL_ID_SERVICE_RUNNING);
        } else {
            builder = new Notification.Builder(context);
        }
        Intent nfIntent = new Intent(cordova.getActivity(), cordova.getActivity().getClass());
        nfIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        builder.setContentIntent(PendingIntent.getActivity(cordova.getActivity(), 0, nfIntent, 0))
                .setContentTitle("学车小王子")
                .setContentText("猎鹰运行中");
        return builder.build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        aMapTrackClient.stopTrack(new TrackParam(serviceId, terminalId), new SimpleOnTrackLifecycleListener());
    }
}
