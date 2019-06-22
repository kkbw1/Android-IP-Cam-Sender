package com.kkb.ipcamera;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

import android.os.Handler;
import android.util.Log;

public class UDPService extends Thread{
    // Debugging
    private static final String TAG = "UDPSERVICE";
    private static final boolean D = true;
    
    // Member fields
    private final Handler mHandler;
    
    private DatagramSocket socket;
    
    private DatagramPacket packet_rx;
    private DatagramPacket packet_tx;
    
    private String IP;
    private int Port;
    
    private byte[] buffer_rx;
    private byte[] buffer_tx;
    
    private boolean conn = false;
    
    /**
     * Constructor. Prepares a new UDP session.
     * @param context  The UI Activity Context
     * @param handler  A Handler to send messages back to the UI Activity
     */
    public UDPService(Handler handler) {
        mHandler = handler;
    	try {
			socket = new DatagramSocket();
		} catch (SocketException e) {
			return;
		}
    }
    
    public void set(String ip, int port) {
    	this.IP = ip;
    	this.Port = port;
    }
    
    public void run() {				//Receive
    	conn = true;
    	buffer_rx = new byte[1024];	//1KB
    	packet_rx = new DatagramPacket(buffer_rx, buffer_rx.length);
    	int bytes;
    	while(conn) {
    		try {
				socket.receive(packet_rx);
				bytes = packet_rx.getLength();
				mHandler.obtainMessage(CameraActivity.MESSAGE_READ, bytes, -1, buffer_rx)
						.sendToTarget();
			} catch (IOException e) {

			}
    	}
    }
    
    public void write(byte[] buffer) {
    	try {
        	buffer_tx = buffer;
			packet_tx = new DatagramPacket(buffer_tx, buffer_tx.length, 
					InetAddress.getByName(IP), Port);
			socket.send(packet_tx);
		} catch (UnknownHostException e) {

		} catch (IOException e) {
			Log.e(TAG, "Exception during write", e);
		}
    }
    
//    public void cancel() {
//    	
//    }
}
