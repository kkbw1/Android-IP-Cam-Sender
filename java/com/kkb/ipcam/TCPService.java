package com.kkb.ipcamera;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class TCPService {
    // Debugging
    private static final String TAG = "TCPSERVICE";
    private static final boolean D = true;
    
    // Member fields
    private final Handler mHandler;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private int mState;

    // Constants that indicate the current connection state
    public static final int STATE_INITIAL = 0;
    public static final int STATE_NONE = 1;       // we're doing nothing
    public static final int STATE_LISTEN = 2;     // now listening for incoming connections
    public static final int STATE_CONNECTING = 3; // now initiating an outgoing connection
    public static final int STATE_CONNECTED = 4;  // now connected to a remote device
    
    /**
     * Constructor. Prepares a new BluetoothChat session.
     * @param context  The UI Activity Context
     * @param handler  A Handler to send messages back to the UI Activity
     */
    public TCPService(Context context, Handler handler) {
        mState = STATE_INITIAL;
        mHandler = handler;
        
        // Cancel any thread attempting to make a connection
        if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}
        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}
        
        setState(mState);	//set STATE_INITIAL
    }

    /**
     * Set the current state of the chat connection
     * @param state  An integer defining the current connection state
     */
    private synchronized void setState(int state) {
        if (D) Log.d(TAG, "setState() " + mState + " -> " + state);
        mState = state;

        // Give the new state to the Handler so the UI Activity can update
        mHandler.obtainMessage(CameraActivity.MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
    }
    
    /**
     * Return the current connection state. */
    public synchronized int getState() {
        return mState;
    }
    
    /**
     * Start the ConnectThread to initiate a connection to a remote device.
     * @param device  The BluetoothDevice to connect
     */
    public synchronized void connect(String ip, int port) {
        if (D) Log.d(TAG, "connect to: " + ip + ":" + port);

        // Cancel any thread attempting to make a connection
        if (mState == STATE_CONNECTING) {
            if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}
        }
        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}
        
        // Start the thread to connect with the given device
        mConnectThread = new ConnectThread(ip, port);
        mConnectThread.start();
        setState(STATE_CONNECTING);
    }
    
    /**
     * Start the ConnectedThread to begin managing a tcp connection
     * @param socket  The Socket on which the connection was made
     * @param addr The InetSocketAddress that has been connected
     */
    public synchronized void connected(Socket socket, InetSocketAddress addr) {
        if (D) Log.d(TAG, "connected to server");

        // Cancel the thread that completed the connection
        if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}
        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}

        // Start the thread to manage the connection and perform transmissions
        mConnectedThread = new ConnectedThread(socket);
        mConnectedThread.start();

        // Send the name of the connected device back to the UI Activity
        Message msg = mHandler.obtainMessage(CameraActivity.MESSAGE_SERVER_IP);
        Bundle bundle = new Bundle();
        bundle.putString(CameraActivity.DEVICE_NAME, addr.toString());
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        setState(STATE_CONNECTED);
    }
    
    /**
     * Stop all threads
     */
    public synchronized void stop() {
        if (D) Log.d(TAG, "stop");
        if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}
        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}
        setState(STATE_NONE);
    }
    
    /**
     * Write to the ConnectedThread in an unsynchronized manner
     * @param out The bytes to write
     * @see ConnectedThread#write(byte[])
     */
    public void write(byte[] out) {
        // Create temporary object
        ConnectedThread r;
        // Synchronize a copy of the ConnectedThread
        synchronized (this) {
            if (mState != STATE_CONNECTED) return;
            r = mConnectedThread;
        }
        // Perform the write unsynchronized
        r.write(out);
    }
    
    private void connectionFailed() {
        setState(STATE_NONE);
        
        String message = "Unable to connect device";
        mHandler.obtainMessage(CameraActivity.MESSAGE_ADMIN, -1, -1, message)
        .sendToTarget();
    }

    /**
     * Indicate that the connection was lost and notify the UI Activity.
     */
    private void connectionLost() {
        setState(STATE_NONE);

        String message = "Connected Device Left.";
        mHandler.obtainMessage(CameraActivity.MESSAGE_ADMIN, -1, -1, message)
        .sendToTarget();
    }
    
    /**
     * This thread runs while attempting to make an outgoing connection
     * with a device. It runs straight through; the connection either
     * succeeds or fails.
     */	
    private class ConnectThread extends Thread {
    	private final InetSocketAddress mmAddress;
        private final Socket mmSocket;
        private final int timeout = 4000;

        public ConnectThread(String ip, int port) {
        	mmAddress = new InetSocketAddress(ip, port);
            mmSocket = new Socket();
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectThread");
            setName("ConnectThread");

//             Make a connection to the TCPSocket
            try {
                // This is a blocking call and will only return on a
                // successful connection or an exception
                mmSocket.connect(mmAddress, timeout);
            } catch (IOException e) {
                // Close the socket
                try {
                    mmSocket.close();
                } catch (IOException e2) {
                    Log.e(TAG, "unable to close() socket during connection failure", e2);
                }
                // Start the service over to restart listening mode
            	connectionFailed();
                return;
            }

            // Reset the ConnectThread because we're done
            synchronized (TCPService.this) {
                mConnectThread = null;
            }

            // Start the connected thread
            connected(mmSocket, mmAddress);
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }
    
    /**
     * This thread runs during a connection with a remote device.
     * It handles all incoming and outgoing transmissions.
     */
    private class ConnectedThread extends Thread {
        private final Socket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        
        private Boolean conn = false;

        public ConnectedThread(Socket socket) {
            Log.d(TAG, "create ConnectedThread");
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the Socket input and output streams
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "temp sockets not created", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
            
            conn = true;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectedThread");
            byte[] buffer = new byte[1024];		// 1KByte
            int bytes;
            // Keep listening to the InputStream while connected
            while (conn) {
                try {
                	bytes = mmInStream.read(buffer);
                	if(bytes != -1)
                	{
	                    // Send the obtained bytes to the UI Activity
	                    mHandler.obtainMessage(CameraActivity.MESSAGE_READ, bytes, -1, buffer)
	                            .sendToTarget();
                	}
                	else if(bytes == -1)	// the Device leave.
                	{
                        Log.d(TAG, "disconnected");
                        connectionLost();
                        conn = false;
//                        TCPService.this.stop();
                        break;
                	}
                } catch (IOException e) {
                    Log.d(TAG, "disconnected", e);
                    connectionLost();
                    break;
                }
            }
        }

        /**
         * Write to the connected OutStream.
         * @param buffer  The bytes to write
         */
        public void write(byte[] buffer) {
            try {
                mmOutStream.write(buffer);
                mmOutStream.flush();
            } catch (IOException e) {
                Log.e(TAG, "Exception during write", e);
            }
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }
}
