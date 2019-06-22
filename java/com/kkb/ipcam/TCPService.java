package com.kkb.ipcam;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class TCPService {
    private static final String TAG = "TCPSERVICE";
    private static final boolean D = true;

    // Message types sent from the TCPService Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_SERVER_IP = 4;
    public static final int MESSAGE_CLIENT_IP = 5;
    public static final int MESSAGE_ADMIN = 6;
    public static final int MESSAGE_TOAST = 7;

    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";

    // Constants that indicate the current connection state
    public static final int STATE_INITIAL = 0;
    public static final int STATE_NONE = 1;       // we're doing nothing
    public static final int STATE_LISTEN = 2;     // now listening for incoming connections
    public static final int STATE_CONNECTING = 3; // now initiating an outgoing connection
    public static final int STATE_CONNECTED = 4;  // now connected to a remote device
//    public static final int STATE_DISCONNECTED = 5;

    // Member fields
    private final Handler mHandler;
    private AcceptThread mAcceptThread;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private int mState;

    /**
     * Constructor. Prepares a new BluetoothChat session.
     * @param context  The UI Activity Context
     * @param handler  A Handler to send messages back to the UI Activity
     */
    public TCPService(Context context, Handler handler) {
        mState = STATE_INITIAL;
        mHandler = handler;

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
        mHandler.obtainMessage(TCPService.MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
    }

    /**
     * Return the current connection state.
     **/
    public synchronized int getState() {
        return mState;
    }

    /**
     * TCP Server Mode
     * Start the chat service. Specifically start AcceptThread to begin a
     * session in listening (server) mode.
     **/
    public synchronized void start(int port) {
        if (D) Log.d(TAG, "start");

        // Cancel any thread attempting to make a connection
        if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}
        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}

        // Start the thread to listen on a BluetoothServerSocket
        if (mAcceptThread == null) {
            mAcceptThread = new AcceptThread(port);
            mAcceptThread.start();
        }
        setState(STATE_LISTEN);
    }

    /**
     * TCP Client Mode
     * Start the ConnectThread to initiate a connection to a remote device.
     */
    public synchronized void connect(String ip, int port) {
        if (D) Log.d(TAG, "connect to: " + ip + ":" + port);

        // Cancel any thread attempting to make a connection
        if (mState == STATE_CONNECTING) {
            if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}

        Message msg = mHandler.obtainMessage(TCPService.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(TOAST, "Connecting.....");
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        // Start the thread to connect with the given device
        mConnectThread = new ConnectThread(ip, port);
        mConnectThread.start();
        setState(STATE_CONNECTING);
    }

    /**
     * Start the ConnectedThread to begin managing a tcp connection
     * @param socket  The Socket on which the connection was made
     * @param addr    The InetSocketAddress that has been connected
     */
    public synchronized void connected(Socket socket, InetSocketAddress addr) {
        if (D) Log.d(TAG, "connected to server");

        // Cancel the thread that completed the connection
        if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}
        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}
        // Cancel the accept thread because we only want to connect to one device
        if (mAcceptThread != null) {mAcceptThread.cancel(); mAcceptThread = null;}

        // Start the thread to manage the connection and perform transmissions
        mConnectedThread = new ConnectedThread(socket);
        mConnectedThread.start();

        // Send the name of the connected device back to the UI Activity
        Message msg = mHandler.obtainMessage(TCPService.MESSAGE_SERVER_IP);
        Bundle bundle = new Bundle();
        bundle.putString(DEVICE_NAME, addr.toString());
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        setState(STATE_CONNECTED);
    }

    public synchronized void connectedListen(Socket socket, InetAddress addr) {
        if (D) Log.d(TAG, "connected from client");

        // Cancel the thread that completed the connection
        if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}
        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}
        // Cancel the accept thread because we only want to connect to one device
        if (mAcceptThread != null) {mAcceptThread.cancel(); mAcceptThread = null;}

        // Start the thread to manage the connection and perform transmissions
        mConnectedThread = new ConnectedThread(socket);
        mConnectedThread.start();

        // Send the name of the connected device back to the UI Activity
        Message msg = mHandler.obtainMessage(TCPService.MESSAGE_CLIENT_IP);
        Bundle bundle = new Bundle();
        bundle.putString(DEVICE_NAME, addr.toString());
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
        if (mAcceptThread != null) {mAcceptThread.cancel(); mAcceptThread = null;}
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

        // Send a failure message back to the Activity
        Message msg = mHandler.obtainMessage(TCPService.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(TOAST, "Unable to connect device");
        msg.setData(bundle);
        mHandler.sendMessage(msg);
    }

    /**
     * Indicate that the connection was lost and notify the UI Activity.
     */
    private void connectionLost() {
        setState(STATE_NONE);

        // Send a failure message back to the Activity
        Message msg = mHandler.obtainMessage(TCPService.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(TOAST, "Device connection was lost");
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        String message = "Connected Device Left.";
        mHandler.obtainMessage(TCPService.MESSAGE_ADMIN, -1, -1, message)
                .sendToTarget();
    }

    /**
     * This thread runs while listening for incoming connections. It behaves
     * like a server-side client. It runs until a connection is accepted
     * (or until cancelled).
     */
    private class AcceptThread extends Thread {
        // The local server socket
        private final ServerSocket mmServerSocket;

        public AcceptThread(int port) {
            ServerSocket tmp = null;
            // Create a new listening server socket
            try {
                tmp = new ServerSocket(port);
            } catch (IOException e) {
                Log.e(TAG, "listen() failed", e);
            }
            mmServerSocket = tmp;
        }

        public void run() {
            if (D) Log.d(TAG, "BEGIN mAcceptThread" + this);
            setName("AcceptThread");
            Socket client = null;

            // Listen to the server socket if we're not connected
            while (mState != STATE_CONNECTED) {
                try {
                    // This is a blocking call and will only return on a
                    // successful connection or an exception
                    client = mmServerSocket.accept();
                } catch (IOException e) {
                    Log.e(TAG, "accept() failed", e);
                    break;
                }

                // If a connection was accepted
                if (client != null) {
                    synchronized (TCPService.this) {
                        switch (mState) {
                            case STATE_LISTEN:
                                connectedListen(client, client.getLocalAddress());
                                break;
                            case STATE_CONNECTING:
                                // Situation normal. Start the connected thread.
//                            connected(client, );
                                break;
                            case STATE_NONE:
                                // TCP connection is lost, failed, or disconnected.

                            case STATE_CONNECTED:
                                // Either not ready or already connected. Terminate new socket.
                                try {
                                    client.close();
                                } catch (IOException e) {
                                    Log.e(TAG, "Could not close unwanted socket", e);
                                }
                                break;
                        }
                    }
                }
            }
            if (D) Log.i(TAG, "END mAcceptThread");
        }

        public void cancel() {
            if (D) Log.d(TAG, "cancel " + this);
            try {
                mmServerSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of server failed", e);
            }
        }
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

//             Get a BluetoothSocket for a connection with the
//             given BluetoothDevice
//            try {
//            	mAddress = new InetSocketAddress(ip, port);
//                mmSocket = new Socket();
//            } catch (Exception e) {
//                Log.e(TAG, "create() failed", e);
//            }
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
                connectionFailed();
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
            byte[] buffer = new byte[1024];		// 1MegaByte
            int bytes;
            // Keep listening to the InputStream while connected
            while (conn) {
                try {
                    bytes = mmInStream.read(buffer);
                    if(bytes != -1)
                    {
                        // Send the obtained bytes to the UI Activity
                        mHandler.obtainMessage(TCPService.MESSAGE_READ, bytes, -1, buffer)
                                .sendToTarget();
//	                    bytes = (Integer) null;
                    }
                    else if(bytes == -1)	// the Device leave.
                    {
                        Log.e(TAG, "disconnected");
                        connectionLost();
                        conn = false;
                        TCPService.this.stop();
//                        setState(STATE_DISCONNECTED);
                        break;
                    }
                } catch (IOException e) {
                    Log.e(TAG, "disconnected", e);
                    connectionLost();
                    // Start the service over to restart listening mode
//                    TCPService.this.start();
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
                // Share the sent message back to the UI Activity
                mHandler.obtainMessage(TCPService.MESSAGE_WRITE, -1, -1, buffer)
                        .sendToTarget();
            } catch (Exception e) {
                Log.e(TAG, "Exception during write", e);
            }
        }

        public void cancel() {
            try {
                mmOutStream.flush();
                mmOutStream.close();

                mmInStream.close();

                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }
}
