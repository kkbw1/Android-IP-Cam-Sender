package com.kkb.ipcam;

import android.content.DialogInterface;
import android.content.Intent;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    RadioButton rbtnTCP, rbtnUDP;
    EditText etIP, etPort;
    Button btnConn, btnExit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getSupportActionBar().hide();

        InitializeComponent();

        etIP.setText("192.168.1.157");
        etPort.setText("8000");
    }

    @Override
    public void onBackPressed() {
        ExitDialog();
//        super.onBackPressed();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId())
        {
            case R.id.btnConn:
                String IP = etIP.getText().toString();
                String PORT = etPort.getText().toString();
                if(!(IP.equals("") || PORT.equals("") || IP.length() > 15 || PORT.length() > 6))
                {
                    Intent intent = new Intent(this, CameraActivity.class);
                    intent.putExtra("IP", IP);
                    intent.putExtra("PORT", PORT);
                    if(rbtnTCP.isChecked() && !rbtnUDP.isChecked())
                    {
                        intent.putExtra("PROTOCOL", "TCP");
                        startActivityForResult(intent, 0);
                    }
                    else if(rbtnUDP.isChecked() && !rbtnTCP.isChecked())
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

//                Intent intent = new Intent(this, CameraActivity.class);
//                intent.putExtra("PROTOCOL", "TCP");
//                startActivityForResult(intent, 0);

                break;
            case R.id.btnExit:
                ExitDialog();
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Check which request we're responding to
        if (requestCode == 0) {
            // Make sure the request was successful
            if (resultCode == RESULT_OK) {
                String msg = data.getStringExtra("MESSAGE");
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
            }
        }
    }

    //********************************************************************************************//
    //                                                                                            //
    //                               User Defined Sub-routines                                    //
    //                                                                                            //
    //********************************************************************************************//
//    private void checkDiskPermission () {
//        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
//                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
//                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
//            Toast.makeText(this, "No Disk Permissions" , Toast.LENGTH_LONG).show();
//            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 0);
//        }
//        else
//        {
//            Toast.makeText(this, "Has Disk Permissions" , Toast.LENGTH_LONG).show();
//        }
//    }

    private void InitializeComponent() {
        rbtnTCP = findViewById(R.id.rbtnTCP);
        rbtnUDP = findViewById(R.id.rbtnUDP);

        etIP = findViewById(R.id.etIP);
        etPort = findViewById(R.id.etPort);

        btnConn = findViewById(R.id.btnConn);
        btnConn.setOnClickListener(this);
        btnExit = findViewById(R.id.btnExit);
        btnExit.setOnClickListener(this);
    }

    private void ExitDialog() {
        AlertDialog.Builder ExitDialog = new AlertDialog.Builder(MainActivity.this);
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
