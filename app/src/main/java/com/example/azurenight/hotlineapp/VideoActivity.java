package com.example.azurenight.hotlineapp;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.media.AudioManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.util.ArrayList;
import java.util.List;

public class VideoActivity extends AppCompatActivity implements View.OnClickListener, VideoSignalingClient.VideoSignalingInterface {
    PeerConnectionFactory peerConnectionFactory;
    MediaConstraints audioConstraints;
    MediaConstraints videoConstraints;
    MediaConstraints sdpConstraints;
    VideoSource videoSource;
    VideoTrack localVideoTrack;
    AudioSource audioSource;
    AudioTrack localAudioTrack;

    SurfaceViewRenderer localVideoView;
    //SurfaceViewRenderer remoteVideoView;
    FrameLayout videoframes;
    ViewGroup viewgroup;
    List<SurfaceViewRenderer> viewslist = new ArrayList<>();

    Button hangup;
    PeerConnection localPeer;
    EglBase rootEglBase = EglBase.create();

    boolean gotUserMedia;
    List<PeerConnection.IceServer> peerIceServers = new ArrayList<>();

    private static final String TAG = "VideoActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video);
        videoframes = findViewById(R.id.activity_main);
        viewgroup = (ViewGroup)videoframes.getChildAt(0);


        //Change audio output to the speaker.
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        audioManager.setSpeakerphoneOn(false);

        //Ask for permissions.
        int permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(VideoActivity.this, new String[]{Manifest.permission.CAMERA}, 1);
        }
        permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(VideoActivity.this, new String[]{Manifest.permission.RECORD_AUDIO}, 1);
        }

        /*
        addViews(videoviews.size());
        hangup = findViewById(R.id.end_call);
        hangup.setOnClickListener(this);
        */
        //initVideos();

        PeerConnection.IceServer peerIceServer = PeerConnection.IceServer.builder("turn:140.113.167.189:3478").setUsername("turnserver").setPassword("turnserver").createIceServer();
        peerIceServers.add(peerIceServer);
        PeerConnection.IceServer peerIceServer_stun = PeerConnection.IceServer.builder("stun:140.113.167.189:3478").createIceServer();
        peerIceServers.add(peerIceServer_stun);

        Intent intent = this.getIntent();
        String roomName = intent.getStringExtra("room");
        VideoSignalingClient.getInstance().setRoomName(roomName);
        VideoSignalingClient.getInstance().init(this);

        start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        VideoSignalingClient.getInstance().close();
    }

    private void addViews(final int index) {
        int halfhight, width;
        FrameLayout.LayoutParams lp;
        SurfaceViewRenderer surfaceviewrenderer = new SurfaceViewRenderer(this);
        surfaceviewrenderer.init(rootEglBase.getEglBaseContext(), null);
        surfaceviewrenderer.setVisibility(View.VISIBLE);
        surfaceviewrenderer.setMirror(true);
        if(index == 0){
            lp = new FrameLayout.LayoutParams(dpToPx(120), dpToPx(150));
            lp.gravity = Gravity.BOTTOM|Gravity.END;
            surfaceviewrenderer.setZOrderMediaOverlay(true);
        }
        else{
            lp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        }
        //surfaceviewrenderer.setLayoutParams(lp);
        if(index==0){
            localVideoTrack.addSink(surfaceviewrenderer);
        }
        viewslist.add(surfaceviewrenderer);
        videoframes.addView(surfaceviewrenderer, lp);

    }

    public void start() {
        //Initialize PeerConnectionFactory Globals.
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(this).setEnableVideoHwAcceleration(true).createInitializationOptions());

        //Create a new PeerConnectionFactory instance.
        peerConnectionFactory = PeerConnectionFactory.builder().createPeerConnectionFactory();

        //Create a VideoCapturer instance.
        VideoCapturer videoCapturerAndroid;
        videoCapturerAndroid = createCameraCapturer(new Camera1Enumerator(false));

        //Create MediaConstraints - Will be useful for specifying video and audio constraints.
        audioConstraints = new MediaConstraints();
        videoConstraints = new MediaConstraints();

        //Create sdpConstraints.
        sdpConstraints = new MediaConstraints();
        sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("offerToReceiveAudio", "true"));
        sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("offerToReceiveVideo", "true"));

        //Create a VideoSource instance.
        if (videoCapturerAndroid != null) {
            videoSource = peerConnectionFactory.createVideoSource(videoCapturerAndroid);
        }
        localVideoTrack = peerConnectionFactory.createVideoTrack("100", videoSource);

        //Create an AudioSource instance.
        audioSource = peerConnectionFactory.createAudioSource(audioConstraints);
        localAudioTrack = peerConnectionFactory.createAudioTrack("101", audioSource);

        //Start capturing the video from the camera.
        //width, height and fps
        if (videoCapturerAndroid != null) {
            videoCapturerAndroid.startCapture(1024, 720, 30);
        }

        addViews(viewslist.size());
        hangup = findViewById(R.id.end_call);
        hangup.setOnClickListener(this);
        /*
        localVideoView.setVisibility(View.VISIBLE);
        localVideoTrack.addSink(localVideoView);


        localVideoView.setMirror(true);
        remoteVideoView.setMirror(true);
        */

        gotUserMedia = true;
        if (VideoSignalingClient.getInstance().isInitiator) {
            Log.d(TAG, "call onTryToStart 1");
            onTryToStart();
        }
        VideoSignalingClient.getInstance().emitMessage("got user media");
    }

    private VideoCapturer createCameraCapturer(CameraEnumerator enumerator) {
        final String[] deviceNames = enumerator.getDeviceNames();

        //Try to find front facing camera.
        Log.d(TAG, "Looking for front facing cameras.");
        for (String deviceName : deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                Log.d(TAG, "Create front facing camera capturer.");
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        //Front facing camera not found, try something else.
        Log.d(TAG, "Looking for other cameras.");
        for (String deviceName : deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                Log.d(TAG, "Creating other camera capturer.");
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        //Can't find any camera.
        return null;
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.end_call: {
                hangup();
                break;
            }
            case R.id.resize: {
                resize();
                break;
            }
        }
    }

    @Override
    public void onRemoteHangUp(String msg) {
        showToast("Remote peer hung up");
        runOnUiThread(this::hangup);
    }

    private void hangup() {
        try {
            localPeer.close();
            localPeer = null;
            VideoSignalingClient.getInstance().close();
            updateVideoViews(false);
            finish();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void resize(){
        Log.d(TAG, "click resize");
        SurfaceViewRenderer surfaceViewRenderer = (SurfaceViewRenderer)videoframes.getChildAt(0);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dpToPx(240), dpToPx(300));
        lp.gravity = Gravity.BOTTOM|Gravity.END;
        surfaceViewRenderer.setLayoutParams(lp);
    }

    private void updateVideoViews(final boolean remoteVisible) {
        runOnUiThread(() -> {
            ViewGroup.LayoutParams params = localVideoView.getLayoutParams();
            if (remoteVisible) {
                params.height = dpToPx(100);
                params.width = dpToPx(100);
            } else {
                params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            }
            localVideoView.setLayoutParams(params);
        });
    }

    public int dpToPx(int dp) {
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        return Math.round(dp * (displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT));
    }

    //Signaling Callback - called when remote peer sends offer.
    @Override
    public void onOfferReceived(final JSONObject data) {
        showToast("Received offer");
        runOnUiThread(() -> {
            if (!VideoSignalingClient.getInstance().isInitiator && !VideoSignalingClient.getInstance().isStarted) {
                Log.d(TAG, "call onTryToStart 2");
                onTryToStart();
            }
            try {
                localPeer.setRemoteDescription(new VideoCustomSdpObserver("localSetRemoteDescription"), new SessionDescription(SessionDescription.Type.OFFER, data.getString("sdp")));
                doAnswer();
                //updateVideoViews(true);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        });
    }

    private void doAnswer() {
        localPeer.createAnswer(new VideoCustomSdpObserver("localCreateAnswer") {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                super.onCreateSuccess(sessionDescription);
                localPeer.setLocalDescription(new VideoCustomSdpObserver("localSetLocalDescription"), sessionDescription);
                VideoSignalingClient.getInstance().emitMessage(sessionDescription);
            }
        }, new MediaConstraints());
    }

    //Signaling Callback - called when remote peer sends answer to your offer.
    @Override
    public void onAnswerReceived(JSONObject data) {
        showToast("Received answer");
        try {
            localPeer.setRemoteDescription(new VideoCustomSdpObserver("localSetRemoteDescription"), new SessionDescription(SessionDescription.Type.fromCanonicalForm(data.getString("type").toLowerCase()), data.getString("sdp")));
            //updateVideoViews(true);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    //Remote Ice candidate received.
    @Override
    public void onIceCandidateReceived(JSONObject data) {
        try {
            localPeer.addIceCandidate(new IceCandidate(data.getString("id"), data.getInt("label"), data.getString("candidate")));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * This method will be called directly by the app when it is the initiator and has got the local media
     * or when the remote peer sends a message through socket that it is ready to transmit AV data.
     */
    @Override
    public void onTryToStart() {
        runOnUiThread(() -> {
            if (!VideoSignalingClient.getInstance().isStarted && localVideoTrack != null && VideoSignalingClient.getInstance().isChannelReady) {
                createPeerConnection();
                VideoSignalingClient.getInstance().isStarted = true;
                if (VideoSignalingClient.getInstance().isInitiator) {
                    doCall();
                }
            }
        });
    }

    //Creating the local PeerConnection instance.
    private void createPeerConnection() {
        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(peerIceServers);
        //TCP candidates are only useful when connecting to a server that supports ICE-TCP.
        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED;
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
        rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
        //Use ECDSA encryption.
        rtcConfig.keyType = PeerConnection.KeyType.ECDSA;
        //Enable DTLS-SRTP.
        rtcConfig.enableDtlsSrtp = true;
        localPeer = peerConnectionFactory.createPeerConnection(rtcConfig, new VideoCustomPeerConnectionObserver("localPeerConnection") {
            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                super.onIceCandidate(iceCandidate);
                onIceCandidateReceived(iceCandidate);
            }

            @Override
            public void onAddStream(MediaStream mediaStream) {
                showToast("Received remote stream");
                super.onAddStream(mediaStream);
                gotRemoteStream(mediaStream);

            }
        });

        addStreamToLocalPeer();
    }

    //Received local ice candidate. Send it to remote peer through signaling for negotiation.
    public void onIceCandidateReceived(IceCandidate iceCandidate) {
        //We have received ice candidate We can set it to the other peer.
        VideoSignalingClient.getInstance().emitIceCandidate(iceCandidate);
    }

    public void showToast(final String msg) {
        runOnUiThread(() ->
                Toast.makeText(VideoActivity.this, msg, Toast.LENGTH_SHORT).show()
        );
    }

    //Received remote peer's media stream. We will get the first video track and render it.
    private void gotRemoteStream(MediaStream stream) {
        //We have remote video stream. Add to the render.
        final VideoTrack videoTrack = stream.videoTracks.get(0);
        runOnUiThread(() -> {
            try {
                addViews(viewslist.size());
                videoTrack.addSink(viewslist.get(1));
                //hangup.setEnabled(true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    //Adding the stream to the local peer.
    private void addStreamToLocalPeer() {
        //Creating local mediaStream.
        MediaStream stream = peerConnectionFactory.createLocalMediaStream("102");
        stream.addTrack(localAudioTrack);
        stream.addTrack(localVideoTrack);
        localPeer.addStream(stream);
    }

    /**
     * This method is called when the app is initiator.
     * We generate the offer and send it over through socket to remote peer.
     */
    private void doCall() {
        localPeer.createOffer(new VideoCustomSdpObserver("localCreateOffer") {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                super.onCreateSuccess(sessionDescription);
                localPeer.setLocalDescription(new VideoCustomSdpObserver("localSetLocalDescription"), sessionDescription);
                Log.d("onCreateSuccess", "SignalingClient emit");
                VideoSignalingClient.getInstance().emitMessage(sessionDescription);
            }
        }, sdpConstraints);
    }

    //SignalingCallback - called when the room is created.
    //i.e. you are the initiator.
    @Override
    public void onCreatedRoom() {
        showToast("You created the room");
    }

    //SignalingCallback - called when you join the room.
    //i.e. you are a participant.
    @Override
    public void onJoinedRoom() {
        showToast("You joined the room");
    }

    @Override
    public void onNewPeerJoined() {
        showToast("Remote peer joined");
    }

    @Override
    public void roomIsFull(String roomName) { showToast("Room " + roomName + " is full"); }
}
