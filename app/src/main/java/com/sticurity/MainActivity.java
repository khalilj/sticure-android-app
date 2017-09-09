package com.sticurity;


import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

import javax.net.ssl.HttpsURLConnection;

public class MainActivity extends AppCompatActivity {
    private static final String EOM = "#!#";
    // SPP UUID service
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");


    BluetoothAdapter bluetoothAdapter = null;
    Handler bluetoothIn;
    final int handlerState = 0;
    String blueToothDeviceName = "HC-05";
    BluetoothDevice bluetoothDevice = null;
    private BluetoothSocket btSocket = null;
    private ConnectedThread mConnectedThread;

    // SPP UUID service - this should work for most devices
    private static final UUID BTMODULEUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(getApplicationContext(), "Device doesnt Support Bluetooth", Toast.LENGTH_LONG).show();
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            Intent enableAdapter = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableAdapter, 0);
        }

        Set<BluetoothDevice> bondedDevices = bluetoothAdapter.getBondedDevices();


        if (bondedDevices.isEmpty()) {
            Toast.makeText(getApplicationContext(), "Please Pair the Device first", Toast.LENGTH_LONG).show();
        } else {
            for (BluetoothDevice aDevice : bondedDevices) {
                if (aDevice.getName().startsWith(blueToothDeviceName)) //Replace with iterator.getName() if comparing Device names.
                {
                    Toast.makeText(getApplicationContext(), aDevice.getName(), Toast.LENGTH_LONG).show();
                    bluetoothDevice = aDevice;
                    break;
                }
            }
        }

        if (bluetoothDevice == null) {
            Toast.makeText(getApplicationContext(), "Couldn't find device: " + blueToothDeviceName, Toast.LENGTH_LONG).show();
            return;
        }

        bluetoothIn = new Handler() {
            public void handleMessage(android.os.Message msg) {
                if (msg.what == handlerState) {
                    String readMessage = (String) msg.obj;
                    Toast.makeText(getApplicationContext(), "Message: " + readMessage, Toast.LENGTH_LONG).show();

                    SecurityEvent event = new SecurityEvent();
                    event.setId(getRandomId());
                    event.setLat(100);
                    event.setLon(100);
                    event.setTime(new Date());
                    event.setTitle("Crash at Naz");
                    event.setType("Crash");
                    new HttpRequestTask().execute(event);
                }
            }
        };
    }

    private int getRandomId() {
        return 1000 + (int) (Math.random() * ((Integer.MAX_VALUE - 1000) + 1));
    }

    private class HttpRequestTask extends AsyncTask<SecurityEvent, Void, String> {
        @Override
        protected String doInBackground(SecurityEvent... events) {
            try{
                final String url = "http://10.20.2.56:8080/reportEvent";
                RestTemplate restTemplate = new RestTemplate();
                restTemplate.getMessageConverters().add(new MappingJackson2HttpMessageConverter());
                restTemplate.postForEntity(url, events[0], SecurityEvent.class);
            }
            catch(Throwable t){
                t.printStackTrace();
            }

            return "";
        }

//        @Override
//        protected void onPostExecute(Greeting greeting) {
//            TextView greetingIdText = (TextView) findViewById(R.id.id_value);
//            TextView greetingContentText = (TextView) findViewById(R.id.content_value);
//            greetingIdText.setText(greeting.getId());
//            greetingContentText.setText(greeting.getContent());
//        }
    }

    private BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException {

        return  device.createRfcommSocketToServiceRecord(BTMODULEUUID);
        //creates secure outgoing connecetion with BT device using UUID
    }

    @Override
    public void onResume() {
        super.onResume();

        try {
            btSocket = createBluetoothSocket(bluetoothDevice);
            btSocket.connect();
        } catch (IOException e) {
            Toast.makeText(getBaseContext(), "Socket creation failed", Toast.LENGTH_LONG).show();
        } finally {
            if (btSocket != null) {
                try {
                    btSocket.close();
                } catch (IOException e2) {
                    //insert code to deal with this
                }
            }
        }

        mConnectedThread = new ConnectedThread(btSocket);
        mConnectedThread.start();

        //I send a character when resuming.beginning transmission to check device is connected
        //If it is not an exception will be thrown in the write method and finish() will be called
        mConnectedThread.write("x");
    }

    @Override
    public void onPause()
    {
        super.onPause();
        try
        {
            //Don't leave Bluetooth sockets open when leaving activity
            btSocket.close();
        } catch (IOException e2) {
            //insert code to deal with this
        }
    }

    //create new class for connect thread
    private class ConnectedThread extends Thread {
        private final DataInputStream mmInStream;
        private final OutputStream mmOutStream;

        //creation of the connect thread
        public ConnectedThread(BluetoothSocket socket) {
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try {
                //Create I/O streams for connection
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) { }
            mmInStream = new DataInputStream(tmpIn);
            //mmInStream = tmpIn;//
            mmOutStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[256];
            int bytes;
            final byte delimiter = 10; //This is the ASCII code for a newline character

            // Keep looping to listen for received messages
            StringBuilder message = new StringBuilder();
            int readBufferPosition = 0;
            byte[] readBuffer = new byte[1024];

            while (true) {
                try {
                    bytes = mmInStream.read(buffer);
                    String readMessage = new String(buffer, 0, bytes);
                    System.out.println(readMessage);
                    int eomIndex = readMessage.indexOf(EOM);
                    if (eomIndex != -1){
                        message.append(readMessage.substring(0, eomIndex));
                        // Send the obtained bytes to the UI Activity via handler
                        bluetoothIn.obtainMessage(handlerState, bytes, -1, message.toString()).sendToTarget();
                        message = new StringBuilder();
                        readMessage = readMessage.substring(eomIndex + EOM.length(), readMessage.length()-1);
                    }

                    message.append(readMessage);
                } catch (IOException e) {
                    e.printStackTrace();
                    break;
                }
            }
        }

        //write method
        public void write(String input) {
            byte[] msgBuffer = input.getBytes();           //converts entered String into bytes
            try {
                mmOutStream.write(msgBuffer);                //write bytes over BT connection via outstream
            } catch (IOException e) {
                //if you cannot write, close the application
                Toast.makeText(getBaseContext(), "Connection Failure", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }
}
