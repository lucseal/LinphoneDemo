package com.example.linphonedemo;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneAuthInfo;
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCallParams;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCoreException;
import org.linphone.core.LinphoneCoreFactory;
import org.linphone.core.LinphoneCoreListenerBase;
import org.linphone.core.LinphoneProxyConfig;
import org.linphone.mediastream.video.AndroidVideoWindowImpl;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private Button mBtnReg;
    private Button mBtnCall;
    private Button mBtnTerminate;
    private Button mBtnAccept;
    private EditText mEtUser;
    private EditText mEtCallTo;

    private SurfaceView mVideoView;
    private SurfaceView mCaptureView;
    private AndroidVideoWindowImpl androidVideoWindowImpl;

    private LinphoneProxyConfig proxyConfig;
    private LinphoneAuthInfo authInfo;
    private LinphoneCoreListenerBase mListener;

    private LinphoneCall mCall;

    private String userName;
    public static final String password = "123456";
    private static final String domain = "sip.linphone.org";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

//        startIterate();

        mListener = new LinphoneCoreListenerBase() {
            @Override
            public void registrationState(LinphoneCore lc, LinphoneProxyConfig cfg, LinphoneCore.RegistrationState state, String smessage) {
                Toast.makeText(MainActivity.this, "register state: " + state, Toast.LENGTH_SHORT).show();
                Log.d("myPhoneMsg", "registrationState: msg: " + smessage);
            }

            @Override
            public void callState(LinphoneCore lc, LinphoneCall call, LinphoneCall.State state, String message) {
                if (state == LinphoneCall.State.IncomingReceived) {
                    call.enableCamera(true);
                    Toast.makeText(MainActivity.this, "on call", Toast.LENGTH_SHORT).show();
                } else if (state == LinphoneCall.State.OutgoingInit || state == LinphoneCall.State.OutgoingProgress) {
                    Toast.makeText(MainActivity.this, "out going", Toast.LENGTH_SHORT).show();
                } else if (state == LinphoneCall.State.CallEnd || state == LinphoneCall.State.Error || state == LinphoneCall.State.CallReleased) {
                    Toast.makeText(MainActivity.this, "end call", Toast.LENGTH_SHORT).show();
                } else if (state == LinphoneCall.State.StreamsRunning) {
                    Toast.makeText(MainActivity.this, "stream running", Toast.LENGTH_SHORT).show();
                }
            }
        };

        LinphoneManager.getLc().addListener(mListener);
        initView();

    }

//    private void startIterate() {
//        TimerTask lTask = new TimerTask() {
//            @Override
//            public void run() {
//                UIThreadDispatcher.dispatch(new Runnable() {
//                    @Override
//                    public void run() {
//                        if (LinphoneManager.getLc() != null) {
//                            LinphoneManager.getLc().iterate();
//                        }
//                    }
//                });
//            }
//        };
//
//        Timer mTimer = new Timer("Linphone scheduler");
//        mTimer.schedule(lTask, 0, 20);
//    }

    private void initView() {
        mVideoView = findViewById(R.id.videoSurface);
        mCaptureView = findViewById(R.id.videoCaptureSurface);

        mBtnReg = findViewById(R.id.btn_register);
        mBtnCall = findViewById(R.id.btn_call);
        mBtnTerminate = findViewById(R.id.btn_terminate);
        mBtnAccept = findViewById(R.id.btn_accept);
        mEtCallTo = findViewById(R.id.et_call_to);
        mEtUser = findViewById(R.id.et_user);

        mBtnReg.setOnClickListener(this);
        mBtnCall.setOnClickListener(this);
        mBtnTerminate.setOnClickListener(this);
        mBtnAccept.setOnClickListener(this);

        mCaptureView.getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        androidVideoWindowImpl = new AndroidVideoWindowImpl(mVideoView, mCaptureView, new AndroidVideoWindowImpl.VideoWindowListener() {
            @Override
            public void onVideoRenderingSurfaceReady(AndroidVideoWindowImpl androidVideoWindow, SurfaceView surfaceView) {
                mVideoView = surfaceView;
                LinphoneManager.getLc().setVideoWindow(androidVideoWindow);
            }

            @Override
            public void onVideoRenderingSurfaceDestroyed(AndroidVideoWindowImpl androidVideoWindow) {

            }

            @Override
            public void onVideoPreviewSurfaceReady(AndroidVideoWindowImpl androidVideoWindow, SurfaceView surfaceView) {
                mCaptureView = surfaceView;
                LinphoneManager.getLc().setPreviewWindow(mCaptureView);
            }

            @Override
            public void onVideoPreviewSurfaceDestroyed(AndroidVideoWindowImpl androidVideoWindow) {

            }
        });


    }

    private void checkAndRequestCallPermissions() {
        ArrayList<String> permissionsList = new ArrayList<String>();

        int recordAudio = getPackageManager().checkPermission(Manifest.permission.RECORD_AUDIO, getPackageName());
        org.linphone.mediastream.Log.i("[Permission] Record audio permission is " + (recordAudio == PackageManager.PERMISSION_GRANTED ? "granted" : "denied"));
        int camera = getPackageManager().checkPermission(Manifest.permission.CAMERA, getPackageName());
        org.linphone.mediastream.Log.i("[Permission] Camera permission is " + (camera == PackageManager.PERMISSION_GRANTED ? "granted" : "denied"));

        if (recordAudio != PackageManager.PERMISSION_GRANTED) {

            permissionsList.add(Manifest.permission.RECORD_AUDIO);
        }

        if (camera != PackageManager.PERMISSION_GRANTED) {
            permissionsList.add(Manifest.permission.CAMERA);
        }

        if (permissionsList.size() > 0) {
            String[] permissions = new String[permissionsList.size()];
            permissions = permissionsList.toArray(permissions);
            ActivityCompat.requestPermissions(this, permissions, 0);
        }
    }

    @Override
    protected void onResume() {
        LinphoneManager.getLc().setVideoWindow(androidVideoWindowImpl);
        checkAndRequestCallPermissions();
        super.onResume();
    }

    private void register() {
//        PayloadType[] types = LinphoneManager.getLc().getVideoCodecs();
//        for (PayloadType codec : types) {
//            if (codec.getMime().equals("H264")) {
//                try {
//                    LinphoneManager.getLc().enablePayloadType(codec, true);
//                } catch (LinphoneCoreException e) {
//                    e.printStackTrace();
//                }
//            }
//        }
        userName = mEtUser.getText().toString();
        if (userName.length() == 0) {
            return;
        }
        String sipAddress = "sip:" + userName + "@" + domain;
        try {
            if (LinphoneManager.getLc().getDefaultProxyConfig() != null) {
                LinphoneManager.getLc().clearAuthInfos();
                LinphoneManager.getLc().clearProxyConfigs();
            }
            LinphoneAddress identityAddr = LinphoneCoreFactory.instance().createLinphoneAddress(sipAddress);
            LinphoneAddress proxyAddr = LinphoneCoreFactory.instance().createLinphoneAddress("sip:" + domain);
            identityAddr.setTransport(LinphoneAddress.TransportType.LinphoneTransportTcp);
            proxyAddr.setTransport(LinphoneAddress.TransportType.LinphoneTransportTcp);
            proxyConfig = LinphoneManager.getLc().createProxyConfig(identityAddr.asString(), proxyAddr.asStringUriOnly(), domain, true);
            proxyConfig.setExpires(3600);
            proxyConfig.enableRegister(true);
            proxyConfig.setRealm("sip.linphone.org");
            proxyConfig.enableAvpf(true);
            LinphoneManager.getLc().addProxyConfig(proxyConfig);
            authInfo = LinphoneCoreFactory.instance().createAuthInfo(userName, password, domain, domain);
            LinphoneManager.getLc().addAuthInfo(authInfo);
            LinphoneManager.getLc().setDefaultProxyConfig(proxyConfig);

        } catch (LinphoneCoreException e) {
            e.printStackTrace();
        }
    }

    private void call() {
        if (LinphoneManager.getLc().isIncall()) {
            return;
        }
        String callTo = mEtCallTo.getText().toString();
        if (callTo.length() == 0) {
            return;
        }
        try {
            if (LinphoneManager.getLc().isNetworkReachable()) {
                String to = "sip:" + callTo + "@" + domain;
                LinphoneCallParams params = LinphoneManager.getLc().createCallParams(null);
                params.setVideoEnabled(true);
                LinphoneAddress address = LinphoneManager.getLc().interpretUrl(to);
                address.setTransport(LinphoneAddress.TransportType.LinphoneTransportUdp);
                mCall = LinphoneManager.getLc().inviteAddressWithParams(address, params);
            }
        } catch (LinphoneCoreException e) {
            e.printStackTrace();
        }
    }

    private void accept() {
        try {
            LinphoneCall[] calls = LinphoneManager.getLc().getCalls();
            for (LinphoneCall call : calls) {
                if (LinphoneCall.State.IncomingReceived == call.getState()) {
                    mCall = call;
                    break;
                }
            }
            LinphoneCallParams params = LinphoneManager.getLc().createCallParams(mCall);
            LinphoneManager.getLc().acceptCallWithParams(mCall, params);
        } catch (LinphoneCoreException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btn_register:
                register();
                break;
            case R.id.btn_call:
                call();
                break;
            case R.id.btn_terminate:
                if (mCall != null) {
                    LinphoneManager.getLc().terminateCall(mCall);
                }
                break;
            case R.id.btn_accept:
                accept();
                break;
        }
    }

}
