package com.kkb.ipcam;

import android.content.Context;
import android.os.Handler;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;

public class UDPService extends Thread {

    // Message types sent from the UDPService Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_SERVER_IP = 4;
    public static final int MESSAGE_CLIENT_IP = 5;
    public static final int MESSAGE_ADMIN = 6;
    public static final int MESSAGE_TOAST = 7;

    // Debugging
    private static final String TAG = "UDPSERVICE";
    private static final boolean D = true;

    // Member fields
    private Handler mHandler;

    private DatagramSocket socket;
    private DatagramPacket packet_tx;

    private String IP;
    private int Port;

    private boolean conn = false;

    /**
     * Constructor. Prepares a new UDP session.
     * @param handler  A Handler to send messages back to the UI Activity
     */
    public UDPService(Context context, Handler handler, String strIP, String strPort) {
        mHandler = handler;

        set(strIP, Integer.valueOf(strPort));
        try {
            socket = new DatagramSocket();
        } catch (SocketException e) {
            String msg = "Failed to bind the socket.";
            mHandler.obtainMessage(UDPService.MESSAGE_TOAST, -1, -1, msg).sendToTarget();
            return;
        }

        UDPService.this.start();
    }

    public void set(String ip, int port) {
        this.IP = ip;
        this.Port = port;
    }

    public void run() {

        byte[] buffer_rx = new byte[1024];
        DatagramPacket packet_rx = new DatagramPacket(buffer_rx, buffer_rx.length);
        String addr;

        /* receiving data loop for UDP */
        conn = true;
        while (conn) {
            try {
                socket.receive(packet_rx);
                addr = packet_rx.getAddress().getHostAddress()
                        + ":" + packet_rx.getPort() + "ADDREND";

                byte[] addrBytes = addr.getBytes();
                byte[] entireBytes = new byte[addrBytes.length + packet_rx.getLength()];
                System.arraycopy(addrBytes, 0, entireBytes, 0, addrBytes.length);
                System.arraycopy(buffer_rx, 0, entireBytes, addrBytes.length, packet_rx.getLength());

//                mHandler.obtainMessage(UDPService.MESSAGE_ADMIN, addr).sendToTarget();
                mHandler.obtainMessage(UDPService.MESSAGE_READ, packet_rx.getLength(), -1,
                        entireBytes).sendToTarget();
            } catch (SocketTimeoutException e) {
                Log.e(TAG, "UDP run: " + e);
            } catch (IOException e) {
                Log.e(TAG, "UDP run: " + e);
            }
        }
    }

    public void close() {
        conn = false;

        if(mHandler != null)
        {
            mHandler = null;
        }

        if(socket != null)
        {
            try {
//                socket.disconnect();
                socket.close();
            } catch (Exception e) {
                Log.e(TAG, String.valueOf(e));
            }
        }

//        UDPService.this.stop();
    }

    public void sendString(String message) {
        try {
            byte[] buffer_tx = message.getBytes();

            /* UDP Packet can have only 64KBytes so, need to be fragmented to send through UDP */
            if(buffer_tx.length > 64000)
            {
                int socketBufferSz = 64000;
                int packetSize = buffer_tx.length / socketBufferSz;
                int dataRem = buffer_tx.length % socketBufferSz;
                byte[][] packets;

                if(dataRem == 0)
                {
                    packets = new byte[packetSize][socketBufferSz];
                    for(int i = 0; i < packetSize; i++)
                    {
                        System.arraycopy(buffer_tx, i*socketBufferSz, packets[i],
                                0, socketBufferSz);
                    }

                    for(int i = 0; i < packetSize; i++)
                    {
                        packet_tx = new DatagramPacket(packets[i], socketBufferSz,
                                InetAddress.getByName(IP), Port);
                        socket.send(packet_tx);
                    }
                }
                else
                {
                    packets = new byte[packetSize][socket.getSendBufferSize()];
                    for(int i = 0; i < packetSize; i++)
                    {
                        System.arraycopy(buffer_tx, i*socketBufferSz, packets[i],
                                0, socketBufferSz);
                    }

                    byte[] packetRem = new byte[dataRem];
                    System.arraycopy(buffer_tx, packetSize * socketBufferSz, packetRem,
                            0, dataRem);

                    for(int i = 0; i < packetSize; i++)
                    {
                        packet_tx = new DatagramPacket(packets[i], socketBufferSz,
                                InetAddress.getByName(IP), Port);
                        socket.send(packet_tx);
                    }

                    packet_tx = new DatagramPacket(packetRem, packetRem.length,
                            InetAddress.getByName(IP), Port);
                    socket.send(packet_tx);
                }
            }
            else
            {
                packet_tx = new DatagramPacket(buffer_tx, buffer_tx.length, InetAddress.getByName(IP), Port);
                socket.send(packet_tx);
            }

            mHandler.obtainMessage(UDPService.MESSAGE_WRITE, new String(buffer_tx, StandardCharsets.UTF_8)).sendToTarget();
        } catch (UnknownHostException e) {
            Log.e(TAG, "sendString: " + e);
        } catch (IOException e) {
            Log.e(TAG, "sendString: " + e);
        }
    }

    public void sendBytes(byte[] bytes) {
        try {

            packet_tx = new DatagramPacket(bytes, bytes.length, InetAddress.getByName(IP), Port);
            socket.send(packet_tx);

            mHandler.obtainMessage(UDPService.MESSAGE_WRITE, new String(bytes, StandardCharsets.UTF_8)).sendToTarget();
        } catch (UnknownHostException e) {
            Log.e(TAG, "sendBytes: " + e);
        } catch (IOException e) {
            Log.e(TAG, "sendBytes: " + e);
        } catch (Exception e) {
            Log.e(TAG, "sendByes: " + e);
        }
    }
}