package com.kkb.ipcamera;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.Toast;

public class MainActivity extends Activity implements OnClickListener {

	final static int INT_CAMACT = 0;
	
	RadioButton rbtn_tcp, rbtn_udp;
	EditText et_ip, et_port;
	Button btn_conn, btn_exit;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		rbtn_tcp = (RadioButton)findViewById(R.id.rbtn_tcp);
		rbtn_udp = (RadioButton)findViewById(R.id.rbtn_udp);
		
		et_ip = (EditText)findViewById(R.id.et_ip);
		et_port = (EditText)findViewById(R.id.et_port);
		
		btn_conn = (Button)findViewById(R.id.btn_conn);
		btn_conn.setOnClickListener(this);
		btn_exit = (Button)findViewById(R.id.btn_exit);
		btn_exit.setOnClickListener(this);
	}

//	@Override
//	public boolean onCreateOptionsMenu(Menu menu) {
//		// Inflate the menu; this adds items to the action bar if it is present.
//		getMenuInflater().inflate(R.menu.activity_main, menu);
//		return true;
//	}
	
    @Override
	public void onBackPressed() {
    	ExitDialog();
//		super.onBackPressed();
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.btn_conn:
			String IP = et_ip.getText().toString();
			String PORT = et_port.getText().toString();
			if(!(IP.equals("") || PORT.equals("") || IP.length() > 15 || PORT.length() > 6))
			{
				Intent intent = new Intent(this, CameraActivity.class);
				intent.putExtra("IP", IP);
				intent.putExtra("PORT", PORT);
				if(rbtn_tcp.isChecked() && !rbtn_udp.isChecked())
				{
					intent.putExtra("PROTOCOL", "TCP");
					startActivityForResult(intent, 0);
				}
				else if(rbtn_udp.isChecked() && !rbtn_tcp.isChecked())
				{
					intent.putExtra("PROTOCOL", "UDP");
					startActivityForResult(intent, 0);
				}
				else
				{
					Toast.makeText(this, "Choose Protocol Type.", 
							Toast.LENGTH_SHORT).show();
				}
			}
			else
			{
				Toast.makeText(this, "Write IP or Port.", 
						Toast.LENGTH_SHORT).show();
			}
			break;
		case R.id.btn_exit:
			ExitDialog();
			break;
		}
	}
	
	private void ExitDialog() {
    	Builder ExitDialog = new AlertDialog.Builder(MainActivity.this);
    	ExitDialog.setTitle("Alert");
    	ExitDialog.setMessage("Exit the App?");
    	ExitDialog.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				finish();
			}
		});
    	ExitDialog.setNegativeButton("No", null);
    	ExitDialog.show();
	}

}
