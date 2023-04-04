package io.generalgalactic.capacitor.esp_idf_provisioning;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.espressif.provisioning.DeviceConnectionEvent;
import com.espressif.provisioning.ESPConstants;
import com.espressif.provisioning.ESPDevice;
import com.espressif.provisioning.ESPProvisionManager;
import com.espressif.provisioning.WiFiAccessPoint;
import com.espressif.provisioning.listeners.BleScanListener;
import com.espressif.provisioning.listeners.ProvisionListener;
import com.espressif.provisioning.listeners.ResponseListener;
import com.espressif.provisioning.listeners.WiFiScanListener;
import com.getcapacitor.Bridge;
import com.getcapacitor.PermissionState;
import com.getcapacitor.PluginMethod;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.generalgalactic.capacitor.esp_idf_provisioning.listeners.ConnectListener;
import io.generalgalactic.capacitor.esp_idf_provisioning.listeners.DisconnectListener;
import io.generalgalactic.capacitor.esp_idf_provisioning.listeners.EspProvisioningEventListener;
import io.generalgalactic.capacitor.esp_idf_provisioning.listeners.ScanListener;
import io.generalgalactic.capacitor.esp_idf_provisioning.listeners.ScanWiFiListener;
import io.generalgalactic.capacitor.esp_idf_provisioning.listeners.SendCustomDataStringListener;
import io.generalgalactic.capacitor.esp_idf_provisioning.listeners.UsesBluetooth;
import io.generalgalactic.capacitor.esp_idf_provisioning.listeners.UsesESPDevice;
import io.generalgalactic.capacitor.esp_idf_provisioning.listeners.WifiProvisionListener;

public class EspProvisioningBLE {

    private ESPProvisionManager provisionManager;
    private Map<String, DiscoveredBluetoothDevice> devices = new HashMap<String, DiscoveredBluetoothDevice>();
    private Handler handler = new Handler();
    private Bridge bridge;
    private ESPConstants.TransportType transport;
    private ESPConstants.SecurityType security;
    private boolean loggingEnabled = false;
    private EventCallback disconnectionHandler;
    private String currentDeviceName;
    private BroadcastReceiver broadcastReceiver;

    private static final long DEVICE_CONNECT_TIMEOUT = 20000;

    public EspProvisioningBLE(Bridge bridge, EspProvisioningEventListener eventListener){
        this.bridge = bridge;

        EspProvisioningBLE self = this;

        this.broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final String action = intent.getAction();

                if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                    final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                    debugLog(String.format("Bluetooth state change: %d", state));
                    eventListener.bluetoothStateChange(state);
                }
            }
        };

        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        bridge.getActivity().registerReceiver(broadcastReceiver, filter);

        // This listens for random device disconnections and will end up sending an out-of-band event to the capacitor plugin
        this.disconnectionHandler = new EventCallback(){
            @Subscribe(threadMode = ThreadMode.MAIN)
            public void onEvent(DeviceConnectionEvent event) {
                if(event.getEventType() == ESPConstants.EVENT_DEVICE_DISCONNECTED ){
                    debugLog("Device disconnected unexpectedly");

                    String deviceName = self.currentDeviceName;

                    // Call disconnect just to clean up all the state
                    self.disconnect(deviceName, null);

                    // Now notify up 1 level so a capacitor event can be sent
                    eventListener.deviceDisconnectedUnexpectedly(deviceName);
                }
            }
        };
    }

    public void setTransport(ESPConstants.TransportType transport) {
        if(transport == ESPConstants.TransportType.TRANSPORT_SOFTAP){
            throw new Error("softap transport is not supported");
        }
        this.transport = transport;
    }

    public void setSecurity(ESPConstants.SecurityType security) {
        this.security = security;
    }

    public void setLoggingEnabled(boolean loggingEnabled) {
        this.loggingEnabled = loggingEnabled;
    }

    private synchronized ESPProvisionManager getESPProvisionManager() {
        if (this.provisionManager == null) {
            this.provisionManager = ESPProvisionManager.getInstance(this.bridge.getContext());
        }
        return this.provisionManager;
    }

    public boolean hasBLEHardware(){
        return this.bridge.getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
    }

    public boolean bleIsEnabled(){
        if(!this.hasBLEHardware()) return false;

        BluetoothManager manager = (BluetoothManager) this.bridge.getActivity().getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = manager.getAdapter();

        if (adapter == null) return false; // Unable to access? Does this really happen?
        return adapter.isEnabled();
    }

    public boolean assertBluetooth(UsesBluetooth listener) {
        if(!this.hasBLEHardware()) {
            if (listener != null) listener.bleNotSupported();
            return false;
        }

        if(!this.blePermissionsGranted()) {
            if (listener != null) listener.blePermissionNotGranted();
            return false;
        }

        if(!this.bleIsEnabled()) {
            if (listener != null) listener.bleNotPoweredOn();
            return false;
        }

        return true;
    }

    private boolean blePermissionsGranted(){
        if (Build.VERSION.SDK_INT >= 31) {
            if (ActivityCompat.checkSelfPermission(this.bridge.getContext(), Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                Log.d("capacitor-esp-provision", String.format("MISSING PERMISSION: ", Manifest.permission.BLUETOOTH_SCAN));
                return false;
            }
            if (ActivityCompat.checkSelfPermission(this.bridge.getContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                Log.d("capacitor-esp-provision", String.format("MISSING PERMISSION: ", Manifest.permission.BLUETOOTH_CONNECT));
                return false;
            }
        } else {
            if (ActivityCompat.checkSelfPermission(this.bridge.getContext(), Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
                Log.d("capacitor-esp-provision", String.format("MISSING PERMISSION: ", Manifest.permission.BLUETOOTH));
                return false;
            }
            if (ActivityCompat.checkSelfPermission(this.bridge.getContext(), Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
                Log.d("capacitor-esp-provision", String.format("MISSING PERMISSION: ", Manifest.permission.BLUETOOTH_ADMIN));
                return false;
            }
        }
        return true;
    }

    @SuppressLint("MissingPermission")
    @PluginMethod
    public void searchESPDevices(String devicePrefix, ESPConstants.TransportType transport, ESPConstants.SecurityType security, ScanListener listener) {
        if (!this.assertBluetooth(null)) return;

        // if (ActivityCompat.checkSelfPermission(this.bridge.getContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
        //     Error permissionError = new Error("Not able to start scan as Location permission is not granted.");
        //     errorLog(permissionError);
        //     listener.errorOccurred(permissionError);
        //     return;
        // }

        // Clear the device cache
        this.devices = new HashMap<String, DiscoveredBluetoothDevice>();

        // This sucks, but the ESProvisioning library on iOS takes security and transport for the scan and
        // the Android version takes them for the connect. We're going to store it here from the scan and
        // use it during connect to make the plugin interface consistent. Other option is to take both values
        // on both calls.
        this.setSecurity(security);
        this.setTransport(transport);

        debugLog(String.format("searchESPDevices: devicePrefix=%s; transport=%s; security=%s;", devicePrefix, transport, security));

        EspProvisioningBLE self = this;

        BleScanListener bleScanListener = new BleScanListener() {

            @Override
            public void scanStartFailed() {
                listener.errorOccurred(new Error("Couldn't start scan"));
            }

            @Override
            public void onPeripheralFound(BluetoothDevice device, ScanResult scanResult) {
                if(!self.devices.containsKey(device.getName())) {
                    self.devices.put(device.getName(), new DiscoveredBluetoothDevice(device, scanResult));
                }
            }

            @Override
            public void scanCompleted() {
                List<DiscoveredBluetoothDevice> devices = new ArrayList<DiscoveredBluetoothDevice>();
                for (Map.Entry<String,DiscoveredBluetoothDevice> entry : self.devices.entrySet()) {
                    DiscoveredBluetoothDevice device = entry.getValue();
                    devices.add(device);
                }
                listener.foundDevices(devices);
            }

            @Override
            public void onFailure(Exception e) {
                String message = e.getMessage();
                if (message.indexOf("errorCode=2") > -1) {
                    // statusCode=2 means that the nearby devices permission is not allowed in the device app settings
                    // For some reason this can be true and all the permissions checks coded here are valid
                    // Deciding to map this error here so the UI can at least response with a useful message
                    listener.blePermissionsIssue();
                }else {
                    Error bleScanFailedError = new Error("BLE Scan failed: " + e.getMessage());
                    errorLog(bleScanFailedError);
                    listener.errorOccurred(bleScanFailedError);
                }
            }
        };

        this.getESPProvisionManager().searchBleEspDevices(devicePrefix, bleScanListener);
    }

    @SuppressLint("MissingPermission")
    public void connect(String deviceName, String proofOfPossession, ConnectListener listener){
        if (!this.assertBluetooth(null)) return;

        DiscoveredBluetoothDevice bleDevice = this.devices.get(deviceName);
        if(bleDevice == null) {
            listener.deviceNotFound(deviceName);
            return;
        }

        long connectTimeout = this.DEVICE_CONNECT_TIMEOUT;

        Runnable connectionTimeoutTask = new Runnable() {

            @Override
            public void run() {
                debugLog("Capacitor ESP connect timeout");
                errorLog(new Error(String.format("Timed out after %s seconds while trying to connect to device: %s", connectTimeout, deviceName)));
                listener.connectionTimedOut();
                // EventBus.getDefault().unregister(connectionHandler); // TODO: how to fix this reference issue?
            }

        };

        EspProvisioningBLE provisioningBLE = this;

        EventCallback connectionHandler = new EventCallback(){

            @Subscribe(threadMode = ThreadMode.MAIN)
            public void onEvent(DeviceConnectionEvent event) {
                debugLog(String.format("ESP Connection handler callback: %s",event.getEventType()));

                handler.removeCallbacks(connectionTimeoutTask); // Cancels connection timeout task
                EventBus.getDefault().unregister(this);

                switch (event.getEventType()) {

                    case ESPConstants.EVENT_DEVICE_CONNECTED:
                        debugLog("Device connected event received");
                        provisioningBLE.startListeningForDisconnection(bleDevice.getName());

                        ESPDevice device = provisionManager.getEspDevice();
                        device.setProofOfPossession(proofOfPossession);

                        // Initing a session during connection so that secret failures happen
                        // during connection (like iOS) and not later during other operations.
                        // This also let's me send a more specific error - rather than a generic code=4
                        device.initSession(new ResponseListener() {

                            @Override
                            public void onSuccess(byte[] returnData) {
                                listener.connected(device);
                            }

                            @Override
                            public void onFailure(Exception e) {
                                listener.initSessionFailed(e);
                            }

                        });
                        break;

                    case ESPConstants.EVENT_DEVICE_DISCONNECTED:
                        debugLog("Device disconnected event received");
                        listener.connectionFailed();
                        break;

                    case ESPConstants.EVENT_DEVICE_CONNECTION_FAILED:
                        debugLog("Device connection failed event received");
                        listener.connectionFailed();
                        break;
                }
            }
        };

        EventBus.getDefault().register(connectionHandler);

        ESPDevice espDevice = this.getESPProvisionManager().createESPDevice(transport, security);
        debugLog(String.format("Connecting:. %s, %s, %s", espDevice.getDeviceName(), bleDevice.getName(), bleDevice.getServiceUUID()));

        espDevice.connectBLEDevice(bleDevice.getBluetoothDevice(), bleDevice.getServiceUUID());

        this.handler.postDelayed(connectionTimeoutTask, this.DEVICE_CONNECT_TIMEOUT );
    }

    private void startListeningForDisconnection(String deviceName){
        this.currentDeviceName = deviceName;
        EventBus.getDefault().register(this.disconnectionHandler);
    }

    private void stopListeningForDisconnection(){
        this.currentDeviceName = null;
        EventBus.getDefault().unregister(this.disconnectionHandler);
    }

    private ESPDevice getESPDevice(String deviceName){
        DiscoveredBluetoothDevice bleDevice = this.devices.get(deviceName);
        if(bleDevice == null) return null;

        ESPDevice espDevice = this.getESPProvisionManager().getEspDevice();
        if(espDevice == null) return null;

        if( !espDevice.getDeviceName().equals(bleDevice.getName()) ){
            debugLog(String.format("Device mismatch. %s != %s", espDevice.getDeviceName(), bleDevice.getName()));
            return null;
        }

        return espDevice;
    }

    private ESPDevice getESPDevice(String deviceName, UsesESPDevice listener){
        ESPDevice device = this.getESPDevice(deviceName);

        if (device == null && listener != null) {
            listener.deviceNotFound(deviceName);
        }

        return device;
    }

    public void scanWifiList(String deviceName, ScanWiFiListener listener) {
        if (!this.assertBluetooth(null)) return;

        ESPDevice espDevice = this.getESPDevice(deviceName, listener);
        if(espDevice == null) return;

        espDevice.scanNetworks(new WiFiScanListener() {

            @Override
            public void onWifiListReceived(ArrayList<WiFiAccessPoint> wifiList) {
                listener.foundWiFiNetworks(wifiList);
            }

            @Override
            public void onWiFiScanFailed(Exception e) {
                errorLog(e);
                listener.wiFiScanFailed(e);
            }

        });
    }

    public void provision(String deviceName, String ssid, String passPhrase, WifiProvisionListener listener) {
        if (!this.assertBluetooth(null)) return;

        ESPDevice espDevice = this.getESPDevice(deviceName, listener);
        if (espDevice == null) return;

        espDevice.provision(ssid, passPhrase, new ProvisionListener() {

            @Override
            public void createSessionFailed(Exception e) {
                Error createSessionError = new Error("Couldn't create a secure session", e);
                errorLog(createSessionError);
                listener.provisioningFailed(createSessionError);
            }

            @Override
            public void wifiConfigSent() {
                debugLog("WiFi config sent");
            }

            @Override
            public void wifiConfigFailed(Exception e) {
                Error wifiConfigFailedError = new Error("Failed to send WiFi config", e);
                errorLog(wifiConfigFailedError);
                listener.provisioningFailed(wifiConfigFailedError);
            }

            @Override
            public void wifiConfigApplied() {
                debugLog("WiFi config applied");
            }

            @Override
            public void wifiConfigApplyFailed(Exception e) {
                Error wifiConfigApplyError = new Error("Failed to apply WiFi config", e);
                errorLog(wifiConfigApplyError);
                listener.provisioningFailed(wifiConfigApplyError);
            }

            @Override
            public void provisioningFailedFromDevice(final ESPConstants.ProvisionFailureReason failureReason) {
                switch (failureReason) {
                    case AUTH_FAILED:
                        Error authFailedError = new Error("WiFi credential error. Please check your SSID and password and try again");
                        errorLog(authFailedError);
                        listener.provisioningFailed(authFailedError);
                        break;
                    case DEVICE_DISCONNECTED:
                        Error deviceDisconnectedError = new Error("Device Disconnected unexpectedly");
                        errorLog(deviceDisconnectedError);
                        listener.provisioningFailed(deviceDisconnectedError);
                        break;
                    case NETWORK_NOT_FOUND:
                        Error networkNotFoundError = new Error(String.format("WiFi network not found", ssid));
                        errorLog(networkNotFoundError);
                        listener.provisioningFailed(networkNotFoundError);
                        break;
                    case UNKNOWN:
                    default:
                        Error unknownError = new Error("Unknown Error");
                        errorLog(unknownError);
                        listener.provisioningFailed(unknownError);
                }
            }

            @Override
            public void deviceProvisioningSuccess() {
                listener.provisioningSuccess();
            }

            @Override
            public void onProvisioningFailed(Exception e) {
                errorLog("Error provisioning device: " + e.getMessage(), e);
                listener.provisioningFailed(new Error("Provisioning Failed: " + e.getMessage()));
            }

        });
    }

    public void sendCustomDataString(String deviceName, String path, String dataString, SendCustomDataStringListener listener) {
        ESPDevice espDevice = this.getESPDevice(deviceName, listener);
        if(espDevice == null) return;

        byte[] bytes = dataString.getBytes(StandardCharsets.UTF_8);

        espDevice.sendDataToCustomEndPoint(path, bytes, new ResponseListener(){

            @Override
            public void onSuccess(byte[] returnData) {
                String returnString = new String(returnData, StandardCharsets.UTF_8);
                debugLog(String.format("Sent custom data: sent=%s returnString=%s", dataString, returnString));
                listener.sentCustomDataStringWithResponse(returnString);
            }

            @Override
            public void onFailure(Exception e) {
                Error sendCustomDataStringError = new Error("Error sending custom data string: " + e.getMessage(), e);
                errorLog(sendCustomDataStringError);
                listener.failedToSendCustomDataString(sendCustomDataStringError);
            }

        });
    }

    public void disconnect(String deviceName, DisconnectListener listener) {
        this.stopListeningForDisconnection();

        ESPDevice espDevice = this.getESPDevice(deviceName); // don't pass listener since we don't care about deviceNotFound() for disconnection.
        if (espDevice != null) espDevice.disconnectDevice();

        if (listener != null ) listener.deviceDisconnected();

        // Stopped clearing devices because it was causing issues. We call 'disconnect' from the app side a lot
        // to ensure we aren't leaking BLE connections. We need this map around if the app fails to connect due to a
        // bad proof of possession and the user want's to try again. Otherwise they have to rescan all devices.
        // This will leak devices - maybe we should have a timer that evicts old discovered devices
        // this.devices = new HashMap<String, DiscoveredBluetoothDevice>();
    }

    private void debugLog(String message){
        if(loggingEnabled) Log.d("capacitor-esp-provision", message);
    }

    private void errorLog(String message, Throwable error){
        error.printStackTrace();
        if(loggingEnabled) Log.e("capacitor-esp-provision", message, error);
    }

    private void errorLog(Throwable error){
        error.printStackTrace();
        if(loggingEnabled) Log.e("capacitor-esp-provision", error.getMessage(), error);
    }

}
