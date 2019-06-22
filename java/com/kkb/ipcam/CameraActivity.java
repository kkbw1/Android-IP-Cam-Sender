package com.kkb.ipcam;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CameraActivity extends AppCompatActivity implements SurfaceHolder.Callback,
        GestureDetector.OnGestureListener {

    // Debugging
    private static final String TITLE = "IPCam";
    private static final String TAG = "IPCAM";
    private static final boolean D = true;

    private static final String FILE_START = "@@START@@";
    private static final String FILE_END = "@@END@@";

    // TCP/UDP Service Objects
    private TCPService mTCPService = null;
    private UDPService mUDPService = null;

    // Variables and objects
    private String PROTOCOL;
    private String strIP;
    private String strPORT;
    private int PORT;

    Intent intent;

    Handler mTcpHandler;
    Handler mUdpHandler;

    private boolean flagTX = false;
    private boolean flagUDPAck = false;

    private Camera camera;
    private int degree = 0;
    private boolean previewRunning = false;
    private boolean bFrontCameraOn = false;	// Front-Facing Camera.

    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;
    private boolean bSurfaceViewCreate = false;
    private int app_width, app_height;
    private int prev_width, prev_height;

    private GestureDetector mSurfaceGDetector;

    private Button btnCap;
    private Switch swSend;





    //********************************************************************************************//
    //                                                                                            //
    //                                   Overridden Methods                                        //
    //                                                                                            //
    //********************************************************************************************//
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getSupportActionBar().hide();

        mSurfaceGDetector = new GestureDetector(this, this);

        /* get intent message, and Initialize and connect TCP server or UDP */
        intent = getIntent();
        PROTOCOL = intent.getStringExtra("PROTOCOL");
        strIP = intent.getStringExtra("IP");
        strPORT = intent.getStringExtra("PORT");
        PORT = Integer.valueOf(strPORT);
        if(PROTOCOL.equals("TCP"))
        {
            mTcpHandler = new TcpHandler();
              // Automatically connects to specified IP and Port
            mTCPService = new TCPService(this, mTcpHandler);
        }
        else if(PROTOCOL.equals("UDP"))
        {
            mUdpHandler = new UdpHandler();
			mUDPService = new UDPService(this, mUdpHandler, strIP, strPORT);
        }

        InitializeComponent();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        /* close the IP communication services if they are still survived*/
        if(mTCPService != null && PROTOCOL.equals("TCP"))
        {
            mTCPService.stop();
            mTCPService = null;
        }
        else if(mUDPService != null && PROTOCOL.equals("UDP"))
        {
            mUDPService.close();
            mUDPService = null;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.camera_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_start:
                camera.startPreview();
                break;
            case R.id.menu_stop:
                camera.stopPreview();
                break;
            case R.id.menu_change:
//                cameraChange(camera);

                previewRunning = false;
                camera.stopPreview();
                camera.setPreviewCallback(null);
                camera.release();

                int camId;
                if(bFrontCameraOn == true) 	// if Front Camera On.
                {
                    camId = cameraId(Camera.CameraInfo.CAMERA_FACING_BACK);
                    bFrontCameraOn = false;
                }
                else 					// if Front Camera Off.
                {
                    camId = cameraId(Camera.CameraInfo.CAMERA_FACING_FRONT);
                    bFrontCameraOn = true;
                }

                /* open camera */
                try {
                    // open the camera
                    camera = Camera.open(camId);
                } catch (RuntimeException e) {
                    // check for exceptions
                    System.err.println(e);
                    break;
                }

                Camera.Parameters param = camera.getParameters();
                param.setPreviewFpsRange(15000, 15000);
                camera.setParameters(param);

                try {
                    camera.setPreviewDisplay(surfaceHolder);
                    camera.setPreviewCallback(mPreviewCallback);
                } catch (Exception e) {
                    // check for exceptions
                    System.err.println(e);
                    break;
                }

                camera.startPreview();
                previewRunning = true;

                break;
            case R.id.menu_rotation:
                cameraRotation(camera);
                break;
//            case R.id.menu_resolution:
////			    camera.stopPreview();
//                cameraResolutionDialog();
//                break;
//            case R.id.menu_p_anti:
//                createStringListDialog(camera.getParameters().getSupportedAntibanding(),
//                        "Antibanding");
//                break;
//            case R.id.menu_p_color:
//                createStringListDialog(camera.getParameters().getSupportedColorEffects(),
//                        "ColorEffect");
//                break;
//            case R.id.menu_p_flash:
//                createStringListDialog(camera.getParameters().getSupportedFlashModes(),
//                        "FlashMode");
//                break;
//            case R.id.menu_p_focus:
//                createStringListDialog(camera.getParameters().getSupportedFocusModes(),
//                        "FocusMode");
//                break;
//            case R.id.menu_p_thumb:
//                createSizeListDialog(camera.getParameters().getSupportedJpegThumbnailSizes(),
//                        "JpegThumbNail Size");
//                break;
//            case R.id.menu_p_picformat:
//                createIntegerListDialog(camera.getParameters().getSupportedPictureFormats(),
//                        "Picture Format");
//                break;
//            case R.id.menu_p_picsize:
//                createSizeListDialog(camera.getParameters().getSupportedPictureSizes(),
//                        "Picture Size");
//                break;
//            case R.id.menu_p_preformat:
//                createIntegerListDialog(camera.getParameters().getSupportedPreviewFormats(),
//                        "Preview Format");
//                break;
            case R.id.menu_p_prefpsrange:
                createIntArayListDialog(camera.getParameters().getSupportedPreviewFpsRange(), "Preview FPS Range");
                break;
//            case R.id.menu_p_prefps:
//                createIntegerListDialog(camera.getParameters().getSupportedPreviewFrameRates(),
//                        "Preview FPS");
//                break;
//            case R.id.menu_p_presize:
//                createSizeListDialog(camera.getParameters().getSupportedPreviewSizes(),
//                        "Preview Size");
//                break;
//            case R.id.menu_p_scene:
//                createStringListDialog(camera.getParameters().getSupportedSceneModes(),
//                        "SceneMode");
//                break;
//            case R.id.menu_p_vidsize:
//
//                break;
//            case R.id.menu_p_white:
//                createStringListDialog(camera.getParameters().getSupportedWhiteBalance(),
//                        "WhiteBalance");
//                break;
        }
        return true;
    }

    @Override
    public void onBackPressed() {
        DisconnectDialog();
    }

    //-----------------------------  SurfaceView Overridden Methods  ------------------------------//
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        /* check if this device has a camera */
        if (!this.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA))
        {
            // no camera on this device
            Toast.makeText(CameraActivity.this, "No camera available.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        /* check whether this device has permission to access cameras */
        checkCameraPermission();

        /* open camera */
        try {
            // open the camera
            camera = Camera.open();
        } catch (RuntimeException e) {
            // check for exceptions
            System.err.println(e);
            return;
        }

        /* modify camera parameter */
        Camera.Parameters param = camera.getParameters();
        param.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
//        param.setPreviewSize(param.getPreviewSize().width, param.getPreviewSize().height);
        param.setPreviewFpsRange(15000, 15000);
        camera.setParameters(param);

        prev_width = param.getPreviewSize().width;
        prev_height = param.getPreviewSize().height;

        try {
            camera.setPreviewDisplay(surfaceHolder);
            camera.setPreviewCallback(mPreviewCallback);
        } catch (Exception e) {
            // check for exceptions
            System.err.println(e);
            return;
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if(bSurfaceViewCreate == false)
        {
            app_width = width;
            app_height = height;
            bSurfaceViewCreate = true;
        }

        surfaceViewSizeChange(camera, surfaceView, app_width, app_height);

        if(PROTOCOL.equals("TCP") && mTCPService != null && mTCPService.getState() == TCPService.STATE_CONNECTED)
        {
            camera.startPreview();
            previewRunning = true;
        }
        else if(PROTOCOL.equals("UDP") && mUDPService != null)
        {
            camera.startPreview();
            previewRunning = true;
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if(camera != null) {
            cameraClose(camera);
            camera = null;
        }
    }

    //------------------------------  Surface Gesture Events  --------------------------//
    @Override
    public boolean onDown(MotionEvent e) {
        return true;
    }

    @Override
    public void onShowPress(MotionEvent e) {

    }

    @Override
    public boolean onSingleTapUp(MotionEvent e) {
        if(previewRunning)
        {
            camera.autoFocus(mAutoFocus);
        }

        return true;
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        return true;
    }

    @Override
    public void onLongPress(MotionEvent e) {

    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        if(velocityY < -2000)
        {
            getSupportActionBar().hide();
        }
        else if(velocityY > 2000)
        {
            getSupportActionBar().show();
        }

        return true;
    }

    //********************************************************************************************//
    //                                                                                            //
    //                                   Listeners and Callbacks                                  //
    //                                                                                            //
    //********************************************************************************************//

    private View.OnTouchListener surfaceTocuhListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            return mSurfaceGDetector.onTouchEvent(event);
        }
    };

    private Camera.AutoFocusCallback mAutoFocus = new Camera.AutoFocusCallback() {
        @Override
        public void onAutoFocus(boolean success, Camera camera) {
            if(success)
                Toast.makeText(CameraActivity.this, "AutoFoucs",
                        Toast.LENGTH_SHORT).show();
        }
    };

    private Camera.PreviewCallback mPreviewCallback = new Camera.PreviewCallback() {
        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
            if(mTCPService != null && PROTOCOL.equals("TCP") && flagTX)
            {
//                    flagTX = false;
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                Rect size = new Rect(0, 0, prev_width, prev_height);
                YuvImage raw = new YuvImage(data, ImageFormat.NV21, prev_width, prev_height,
                        null);
                raw.compressToJpeg(size, 50, baos);

                byte[] jpeg = baos.toByteArray();

                new SendBytesTask().execute(jpeg);
            }
            else if(mUDPService != null && PROTOCOL.equals("UDP") && flagTX && flagUDPAck)
            {
//				    flagTX = false;
//                flagUDPAck = false;
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                Rect size = new Rect(0, 0, prev_width, prev_height);
                YuvImage raw = new YuvImage(data, ImageFormat.NV21, prev_width, prev_height,
                        null);
                raw.compressToJpeg(size, 50, baos);

                byte[] jpeg = baos.toByteArray();

                /* UDP Packet can have only 64KBytes so, need to be fragmented to send through UDP */
                if(jpeg.length > 64000)
                {
                    int socketBufferSz = 64000;
                    int packetSize = jpeg.length / socketBufferSz;
                    int dataRem = jpeg.length % socketBufferSz;
                    byte[][] packets;

                    if(dataRem == 0)
                    {
                        packets = new byte[packetSize][socketBufferSz];
                        for(int i = 0; i < packetSize; i++)
                        {
                            System.arraycopy(jpeg, i*socketBufferSz, packets[i],
                                    0, socketBufferSz);
                        }

                        for(int i = 0; i < packetSize; i++)
                        {
                            new SendBytesTask().execute(packets[i]);
                        }
                    }
                    else
                    {
                        packets = new byte[packetSize][socketBufferSz];
                        for(int i = 0; i < packetSize; i++)
                        {
                            System.arraycopy(jpeg, i*socketBufferSz, packets[i],
                                    0, socketBufferSz);
                        }

                        byte[] packetRem = new byte[dataRem];
                        System.arraycopy(jpeg, packetSize * socketBufferSz, packetRem,
                                0, dataRem);

                        for(int i = 0; i < packetSize; i++)
                        {
                            new SendBytesTask().execute(packets[i]);
                        }
                        new SendBytesTask().execute(packetRem);
                    }
                }
                else
                {
                    new SendBytesTask().execute(jpeg);
                }

//                byte[] temp = new byte[64000];
//                new SendBytesTask().execute(temp);

                Log.d(TAG, "UDP Picture Sent.");
            }
        }
    };

    private Camera.PictureCallback mJpegPictureCallback = new Camera.PictureCallback() {
        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            /* write the code to do below from here */
            btnCap.setEnabled(false);
            if(mTCPService != null && PROTOCOL.equals("TCP"))
            {
                new SendBytesTask().execute(FILE_START.getBytes());
                new SendBytesTask().execute(data);
                new SendBytesTask().execute(FILE_END.getBytes());
            }
            else if(mUDPService != null && PROTOCOL.equals("UDP"))
            {
                /* UDP Packet can have only 64KBytes so, need to be fragmented to send through UDP */
                if(data.length > 64000)
                {
                    int socketBufferSz = 64000;
                    int packetSize = data.length / socketBufferSz;
                    int dataRem = data.length % socketBufferSz;
                    byte[][] packets;

                    if(dataRem == 0)
                    {
                        packets = new byte[packetSize][socketBufferSz];
                        for(int i = 0; i < packetSize; i++)
                        {
                            System.arraycopy(data, i*socketBufferSz, packets[i],
                                    0, socketBufferSz);
                        }

                        new SendBytesTask().execute(FILE_START.getBytes());
                        for(int i = 0; i < packetSize; i++)
                        {
                            new SendBytesTask().execute(packets[i]);
                        }
                        new SendBytesTask().execute(FILE_END.getBytes());
                    }
                    else
                    {
                        packets = new byte[packetSize][socketBufferSz];
                        for(int i = 0; i < packetSize; i++)
                        {
                            System.arraycopy(data, i*socketBufferSz, packets[i],
                                    0, socketBufferSz);
                        }

                        byte[] packetRem = new byte[dataRem];
                        System.arraycopy(data, packetSize * socketBufferSz, packetRem,
                                0, dataRem);

                        new SendBytesTask().execute(FILE_START.getBytes());
                        for(int i = 0; i < packetSize; i++)
                        {
                            new SendBytesTask().execute(packets[i]);
                        }
                        new SendBytesTask().execute(packetRem);
                        new SendBytesTask().execute(FILE_END.getBytes());
                    }
                }
                else
                {
                    new SendBytesTask().execute(data);
                }
                Log.d(TAG, "UDP Picture Sent.");
            }

            camera.startPreview();
            btnCap.setEnabled(true);

            if(swSend.isChecked())
                flagTX = true;
        }
    };

    class TcpHandler extends Handler {
        public void handleMessage(Message msg) {
            switch(msg.what) {
                case TCPService.MESSAGE_STATE_CHANGE:
                    if(D) Log.d(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
                    switch (msg.arg1) {
                        case TCPService.STATE_INITIAL:
                            /* connect to the IP and port from the MainActivity */
                            mTCPService.connect(strIP, PORT);
                            break;
                        case TCPService.STATE_CONNECTING:
                            Toast.makeText(CameraActivity.this, "Connecting.....",
                                    Toast.LENGTH_SHORT).show();
                            break;
                        case TCPService.STATE_CONNECTED:
                            Toast.makeText(CameraActivity.this, "Connected Success.",
                                    Toast.LENGTH_SHORT).show();

                            camera.startPreview();
                            previewRunning = true;
                            Toast.makeText(CameraActivity.this, "Preview Resolution:" + prev_width + "x" + prev_height, Toast.LENGTH_SHORT).show();

                            break;
                        case TCPService.STATE_NONE:
                            flagTX = false;
                            // if TCP connection is lost, failed, or disconnected.
                            /* back to the main connect activity */
                            Intent intent = new Intent();
                            intent.putExtra("MESSAGE", "Disconnected or the connection is not available.");
                            setResult(RESULT_OK, intent);
                            finish();
                            break;
                    }
                    break;
                case TCPService.MESSAGE_SERVER_IP:
                    setTitle(TITLE + ": " + msg.getData().getString(TCPService.DEVICE_NAME));
                    break;
                case TCPService.MESSAGE_ADMIN:
                    flagTX = false;
                    if(mTCPService != null && PROTOCOL.equals("TCP"))
                    {
                        mTCPService.stop();
                        mTCPService = null;
                        Intent intent = new Intent();
                        intent.putExtra("MESSAGE", (String)msg.obj);
                        setResult(RESULT_OK, intent);
                        finish();
                    }
                    break;
                case TCPService.MESSAGE_WRITE:

                    break;
                case TCPService.MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;
                    // construct a string from the valid bytes in the buffer
                    String readMessage = new String(readBuf, 0, msg.arg1);
                    if(readMessage.equals("FLAG_RTR"))
                    {
                        flagTX = true;
                    }
                    break;
                case TCPService.MESSAGE_TOAST:

                    break;
            }
        }
    }

    class UdpHandler extends Handler {
        public void handleMessage(Message msg) {
            switch(msg.what) {
                case UDPService.MESSAGE_STATE_CHANGE:
                    if(D) Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
                        switch (msg.arg1) {

                    }
                    break;
                case UDPService.MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;
                    // construct a string from the valid bytes in the buffer
                    String strReadBytes = new String(readBuf);
                    /* write the code to do below */
                    String[] strArr = strReadBytes.split("ADDREND");
                    String[] strAddr = strArr[0].split(":");
                    String strMsg = strArr[1];
                    Log.d(TAG, "Data Received.");

                    /* check whether the received message is an ack message
                        and who sent the ack message */
                    if(strMsg.equals("UDPACK") && strAddr[0].equals(strIP))
                    {
                        flagUDPAck = true;
                        Log.d(TAG, "UDPACK Received.");
                    }
                    break;
                case UDPService.MESSAGE_WRITE:
                    // construct a string from the send buffer
                    String writeMessage = (String) msg.obj;
                    /* write the code to do below */

                    break;
                case UDPService.MESSAGE_SERVER_IP:

                    break;
                case UDPService.MESSAGE_CLIENT_IP:

                    break;
                case UDPService.MESSAGE_ADMIN:

                    break;
                case UDPService.MESSAGE_TOAST:
                    String toastMsg = (String) msg.obj;
                    Toast.makeText(CameraActivity.this, toastMsg, Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    }

    //********************************************************************************************//
    //                                                                                            //
    //                               User Defined Sub-routines                                    //
    //                                                                                            //
    //********************************************************************************************//

    private void checkCameraPermission () {
        // Checking if the system already get the access Camera Permission
        if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED)
        {
            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.CAMERA))
            {
                ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.CAMERA}, 50);
            }
            else {   // No explanation needed; request the permission
                ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.CAMERA}, 50);
            }
        }
    }

    private void InitializeComponent() {
        surfaceView = findViewById(R.id.surfaceView1);
        surfaceView.setOnTouchListener(surfaceTocuhListener);
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(CameraActivity.this);
        // deprecated setting, but required on Android versions prior to 3.0
        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        btnCap = findViewById(R.id.btnCap);
        btnCap.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
            flagTX = false;
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
                // Capture a photo image
            camera.takePicture(null, null, mJpegPictureCallback);
            Toast.makeText(CameraActivity.this, "Capture", Toast.LENGTH_SHORT).show();
            }
        });

        swSend = findViewById(R.id.swSend);
        swSend.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if(isChecked)
            {
                Toast.makeText(CameraActivity.this, "On", Toast.LENGTH_SHORT).show();
                flagTX = true;
                flagUDPAck = true;
            }
            else
            {
                Toast.makeText(CameraActivity.this, "Off", Toast.LENGTH_SHORT).show();
                flagTX = false;
            }
            }
        });

        Switch swCam = findViewById(R.id.swFCam);
        swCam.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(camera == null)
                    return;

                if(Camera.getNumberOfCameras() > 1) {
                    previewRunning = false;
                    camera.stopPreview();
                    camera.setPreviewCallback(null);
                    camera.release();

                    int camId;
                    if (isChecked)
                        camId = cameraId(Camera.CameraInfo.CAMERA_FACING_FRONT);
                    else
                        camId = cameraId(Camera.CameraInfo.CAMERA_FACING_BACK);

                    /* open camera */
                    try {
                        // open the camera
                        camera = Camera.open(camId);
                    } catch (RuntimeException e) {
                        // check for exceptions
                        System.err.println(e);
                        return;
                    }

                    Camera.Parameters param = camera.getParameters();
                    param.setPreviewFpsRange(15000, 15000);
                    camera.setParameters(param);

                    try {
                        camera.setPreviewDisplay(surfaceHolder);
                        camera.setPreviewCallback(mPreviewCallback);
                    } catch (Exception e) {
                        // check for exceptions
                        System.err.println(e);
                        return;
                    }

                    camera.startPreview();
                    previewRunning = true;
                }
                else
                {
                    Toast.makeText(CameraActivity.this,
                            "Can't switch camera (Number of Cameras < 2).", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void createIntArayListDialog(List<int[]> list, String title) {
        List<String> listIntArray = new ArrayList<>();
        for(int[] element:list)
        {
            StringBuilder strElement = new StringBuilder();
            for(int i = 0; i < element.length; i++)
            {
                strElement.append(element[i]);
                if(i != element.length - 1)
                {
                    strElement.append(", ");
                }
            }
            listIntArray.add(strElement.toString());
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, listIntArray);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);
        builder.setAdapter(adapter, null);
        builder.setPositiveButton("Back", null);
        builder.create().show();
    }

    private void DisconnectDialog() {
        AlertDialog.Builder DisconnDialog = new AlertDialog.Builder(CameraActivity.this);
        DisconnDialog.setTitle("Alert");
        DisconnDialog.setMessage("Disconnect the IP Comm?");
        DisconnDialog.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                flagTX = false;
                flagUDPAck = false;

                /* close the IP communication services */
                if(mTCPService != null && PROTOCOL.equals("TCP"))
                {
//                    cameraClose();
                    mTCPService.stop();
                    mTCPService = null;
                }
                else if(mUDPService != null && PROTOCOL.equals("UDP"))
                {
                    mUDPService.close();
                    mUDPService = null;
                }

                /* back to the main connect activity */
                intent.putExtra("MESSAGE", "Disconnected by User.");
                setResult(RESULT_OK, intent);
                finish();
            }
        });
        DisconnDialog.setNegativeButton("No", null);
        DisconnDialog.show();
    }

    /** Camera utilizing user-defined sub-routines **/
    private void surfaceViewSizeChange(Camera camera, SurfaceView sv, int w, int h) {
        Camera.Parameters p = camera.getParameters();

        float ratio = (float)p.getPreviewSize().width / (float)p.getPreviewSize().height;
        int change_width = Math.round(h * ratio);
        if(change_width > app_width)
            change_width = app_width;
        int change_height = h;

        ViewGroup.LayoutParams params = sv.getLayoutParams();
        params.width = change_width;
        params.height = change_height;
        sv.setLayoutParams(params);
    }

    private int cameraId(int camera_Info) {
        int getId = 0;
        int count = Camera.getNumberOfCameras();

        Camera.CameraInfo info = new Camera.CameraInfo();
        for(int camId = 0; camId < count; camId++ )
        {
            Camera.getCameraInfo(camId, info);
            if(info.facing == camera_Info)
            {
                getId = camId;
                break;
            }
        }

        return getId;
    }

    private void cameraRotation(Camera camera) {
        degree += 90;
        if(degree >= 360)
            degree = 0;

        if(camera != null)
        {
            camera.stopPreview();
            camera.setDisplayOrientation(degree);
            camera.startPreview();
        }
    }

    private void cameraClose(Camera camera) {
        camera.stopPreview();
        camera.setPreviewCallback(null);
        previewRunning = false;
        camera.release();
    }

    /* Socket-related user-defined sub-routines */
    /**
     * Sends a message using AsyncTask SendBytesTask
     * @param data  data sent in array byte format
     */
    private void sendBytes(byte[] data) {
        if(PROTOCOL.equals("TCP"))
        {
            // Check that we're actually connected before trying send data
            if (mTCPService == null | mTCPService.getState() != TCPService.STATE_CONNECTED) {
                Toast.makeText(this, "Not Connected.", Toast.LENGTH_SHORT).show();
                return;
            }

            if(data.length > 0)
            {
                mTCPService.write(data);
            }
        }
        else if(PROTOCOL.equals("UDP"))
        {
            if(mUDPService == null)
                return;

            if(data.length > 0)
            {
                mUDPService.sendBytes(data);
            }
        }
    }

    private class  SendBytesTask extends AsyncTask<byte[], Void, Void> {

        @Override
        protected Void doInBackground(byte[]... params) {
            try{
                byte[] data = params[0];
                sendBytes(data);
            } catch (Exception e) {
                Log.e(TAG, "Exception", e);
            }

            return null;
        }
    }
}