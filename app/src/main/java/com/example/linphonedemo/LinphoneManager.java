package com.example.linphonedemo;

import android.content.Context;
import android.content.res.Resources;
import android.media.AudioManager;
import android.util.Log;
import android.widget.Toast;

import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneAuthInfo;
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCallStats;
import org.linphone.core.LinphoneChatMessage;
import org.linphone.core.LinphoneChatRoom;
import org.linphone.core.LinphoneContent;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCoreException;
import org.linphone.core.LinphoneCoreFactory;
import org.linphone.core.LinphoneCoreListener;
import org.linphone.core.LinphoneEvent;
import org.linphone.core.LinphoneFriend;
import org.linphone.core.LinphoneFriendList;
import org.linphone.core.LinphoneInfoMessage;
import org.linphone.core.LinphoneNatPolicy;
import org.linphone.core.LinphoneProxyConfig;
import org.linphone.core.PayloadType;
import org.linphone.core.PublishState;
import org.linphone.core.SubscriptionState;
import org.linphone.core.TunnelConfig;
import org.linphone.core.VideoSize;
import org.linphone.mediastream.video.capture.hwconf.AndroidCameraConfiguration;
import org.linphone.tools.H264Helper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by sunyao1 on 2017/8/15.
 */

public class LinphoneManager implements LinphoneCoreListener {
    private static LinphoneManager instance;
    private LinphoneCore mLc;
    private Context mServiceContext;
    private AudioManager mAudioManager;


    private Timer mTimer;
    private Resources mR;

    private String basePath;
    private String mLinphoneFactoryConfigFile;
    private String mLinphoneConfigFile;
    private String mDynamicConfigFile;
//    private String mRingSoundFile;
//    private String mLinphoneRootCaFile;

    LinphoneManager(Context context) {
        mServiceContext = context;
        basePath = context.getFilesDir().getAbsolutePath();
        mLinphoneFactoryConfigFile = basePath + "/linphonerc";
        mLinphoneConfigFile = basePath + "/.linphonerc";
        mDynamicConfigFile = basePath + "/assistant_create.rc";
//        //铃声
//        mRingSoundFile = basePath + "/ringtone.mkv";
//        mLinphoneRootCaFile = basePath + "/rootca.pem";
        mR = context.getResources();
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }


    public synchronized static LinphoneManager createAndStart(Context context) {
        if (instance != null) {
            throw new RuntimeException("Linphone Manager is already initialized.");
        }
        instance = new LinphoneManager(context);
        instance.startLibLinphone(context);
        return instance;
    }

    private synchronized void startLibLinphone(Context context) {
        try {

            copyIfNotExist(R.raw.linphonerc_default, mLinphoneConfigFile);
            copyFromPackage(R.raw.linphonerc_factory, new File(mLinphoneFactoryConfigFile).getName());
            copyFromPackage(R.raw.assistant_create, new File(mDynamicConfigFile).getName());

            //创建core
            LinphoneCoreFactory.instance().setDebugMode(true, "myPhone");
            mLc = LinphoneCoreFactory.instance().createLinphoneCore(
                    this
                    , context);
            //iterate   处理注册 sip消息 ...
            startIterate();
            //初始化配置
            initLiblinphone();

        } catch (LinphoneCoreException | IOException e) {
            e.printStackTrace();
        }
    }

    private void startIterate() {
        TimerTask lTask = new TimerTask() {
            @Override
            public void run() {
                UIThreadDispatcher.dispatch(new Runnable() {
                    @Override
                    public void run() {
                        if (mLc != null) {
                            mLc.iterate();
                        }
                    }
                });
            }
        };

        Timer mTimer = new Timer("Linphone scheduler");
        mTimer.schedule(lTask, 0, 20);
    }

    private void initLiblinphone() {
        Log.d("myPhone", "initLiblinphone: init!!!!!!!!!!!!!!!");
//        H264Helper.setH264Mode(H264Helper.MODE_AUTO, mLc);
//        mLc.setMaxCalls(1);
        mLc.enableVideo(true, true);
        mLc.setNetworkReachable(true);
        mLc.enableSpeaker(true);
        mLc.setUserAgent("LinphoneAndroid", "3.10.2");
//        mLc.setRootCA(mLinphoneRootCaFile);
        //set video cam
        int camId = 0;
        AndroidCameraConfiguration.AndroidCamera[] cameras = AndroidCameraConfiguration.retrieveCameras();
        for (AndroidCameraConfiguration.AndroidCamera cam : cameras) {
            if (cam.frontFacing) {
                camId = cam.id;
                break;
            }
        }
        mLc.setVideoDevice(camId);
        //auto accept video call
        mLc.setVideoPolicy(true, true);
//        mLc.getConfig().loadXmlFile(mDynamicConfigFile);

        //nat
        LinphoneNatPolicy natPolicy = mLc.createNatPolicy();
        natPolicy.setStunServer("stun.linphone.org");
        natPolicy.enableIce(true);
        natPolicy.enableStun(true);
        mLc.setNatPolicy(natPolicy);

        //http proxy
        TunnelConfig tunnelConfig = LinphoneCoreFactory.instance().createTunnelConfig();
        tunnelConfig.setPort(443);
        mLc.tunnelAddServer(tunnelConfig);

        //video & audio preferred setting
        mLc.setPreferredVideoSize(VideoSize.VIDEO_SIZE_QVGA);
        mLc.setPreferredFramerate(20f);
        mLc.setVideoPort(9078); //设置视频UDP 端口
        mLc.setAudioPort(7076);
        mLc.setAudioJittcomp(60); //缓冲区大小，以毫秒记
        mLc.setVideoJittcomp(60); //设置视频缓冲区大小，以毫秒记
        mLc.setNortpTimeout(30);
        //echo
        mLc.enableEchoCancellation(true);
        mLc.enableEchoLimiter(true);

        //enable video payloadtype ? how it works
//        PayloadType[] types = mLc.getVideoCodecs();
//        for (PayloadType codec : types) {
//            if (codec.getMime().equals("VP8")) {
//                try {
//                    mLc.setPayloadTypeBitrate(codec, 45000); //bitrate
//                    mLc.enablePayloadType(codec, true);
//                } catch (LinphoneCoreException e) {
//                    e.printStackTrace();
//                }
//            }
//        }

    }

    public void copyIfNotExist(int ressourceId, String target) throws IOException {
        File lFileToCopy = new File(target);
        if (!lFileToCopy.exists()) {
            copyFromPackage(ressourceId, lFileToCopy.getName());
        }
    }

    private void copyFromPackage(int ressourceId, String target) throws IOException {
        FileOutputStream lOutputStream = mServiceContext.openFileOutput(target, 0);
        InputStream lInputStream = mR.openRawResource(ressourceId);
        int readByte;
        byte[] buff = new byte[8048];
        while ((readByte = lInputStream.read(buff)) != -1) {
            lOutputStream.write(buff, 0, readByte);
        }
        lOutputStream.flush();
        lOutputStream.close();
        lInputStream.close();
    }

    private synchronized static LinphoneManager getInstance() {
        if (instance != null) {
            return instance;
        }
        throw new RuntimeException("Linphone Manager should be created before accessed");
    }

    public static synchronized LinphoneCore getLc() {
        return getInstance().mLc;
    }

    @Override
    public void authInfoRequested(LinphoneCore linphoneCore, String s, String s1, String s2) {

    }

    @Override
    public void authenticationRequested(LinphoneCore linphoneCore, LinphoneAuthInfo linphoneAuthInfo, LinphoneCore.AuthMethod authMethod) {

    }

    @Override
    public void callStatsUpdated(LinphoneCore linphoneCore, LinphoneCall linphoneCall, LinphoneCallStats linphoneCallStats) {

    }

    @Override
    public void newSubscriptionRequest(LinphoneCore linphoneCore, LinphoneFriend linphoneFriend, String s) {

    }

    @Override
    public void notifyPresenceReceived(LinphoneCore linphoneCore, LinphoneFriend linphoneFriend) {

    }

    @Override
    public void dtmfReceived(LinphoneCore linphoneCore, LinphoneCall linphoneCall, int i) {

    }

    @Override
    public void notifyReceived(LinphoneCore linphoneCore, LinphoneCall linphoneCall, LinphoneAddress linphoneAddress, byte[] bytes) {

    }

    @Override
    public void transferState(LinphoneCore linphoneCore, LinphoneCall linphoneCall, LinphoneCall.State state) {

    }

    @Override
    public void infoReceived(LinphoneCore linphoneCore, LinphoneCall linphoneCall, LinphoneInfoMessage linphoneInfoMessage) {

    }

    @Override
    public void subscriptionStateChanged(LinphoneCore linphoneCore, LinphoneEvent linphoneEvent, SubscriptionState subscriptionState) {

    }

    @Override
    public void publishStateChanged(LinphoneCore linphoneCore, LinphoneEvent linphoneEvent, PublishState publishState) {

    }

    @Override
    public void show(LinphoneCore linphoneCore) {

    }

    @Override
    public void displayStatus(LinphoneCore linphoneCore, String s) {

    }

    @Override
    public void displayMessage(LinphoneCore linphoneCore, String s) {

    }

    @Override
    public void displayWarning(LinphoneCore linphoneCore, String s) {

    }

    @Override
    public void fileTransferProgressIndication(LinphoneCore linphoneCore, LinphoneChatMessage linphoneChatMessage, LinphoneContent linphoneContent, int i) {

    }

    @Override
    public void fileTransferRecv(LinphoneCore linphoneCore, LinphoneChatMessage linphoneChatMessage, LinphoneContent linphoneContent, byte[] bytes, int i) {

    }

    @Override
    public int fileTransferSend(LinphoneCore linphoneCore, LinphoneChatMessage linphoneChatMessage, LinphoneContent linphoneContent, ByteBuffer byteBuffer, int i) {
        return 0;
    }

    @Override
    public void globalState(LinphoneCore linphoneCore, LinphoneCore.GlobalState globalState, String s) {
    }


    @Override
    public void registrationState(LinphoneCore linphoneCore, LinphoneProxyConfig linphoneProxyConfig, LinphoneCore.RegistrationState registrationState, String s) {

    }

    @Override
    public void configuringStatus(LinphoneCore linphoneCore, LinphoneCore.RemoteProvisioningState remoteProvisioningState, String s) {

    }

    @Override
    public void messageReceived(LinphoneCore linphoneCore, LinphoneChatRoom linphoneChatRoom, LinphoneChatMessage linphoneChatMessage) {

    }

    @Override
    public void messageReceivedUnableToDecrypted(LinphoneCore linphoneCore, LinphoneChatRoom linphoneChatRoom, LinphoneChatMessage linphoneChatMessage) {

    }

    @Override
    public void callState(LinphoneCore linphoneCore, LinphoneCall linphoneCall, LinphoneCall.State state, String s) {

    }

    @Override
    public void callEncryptionChanged(LinphoneCore linphoneCore, LinphoneCall linphoneCall, boolean b, String s) {

    }

    @Override
    public void notifyReceived(LinphoneCore linphoneCore, LinphoneEvent linphoneEvent, String s, LinphoneContent linphoneContent) {

    }

    @Override
    public void isComposingReceived(LinphoneCore linphoneCore, LinphoneChatRoom linphoneChatRoom) {

    }

    @Override
    public void ecCalibrationStatus(LinphoneCore linphoneCore, LinphoneCore.EcCalibratorStatus ecCalibratorStatus, int i, Object o) {

    }

    @Override
    public void uploadProgressIndication(LinphoneCore linphoneCore, int i, int i1) {

    }

    @Override
    public void uploadStateChanged(LinphoneCore linphoneCore, LinphoneCore.LogCollectionUploadState logCollectionUploadState, String s) {

    }

    @Override
    public void friendListCreated(LinphoneCore linphoneCore, LinphoneFriendList linphoneFriendList) {

    }

    @Override
    public void friendListRemoved(LinphoneCore linphoneCore, LinphoneFriendList linphoneFriendList) {

    }

    @Override
    public void networkReachableChanged(LinphoneCore linphoneCore, boolean b) {

    }
}
