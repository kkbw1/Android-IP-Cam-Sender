package com.kkb.ipcamera;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.Camera.AutoFocusCallback;
import android.hardware.Camera.PreviewCallback;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.View.OnTouchListener;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

public class CameraActivity extends Activity implements Callback {
	
	// Debugging
	private static final String TITLE = "IP Camera";
    private static final String TAG = "IP_CAMERA";
    private static final boolean D = true;
	
	private TCPService mTCPService = null;
	private UDPService mUDPService = null;
	
	// Message types sent from the TCPService Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_SERVER_IP = 4;
    public static final int MESSAGE_ADMIN = 5;
    public static final int MESSAGE_TOAST = 6;
    // Key names received from the TCPService Handler
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";
	
	private String PROTOCOL;
    private String IP_str;
    private String PORT_str;
    private int PORT;
	public static boolean flag_TX = true;
    
    private int app_width, app_height;
    private int prev_width, prev_height;
    
	private SurfaceView surfaceView;
	private SurfaceHolder surfaceHolder;
	private boolean surfaceViewCreate = false;
	
	private Camera camera;
	private boolean previewRunning = false;
    
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Window win = getWindow();
        win.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);  
        setContentView(R.layout.activity_camera);
		
		Intent intent = getIntent();
		PROTOCOL = intent.getStringExtra("PROTOCOL");
		IP_str = intent.getStringExtra("IP");
		PORT_str = intent.getStringExtra("PORT");
		PORT = Integer.valueOf(PORT_str);
		
		if(PROTOCOL.equals("TCP"))
		{
			mTCPService = new TCPService(this, mHandler);
			@SuppressWarnings("unused")
			int a = 1;
		}
		else if(PROTOCOL.equals("UDP"))
		{
//			mUDPService = new UDPService(mHandler);
			@SuppressWarnings("unused")
			int b = 1;
		}
	}
	
	@Override
	protected void onStart() {
		super.onStart();
		
        surfaceView = (SurfaceView) findViewById(R.id.surfaceView1);
        surfaceView.setOnTouchListener(surfaceTocuh);
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(CameraActivity.this);
        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
	}
	
	@Override
	protected void onResume() {
		super.onResume();
	}
	
	@Override
	protected void onPause() {
		super.onPause();
	}

	@Override
	protected void onRestart() {
		super.onRestart();
	}
	
	@Override
	protected void onStop() {
		super.onStop();
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		
		return true;
	}
	
	@Override
	public void onBackPressed() {
		DisconnectDialog();
	}
	
	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		
	}
	
	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		if(surfaceViewCreate == false)
		{
			app_width = width;
			app_height = height;
			surfaceViewCreate = true;
		}
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		cameraClose();
	}
	
	// The Handler that gets information back from the TCPService
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MESSAGE_STATE_CHANGE:
                if(D) Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
                switch (msg.arg1) {
                case TCPService.STATE_INITIAL:
            		mTCPService.connect(IP_str, PORT);
                	break;
                case TCPService.STATE_CONNECTING:
                	Toast.makeText(CameraActivity.this, "Connecting.....", 
                			Toast.LENGTH_SHORT).show();
                    break;
                case TCPService.STATE_CONNECTED:
                	Toast.makeText(CameraActivity.this, "Connected Success.", 
                			Toast.LENGTH_SHORT).show();
                	cameraInit();
            		flag_TX = true;
                    break;
                case TCPService.STATE_NONE:

                    break;
                }
                break;
            case MESSAGE_SERVER_IP:
            	setTitle(TITLE + ": " + msg.getData().getString(DEVICE_NAME));
                break;
            case MESSAGE_ADMIN:
            	if(mTCPService != null && PROTOCOL == "TCP")
            	{
	            	mTCPService.stop();
	            	mTCPService = null;
	            	Intent intent = new Intent();
	            	intent.putExtra("MESSAGE", (String)msg.obj);
	            	setResult(RESULT_OK, intent);
	            	finish();
            	}
            	break;
            case MESSAGE_WRITE:

                break;
            case MESSAGE_READ:
                byte[] readBuf = (byte[]) msg.obj;
                // construct a string from the valid bytes in the buffer
                String readMessage = new String(readBuf, 0, msg.arg1);
                if(readMessage.equals("FLAG_RTR"))
                {
                	flag_TX = true;
                }
                break;
            case MESSAGE_TOAST:

                break;
            }
        }
    };
	
	private OnTouchListener surfaceTocuh = new OnTouchListener() {
		@Override
		public boolean onTouch(View v, MotionEvent event) {
			if(previewRunning)
			{
				camera.autoFocus(mFocus);
			}
//			Toast.makeText(CameraActivity.this, app_width + "," + app_height, 
//					Toast.LENGTH_SHORT).show();
			return false;
		}
	};
	
	private AutoFocusCallback mFocus = new AutoFocusCallback() {
		@Override
		public void onAutoFocus(boolean success, Camera camera) {
			if(success)
				Toast.makeText(CameraActivity.this, "AutoFoucs", 
						Toast.LENGTH_SHORT).show();
		}
	};
	
	private PreviewCallback mPreviewCB = new PreviewCallback() {
		@Override
		public void onPreviewFrame(byte[] data, Camera camera) {
			if(flag_TX == true)
			{
//				flag_TX = false;
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				Rect size = new Rect(0, 0, prev_width, prev_height);
				YuvImage raw = new YuvImage(data, ImageFormat.NV21, 
						prev_width, prev_height, null);
				raw.compressToJpeg(size, 50, baos);
				byte[] jpeg = baos.toByteArray();
				if(mTCPService != null && PROTOCOL.equals("TCP"))
				{
					mTCPService.write(jpeg);
				}
				else if(mUDPService != null && PROTOCOL == "UDP")
				{
					mUDPService.write(jpeg);
				}
			}
		}
	};
	
	private void DisconnectDialog() {
    	Builder DisconnDialog = new AlertDialog.Builder(CameraActivity.this);
    	DisconnDialog.setTitle("Alert");
    	DisconnDialog.setMessage("Do you want Disconnect?");
    	DisconnDialog.setPositiveButton("Yes,I will.", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				if(mTCPService != null && PROTOCOL == "TCP")
				{
					cameraClose();
	            	mTCPService.stop();
	            	mTCPService = null;
				}
				else if(mUDPService != null && PROTOCOL == "UDP")
				{
					
				}
            	Intent intent = new Intent();
            	intent.putExtra("MESSAGE", "Disconnected by User.");
            	setResult(RESULT_OK, intent);
            	finish();
			}
		});
    	DisconnDialog.setNegativeButton("No,I won't.", null);
    	DisconnDialog.show();
	}
	
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
	
	private void cameraInit() {
		if (camera == null) {
			camera = Camera.open();
			camera.lock();
			try {
				camera.setPreviewDisplay(surfaceHolder);
				camera.setDisplayOrientation(0);
        		camera.setPreviewCallback(mPreviewCB);
			} catch (IOException e) {
				e.printStackTrace();
			}
			Camera.Parameters p = camera.getParameters();
			p.setPreviewSize(320, 240);
			prev_width = 320;
			prev_height = 240;
			camera.setParameters(p);
			
			surfaceViewSizeChange(camera, surfaceView, app_width, app_height);
			
			camera.startPreview();
			previewRunning = true;
		}
	}
	
	private void cameraClose() {
		if(camera != null)
		{
		camera.stopPreview();
		previewRunning = false;
		flag_TX = false;
		camera.setPreviewCallback(null);
		camera.unlock();
		camera.release();
		camera = null;
		}
	}
}
