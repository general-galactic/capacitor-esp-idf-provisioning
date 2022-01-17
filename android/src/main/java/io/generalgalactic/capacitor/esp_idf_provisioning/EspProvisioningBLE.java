package io.generalgalactic.capacitor.esp_idf_provisioning;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.util.Log;

import androidx.core.app.ActivityCompat;

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
import com.getcapacitor.PluginMethod;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EspProvisioningBLE {

    private ESPProvisionManager provisionManager;
    private Map<String, DiscoveredBluetoothDevice> devices = new HashMap<String, DiscoveredBluetoothDevice>();
    private Handler handler = new Handler();
    private Bridge bridge;
    private ESPConstants.TransportType transport;
    private ESPConstants.SecurityType security;
    private boolean loggingEnabled = false;


    private static final long DEVICE_CONNECT_TIMEOUT = 20000;

    public EspProvisioningBLE(Bridge bridge){
        this.bridge = bridge;
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

    public boolean assertBluetoothAdapter() {
        if(!this.hasBLEHardware()){
            throw new Error("This device does not support BLE.");
        }

        BluetoothManager manager = (BluetoothManager) this.bridge.getActivity().getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = manager.getAdapter();

        if (adapter == null) {
            throw new Error("Unable to access BLE device.");
        }

        if (!adapter.isEnabled()){
            throw new Error("Device BLE is disabled.");
        }

        return true;
    }

    @SuppressLint("MissingPermission")
    @PluginMethod
    public void searchESPDevices(String devicePrefix, ESPConstants.TransportType transport, ESPConstants.SecurityType security, ScanListener listener) {
        this.assertBluetoothAdapter();

        if (ActivityCompat.checkSelfPermission(this.bridge.getContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Error permissionError = new Error("Not able to start scan as Location permission is not granted.");
            errorLog(permissionError);
            listener.errorOccurred(permissionError);
            return;
        }

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
                Error bleScanFailedError = new Error("BLE Scan failed: " + e.getMessage());
                errorLog(bleScanFailedError);
                listener.errorOccurred(bleScanFailedError);
            }
        };

        this.getESPProvisionManager().searchBleEspDevices(devicePrefix, bleScanListener);
    }

    public void connect(String deviceName, String proofOfPossession, ConnectListener listener){

        DiscoveredBluetoothDevice bleDevice = this.devices.get(deviceName);
        if(bleDevice == null) {
            listener.deviceNotFound();
            return;
        }

        Runnable connectionTimeoutTask = new Runnable() {

            @Override
            public void run() {
                errorLog(new Error("Timed out trying to connect to device: " + deviceName));
                listener.connectionTimedOut();
                // EventBus.getDefault().unregister(connectionHandler); // TODO: cleanup
            }

        };

        EventCallback connectionHandler = new EventCallback(){

            @Subscribe(threadMode = ThreadMode.MAIN)
            public void onEvent(DeviceConnectionEvent event) {

                debugLog("Connection handler callback: " + event.getEventType());

                handler.removeCallbacks(connectionTimeoutTask); // Cancels connection timeout task
                EventBus.getDefault().unregister(this);

                switch (event.getEventType()) {

                    case ESPConstants.EVENT_DEVICE_CONNECTED:
                        debugLog("Device Connected Event Received");
                        provisionManager.getEspDevice().setProofOfPossession(proofOfPossession);
                        listener.connected(provisionManager.getEspDevice());
                        break;

                    case ESPConstants.EVENT_DEVICE_DISCONNECTED:
                        listener.disconnected();
                        break;

                    case ESPConstants.EVENT_DEVICE_CONNECTION_FAILED:
                        listener.connectionFailed();
                        break;
                }
            }
        };

        EventBus.getDefault().register(connectionHandler);

        ESPDevice espDevice = this.getESPProvisionManager().createESPDevice(transport, security);

        espDevice.connectBLEDevice(bleDevice.getBluetoothDevice(), bleDevice.getServiceUUID());

        this.handler.postDelayed(connectionTimeoutTask, this.DEVICE_CONNECT_TIMEOUT );
    }

    private ESPDevice getESPDevice(String deviceName, UsesESPDevice listener){
        DiscoveredBluetoothDevice bleDevice = this.devices.get(deviceName);
        if(bleDevice == null) {
            listener.deviceNotFound(deviceName);
            return null;
        }

        ESPDevice espDevice = this.getESPProvisionManager().getEspDevice();
        if(espDevice == null) {
            listener.deviceNotFound(deviceName);
            return null;
        }

        if( espDevice.getDeviceName() != bleDevice.getName() ){
            debugLog(String.format("Device mismatch. %s != %s", espDevice.getDeviceName(), bleDevice.getName()));
            listener.deviceNotFound(deviceName);
            return null;
        }

        return espDevice;
    }

    public void scanWifiList(String deviceName, ScanWiFiListener listener) {
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
        ESPDevice espDevice = this.getESPDevice(deviceName, listener);
        if(espDevice == null) return;

        espDevice.provision(ssid, passPhrase, new ProvisionListener() {

            @Override
            public void createSessionFailed(Exception e) {
                Error createSessionError = new Error("Provisioning Failed. Couldn't create a session.", e);
                errorLog(createSessionError);
                listener.provisioningFailed(createSessionError);
            }

            @Override
            public void wifiConfigSent() {
                debugLog("WiFi config sent");
            }

            @Override
            public void wifiConfigFailed(Exception e) {
                Error wifiConfigFailedError = new Error("Provisioning Failed. Failed to send WiFi config.", e);
                errorLog(wifiConfigFailedError);
                listener.provisioningFailed(wifiConfigFailedError);
            }

            @Override
            public void wifiConfigApplied() {
                debugLog("WiFi config applied");
            }

            @Override
            public void wifiConfigApplyFailed(Exception e) {
                Error wifiConfigApplyError = new Error("Provisioning Failed. Failed to apply WiFi config.", e);
                errorLog(wifiConfigApplyError);
                listener.provisioningFailed(wifiConfigApplyError);
            }

            @Override
            public void provisioningFailedFromDevice(final ESPConstants.ProvisionFailureReason failureReason) {
                switch (failureReason) {
                    case AUTH_FAILED:
                        Error authFailedError = new Error("Provisioning Failed. Authentication failed.");
                        errorLog(authFailedError);
                        listener.provisioningFailed(authFailedError);
                        break;
                    case DEVICE_DISCONNECTED:
                        Error deviceDisconnectedError = new Error("Provisioning Failed. Device Disconnected.");
                        errorLog(deviceDisconnectedError);
                        listener.provisioningFailed(deviceDisconnectedError);
                        break;
                    case NETWORK_NOT_FOUND:
                        Error networkNotFoundError = new Error(String.format("Provisioning Failed. Network[%s] Not Found.", ssid));
                        errorLog(networkNotFoundError);
                        listener.provisioningFailed(networkNotFoundError);
                        break;
                    case UNKNOWN:
                    default:
                        Error unknownError = new Error("Provisioning Failed. Unknown Error.");
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
                Error sendCustomDataStringError = new Error("Error sending custom data string: " + e.getMessage());
                errorLog(sendCustomDataStringError);
                listener.failedToSendCustomDataString(sendCustomDataStringError);
            }

        });
    }

    public void disconnect(String deviceName, DisconnectListener listener) {
        ESPDevice espDevice = this.getESPDevice(deviceName, listener);
        if(espDevice == null) return;

        espDevice.disconnectDevice();

        this.devices = new HashMap<String, DiscoveredBluetoothDevice>();

        listener.deviceDisconnected();
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
