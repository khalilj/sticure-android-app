package com.sticurity;


import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.Toast;

import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private static final String EOM = "#!#";
    final String serverUrl = "http://10.20.2.46:8080/reportEvent";
    // SPP UUID service
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");


    BluetoothAdapter bluetoothAdapter = null;
    Handler messageHandler;
    final int handlerState = 0;
    String blueToothDeviceName = "HC-05";
    BluetoothDevice bluetoothDevice = null;
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

        mConnectedThread = new ConnectedThread();
        mConnectedThread.start();

    }

    private int getRandomId() {
        return 1000 + (int) (Math.random() * ((Integer.MAX_VALUE - 1000) + 1));
    }

    private class HttpRequestTask extends AsyncTask<SecurityEvent, Void, String> {
        @Override
        protected String doInBackground(SecurityEvent... events) {
            try{
                RestTemplate restTemplate = new RestTemplate();
                restTemplate.getMessageConverters().add(new MappingJackson2HttpMessageConverter());
                restTemplate.postForEntity(serverUrl, events[0], SecurityEvent.class);
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

        return device.createRfcommSocketToServiceRecord(BTMODULEUUID);
        //creates secure outgoing connecetion with BT device using UUID
    }

    //create new class for connect thread
    private class ConnectedThread extends Thread {
        BluetoothSocket btSocket = null;
        InputStream tmpIn = null;
        OutputStream tmpOut = null;
        DataInputStream mmInStream;
        OutputStream mmOutStream;

        //creation of the connect thread
        public ConnectedThread() {
            init();
        }

        private void init() {

            try {
                btSocket = createBluetoothSocket(bluetoothDevice);
                btSocket.connect();
                tmpIn = btSocket.getInputStream();
                tmpOut = btSocket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }

            mmInStream = new DataInputStream(tmpIn);
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

                    sendEvent(readMessage);

                    btSocket.close();
                    init();

//                    messageHandler.obtainMessage(handlerState, bytes, -1, readMessage).sendToTarget();
//
//                    int eomIndex = readMessage.indexOf(EOM);
//                    if (eomIndex != -1){
//                        message.append(readMessage.substring(0, eomIndex));
//                        // Send the obtained bytes to the UI Activity via handler
//                        messageHandler.obtainMessage(handlerState, bytes, -1, message.toString()).sendToTarget();
//                        message = new StringBuilder();
//                        readMessage = readMessage.substring(eomIndex + EOM.length(), readMessage.length()-1);
//                    }
//
//                    message.append(readMessage);
                } catch (IOException e) {
                    e.printStackTrace();
                    break;
                }
            }
        }
    }

    private void sendEvent(String eventType) {
        SecurityEvent event = new SecurityEvent();
        event.setId(getRandomId());
        setPossition(event);
        event.setTime(new Date());

        switch (eventType){
            case "G":
                event.setTitle("Gun Shot At Naz");
                event.setType("Crime");
                event.setImgUrl("{ url:\u0027img/pistol.png\u0027, scaledSize:[32,40], origin: [0,0], anchor: [16,40] }");
                break;
            case "C":
                event.setTitle("Crash at Naz");
                event.setType("Crash");
                return;
//                break;
            case "F":
                event.setTitle("Fire at Naz");
                event.setType("Fire");
                event.setImgUrl("{ url:\u0027img/fire.png\u0027, scaledSize:[32,40], origin: [0,0], anchor: [16,40] }");
                break;
        }

        new HttpRequestTask().execute(event);
    }

    private void setPossition(SecurityEvent event) {
        List<String> positions = getPossitions();
        int randomPosInd = (int) (Math.random() * positions.size());
        String[] latLon = positions.get(randomPosInd).split(":");
        event.setLat(Double.valueOf(latLon[0]));
        event.setLon(Double.valueOf(latLon[1]));
    }

    private List<String> getPossitions() {
        List<String> list = new ArrayList<>();
        list.add("32.696002:35.301039");
        list.add("32.707126:35.301647");
        list.add("32.693525:35.303422");
        list.add("32.6960018:35.3010389");
        list.add("32.6805991:35.2921629");
        list.add("32.68475298:35.27765751");
        list.add("32.69374638:35.29950142");
        list.add("32.69327687:35.29950142");
        list.add("32.70129436:35.2958107");
        list.add("32.7106833:35.3000164");
        list.add("32.71321092:35.30662537");
        list.add("32.70685562:35.28538227");
        list.add("32.70447226:35.31198978");
        list.add("32.7085032:35.2969909");

        return list;
    }
}
