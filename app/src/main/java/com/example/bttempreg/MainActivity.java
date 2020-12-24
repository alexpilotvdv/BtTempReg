package com.example.bttempreg;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_ENABLE_BT = 1;
    BluetoothAdapter bluetoothAdapter;
    ArrayList<String> pairedDeviceArrayList;
    ListView listViewPairedDevice;
    ArrayAdapter pairedDeviceAdapter;
    private UUID myUUID;
    ThreadConnectBTdevice myThreadConnectBTdevice;
    ThreadConnected myThreadConnected;
    private StringBuilder sb = new StringBuilder();
    private StringBuilder sbc = new StringBuilder();
    public TextView textInfo;
    public TextView textBt;
    public static Handler mHandler;
    public String data;
    public String btx;//соберем х
    public String bty;//соберем у
    public String btz;
    public static float fbtx;
    public static float fbty;
    public static float fbtz;
    public static float corx=0;
    public static float cory=0; //для коррекции показаний через кнопки
    public static float fbtycorr=0;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final String UUID_STRING_WELL_KNOWN_SPP = "00001101-0000-1000-8000-00805F9B34FB";
        listViewPairedDevice = (ListView)findViewById(R.id.pairedlist);
        textInfo = (TextView)findViewById(R.id.textInfo);
        textBt = (TextView)findViewById(R.id.textBt);
        ///для получения сообщений от потока блютус
        mHandler = new Handler() {
            @SuppressLint("HandlerLeak")
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                mHandler.obtainMessage();
                data = msg.getData().getString("Key");
                textBt.setText(data);
                // Log.d("xxx",data);
                try {
                    fbtx=Float.parseFloat(btx);
                    fbty=Float.parseFloat(bty);
                    fbtz=Float.parseFloat(btz);
                } catch (InternalError error){
                    error.printStackTrace();
                }


            }
        };
        Log.d("xxx","data");
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)){
            Toast.makeText(this, "BLUETOOTH NOT support", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        myUUID = UUID.fromString(UUID_STRING_WELL_KNOWN_SPP);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not supported on this hardware platform", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        String stInfo = bluetoothAdapter.getName() + " " + bluetoothAdapter.getAddress();
        textInfo.setText(String.format("Это устройство: %s", stInfo));
    }
    @Override
    protected void onStart() { // Запрос на включение Bluetooth
        super.onStart();
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        }
        setup();
    }
    private void setup() { // Создание списка сопряжённых Bluetooth-устройств
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        if (pairedDevices.size() > 0) { // Если есть сопряжённые устройства
            pairedDeviceArrayList = new ArrayList<>();
            for (BluetoothDevice device : pairedDevices) { // Добавляем сопряжённые устройства - Имя + MAC-адресс
                pairedDeviceArrayList.add(device.getName() + "\n" + device.getAddress());
            }
            pairedDeviceAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, pairedDeviceArrayList);
            listViewPairedDevice.setAdapter(pairedDeviceAdapter);
            listViewPairedDevice.setOnItemClickListener(new AdapterView.OnItemClickListener() { // Клик по нужному устройству
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    listViewPairedDevice.setVisibility(View.GONE); // После клика скрываем список
                    String  itemValue = (String) listViewPairedDevice.getItemAtPosition(position);
                    String MAC = itemValue.substring(itemValue.length() - 17); // Вычленяем MAC-адрес
                    BluetoothDevice device2 = bluetoothAdapter.getRemoteDevice(MAC);
                    myThreadConnectBTdevice = new ThreadConnectBTdevice(device2);
                    myThreadConnectBTdevice.start();  // Запускаем поток для подключения Bluetooth
                }
            });
        }
    }

    @Override
    protected void onDestroy() { // Закрытие приложения
        super.onDestroy();
        if(myThreadConnectBTdevice!=null) myThreadConnectBTdevice.cancel();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BT) { // Если разрешили включить Bluetooth, тогда void setup()
            if (resultCode == Activity.RESULT_OK) {
                setup();
            } else { // Если не разрешили, тогда закрываем приложение
                Toast.makeText(this, "BlueTooth не включён", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }




    /*public boolean onTouch(View v, MotionEvent event) {
        switch(v.getId()) { // определяем какая кнопка
            case R.id.rightAng:
                switch (event.getAction()) { // определяем нажата или отпущена
                    case MotionEvent.ACTION_DOWN:
                        corx = (float) (corx + 0.1);
                        break;
                    case MotionEvent.ACTION_UP:

                        break;
                }
            case R.id.leftAng:
                switch (event.getAction()) { // определяем нажата или отпущена
                    case MotionEvent.ACTION_DOWN:
                        corx = (float) (corx - 0.1);
                        break;
                    case MotionEvent.ACTION_UP:

                        break;
                }
                break;
        }
        return true;
    }*/


    private class ThreadConnectBTdevice extends Thread { // Поток для коннекта с Bluetooth
        private BluetoothSocket bluetoothSocket = null;
        private ThreadConnectBTdevice(BluetoothDevice device) {
            try {
                bluetoothSocket = device.createRfcommSocketToServiceRecord(myUUID);
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }
        @Override
        public void run() { // Коннект
            boolean success = false;
            try {
                bluetoothSocket.connect();
                success = true;
            }
            catch (IOException e) {
                e.printStackTrace();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, "Нет коннекта, проверьте Bluetooth-устройство с которым хотите соединица!", Toast.LENGTH_LONG).show();
                        listViewPairedDevice.setVisibility(View.VISIBLE);
                    }
                });
                try {
                    bluetoothSocket.close();
                }
                catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
            if(success) {  // Если законнектились, тогда открываем панель с кнопками и запускаем поток приёма и отправки данных
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // открываем панель с кнопками вызываем активность с ful меняем координаты
                    }
                });

                myThreadConnected = new ThreadConnected(bluetoothSocket);
                myThreadConnected.start(); // запуск потока приёма и отправки данных
            }
        }

        public void cancel() {
            Toast.makeText(getApplicationContext(), "Close - BluetoothSocket", Toast.LENGTH_LONG).show();
            try {
                bluetoothSocket.close();
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }

    } // END ThreadConnectBTdevice:



    private class ThreadConnected extends Thread {    // Поток - приём и отправка данных
        private final InputStream connectedInputStream;
        private final OutputStream connectedOutputStream;
        private String sbprint;
        int flag=0;
        int flag2=0;
        int flagfirstx=0;
        int posx=0;
        int posy=0;
        int posz=0;
        int count=0;

        public ThreadConnected(BluetoothSocket socket) {
            InputStream in = null;
            OutputStream out = null;
            try {
                in = socket.getInputStream();
                out = socket.getOutputStream();
            }
            catch (IOException e) {
                e.printStackTrace();
            }
            connectedInputStream = in;
            connectedOutputStream = out;
        }

        @Override
        public void run() { // Приём данных

            while (true) {

                try {
                    byte[] buffer = new byte[1];
                    char bytes = (char) connectedInputStream.read(buffer);
                    String strIncom = new String(buffer, 0, bytes);
                    //sbc.append(strIncom);
                    // Log.d("xxx", strIncom + " " + strIncom.length() );
                    if(strIncom.equals("x")){
                        if(count==0){
                            //sbc.append(strIncom);
                            //posx=0;
                            //flag=1;
                            count=count+1;
                        }
                        else{
                            // Log.d("xxx", "posx=" + posx );
                            // Log.d("xxx", "posy=" + posy );
                            btx=sbc.substring(0,posy);
                            // Log.d("xxx", "btx=" + btx );
                            bty=sbc.substring(posy,posz);
                            //  Log.d("xxx","bty=" + bty );
                            btz=sbc.substring(posz,sbc.length());
                            //  Log.d("xxx","btz=" + btz );
                            sbc.delete(0, sbc.length());
                            flag2=1;
                            count=1;
                            //      Log.d("xxx", btx );
                        }

                    }
                    else if(strIncom.equals("y")){

                        posy = count-1;
                        // flag=1;

                    }
                    else if(strIncom.equals("z")){

                        posz = count-1;

                    }
                    else{
                        if (count!=0) {
                            sbc.append(strIncom);
                            count = count + 1;
                            //  Log.d("xxx", "str=" + sbc);
                        }
                    }
                    //  sb.append(strIncom); // собираем символы в строку

                    // int endOfLineIndex = sb.indexOf("\r\n"); // определяем конец строки
                    //Log.d("xxx", sb.substring(0));
                    if (flag2==1){
                        //  Log.d("xxx", "btx=" + btx );
                        //sbprint = sb.substring(0, sb.length());
                        //  sb.delete(0, sb.length());
                        sbprint="x=" + btx + " " + "y=" + bty + "z=" + btz;
                        flag2=0;
                        runOnUiThread(new Runnable() { // Вывод данных
                            @Override
                            public void run() {
                                //  передача полученных данных

                                Message msg = Message.obtain();
                                Bundle bundle = new Bundle();
                                bundle.putString("Key", sbprint);
                                msg.setData(bundle);
                                if (mHandler!=null){
                                    mHandler.sendMessage(msg);
                                }

                            }
                        });
                    }
                } catch (IOException e) {
                    break;
                }
            }
        }


    }


}
