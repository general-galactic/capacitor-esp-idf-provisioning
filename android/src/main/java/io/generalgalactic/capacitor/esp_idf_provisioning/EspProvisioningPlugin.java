package io.generalgalactic.capacitor.esp_idf_provisioning;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.util.Log;

import com.espressif.provisioning.ESPConstants;
import com.espressif.provisioning.ESPDevice;
import com.espressif.provisioning.WiFiAccessPoint;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import java.util.List;

import io.generalgalactic.capacitor.esp_idf_provisioning.listeners.ConnectListener;
import io.generalgalactic.capacitor.esp_idf_provisioning.listeners.DisconnectListener;
import io.generalgalactic.capacitor.esp_idf_provisioning.listeners.EspProvisioningEventListener;
import io.generalgalactic.capacitor.esp_idf_provisioning.listeners.ScanListener;
import io.generalgalactic.capacitor.esp_idf_provisioning.listeners.ScanWiFiListener;
import io.generalgalactic.capacitor.esp_idf_provisioning.listeners.SendCustomDataStringListener;
import io.generalgalactic.capacitor.esp_idf_provisioning.listeners.WifiProvisionListener;

class EventCallback {

}

@CapacitorPlugin(
        name = "EspProvisioning",
        permissions = {
                @Permission(
                        alias = "ble",
                        strings = {
                                Manifest.permission.BLUETOOTH,
                                Manifest.permission.BLUETOOTH_ADMIN,
                                Manifest.permission.BLUETOOTH_SCAN
                        }
                ),
                @Permission(
                        alias = "location",
                        strings = {
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                Manifest.permission.ACCESS_FINE_LOCATION
                        }
                )
        }
)
public class EspProvisioningPlugin extends Plugin implements EspProvisioningEventListener {

    private EspProvisioningBLE implementation;

    @Override
    public void load() {
        implementation = new EspProvisioningBLE(this.getBridge(), this);
    }

    @PluginMethod
    public void checkPermissions(PluginCall call) {
        call.resolve(this.getPermissions());
    }

    @PluginMethod
    public void requestPermissions(PluginCall call) {
        boolean needsRequest = false;
        if(this.implementation.hasBLEHardware() && !this.blePermissionGranted()){
            needsRequest = true;
        }

        if(!this.locationPermissionGranted()){
            needsRequest = true;
        }

        if(needsRequest){
            requestAllPermissions(call, "permissionsCallback");
        }else{
            this.permissionsCallback(call);
        }
    }

    @PermissionCallback()
    private void permissionsCallback(PluginCall call) {
        Log.d("capacitor-esp-provision", String.format("Requested ble permissions: hasBLEHardware=%b; blePermissionGranted=%b;", this.implementation.hasBLEHardware(), this.blePermissionGranted()));

        if(this.implementation.hasBLEHardware() && !this.blePermissionGranted()){
            call.reject("BLE is required");
            return;
        }

        if(!this.locationPermissionGranted()){
            call.reject("Location access is required to use BLE");
            return;
        }

        call.resolve(this.getPermissions());
    }

    private JSObject getPermissions(){
        JSObject ret = new JSObject();
        ret.put("location", this.getPermissionState("location").toString());
        ret.put("ble", this.getPermissionState("ble").toString());
        return ret;
    }

    private boolean blePermissionGranted(){
        return this.getPermissionState("ble") == PermissionState.GRANTED;
    }

    private boolean locationPermissionGranted(){
        return this.getPermissionState("location") == PermissionState.GRANTED;
    }

    @PluginMethod
    public void checkStatus(PluginCall call) {
        call.resolve(this.buildStatus());
    }

    private JSObject buildStatus(){
        JSObject location = new JSObject();
        location.put("allowed", this.locationPermissionGranted());

        Log.d("capacitor-esp-provision", String.format("Other permissions: %b, %b, %b", this.implementation.blePermissionsArGranted(), this.blePermissionGranted(), this.locationPermissionGranted()));

        JSObject ble = new JSObject();
        ble.put("supported", this.implementation.hasBLEHardware());
        ble.put("allowed", this.blePermissionGranted());
        ble.put("poweredOn", this.implementation.bleIsEnabled());

        JSObject ret = new JSObject();
        ret.put("ble", ble);
        ret.put("location", location);

        return ret;
    }

    @PluginMethod
    public void searchESPDevices(PluginCall call) {
        if (!this.implementation.assertBluetooth(new BluetoothRequiredCallHandler(call))) return;

        String devicePrefix = call.getString("devicePrefix");
        ESPConstants.TransportType transport = this.transportTypeFromString(call.getString("transport"));
        ESPConstants.SecurityType security = this.securityTypeFromString(call.getString("security"));

        this.implementation.searchESPDevices(devicePrefix, transport, security, new ScanListener() {

            @Override
            public void foundDevices(List<DiscoveredBluetoothDevice> devices) {
                JSArray devicesOutput = new JSArray();

                for (DiscoveredBluetoothDevice device : devices) {
                    JSObject deviceJson = new JSObject();
                    deviceJson.put("id", device.getName());
                    deviceJson.put("name", device.getName());
                    devicesOutput.put(deviceJson);
                }

                JSObject ret = new JSObject();
                ret.put("devices", devicesOutput);
                call.resolve(ret);
            }

            @Override
            public void errorOccurred(Error error) {
                call.reject(error.getMessage());
            }

            @Override
            public void blePermissionsIssue() {
                call.reject("Bluetooth (Nearby Devices) and Location permissions are required", "PERMISSIONS_ISSUE");
            }

        });
    }

    @PluginMethod
    public void connect(PluginCall call) {
        if (!this.implementation.assertBluetooth(new BluetoothRequiredCallHandler(call))) return;

        String deviceName = call.getString("deviceName");
        String proofOfPossession = call.getString("proofOfPossession");

        this.implementation.connect(deviceName, proofOfPossession, new ConnectListener() {

            @Override
            public void connected(ESPDevice device) {
                JSObject response = new JSObject();
                response.put("connected", true);
                call.resolve(response);
            }

            @Override
            public void deviceNotFound(String deviceName) {
                call.reject("Device not found: " + deviceName);
            }

            @Override
            public void connectionTimedOut() {
                call.reject("Connection timed out: " + deviceName);
            }

            @Override
            public void connectionFailed() {
                call.reject("Device connection failed: " + deviceName);
            }

        });
    }

    @PluginMethod
    public void scanWifiList(PluginCall call) {
        if (!this.implementation.assertBluetooth(new BluetoothRequiredCallHandler(call))) return;

        String deviceName = call.getString("deviceName");
        this.implementation.scanWifiList(deviceName, new ScanWiFiListener() {

            @Override
            public void foundWiFiNetworks(List<WiFiAccessPoint> networks) {
                JSArray networksResponse = new JSArray();
                for (WiFiAccessPoint accessPoint : networks) {
                    JSObject network = new JSObject();
                    network.put("ssid", accessPoint.getWifiName());
                    network.put("rssi", accessPoint.getRssi());
                    network.put("security", accessPoint.getSecurity());
                    networksResponse.put(network);
                }
                JSObject ret = new JSObject();
                ret.put("networks", networksResponse);
                call.resolve(ret);
            }

            @Override
            public void deviceNotFound(String deviceName) {
                call.reject("Device not found: " + deviceName);
            }

            @Override
            public void wiFiScanFailed(Exception error) {
                call.reject("WiFi scan failed: " + error.getMessage());
            }

        });
    }

    @PluginMethod
    public void provision(PluginCall call) {
        if (!this.implementation.assertBluetooth(new BluetoothRequiredCallHandler(call))) return;

        String deviceName = call.getString("deviceName");
        String ssid = call.getString("ssid");
        String passPhrase = call.getString("passPhrase");

        this.implementation.provision(deviceName, ssid, passPhrase, new WifiProvisionListener() {

            @Override
            public void provisioningSuccess() {
                JSObject response = new JSObject();
                response.put("success", true);
                call.resolve(response);
            }

            @Override
            public void provisioningFailed(Error error) {
                call.reject("WiFi provisioning failed: " + error.getMessage());
            }

            @Override
            public void deviceNotFound(String deviceName) {
                call.reject("Device not found: " + deviceName);
            }

        });
    }

    @PluginMethod
    public void sendCustomDataString(PluginCall call) {
        if (!this.implementation.assertBluetooth(new BluetoothRequiredCallHandler(call))) return;

        String deviceName = call.getString("deviceName");
        String path = call.getString("path");
        String dataString = call.getString("dataString");

        this.implementation.sendCustomDataString(deviceName, path, dataString, new SendCustomDataStringListener() {

            @Override
            public void sentCustomDataStringWithResponse(String returnString) {
                JSObject ret = new JSObject();
                ret.put("success", true);
                ret.put("returnString", returnString);
                call.resolve(ret);
            }

            @Override
            public void failedToSendCustomDataString(Error error) {
                call.reject(error.getMessage());
            }

            @Override
            public void deviceNotFound(String deviceName) {
                call.reject("Device not found: " + deviceName);
            }

        });
    }

    @PluginMethod
    public void disconnect(PluginCall call) {
        String deviceName = call.getString("deviceName");
        this.implementation.disconnect(deviceName, new DisconnectListener() {

            @Override
            public void deviceDisconnected() {
                call.resolve();
            }

            @Override
            public void deviceNotFound(String deviceName) {
                call.reject("Device not found: " + deviceName);
            }

        });
    }

    @PluginMethod
    public void openLocationSettings(PluginCall call) {
        Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
        this.bridge.getActivity().startActivity(intent);
        JSObject ret = new JSObject();
        ret.put("value", true);
        call.resolve(ret);
    }

    @PluginMethod
    public void openBluetoothSettings(PluginCall call) {
        Intent intent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
        this.bridge.getActivity().startActivity(intent);
        JSObject ret = new JSObject();
        ret.put("value", true);
        call.resolve(ret);
    }

    @PluginMethod
    public void openAppSettings(PluginCall call) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + this.bridge.getActivity().getPackageName()));
        this.bridge.getActivity().startActivity(intent);
        JSObject ret = new JSObject();
        ret.put("value", true);
        call.resolve(ret);
    }

    @PluginMethod
    public void enableLogging(PluginCall call){
        this.implementation.setLoggingEnabled(true);
        call.resolve();
    }

    @PluginMethod
    public void disableLogging(PluginCall call){
        this.implementation.setLoggingEnabled(false);
        call.resolve();
    }

    private ESPConstants.TransportType transportTypeFromString(String transportString){
        switch (transportString){
            case "ble":
                return ESPConstants.TransportType.TRANSPORT_BLE;
            case "softap":
                return ESPConstants.TransportType.TRANSPORT_SOFTAP;
            default:
                throw new Error("Unknown transport type: " + transportString);
        }
    }

    private ESPConstants.SecurityType securityTypeFromString(String securityString){
        switch (securityString){
            case "unsecure":
                return ESPConstants.SecurityType.SECURITY_0;
            case "secure":
                return ESPConstants.SecurityType.SECURITY_1;
            default:
                throw new Error("Unknown security type: " + securityString);
        }
    }

    @Override
    public void deviceDisconnectedUnexpectedly(String deviceName) {
        JSObject ret = new JSObject();
        ret.put("deviceName", deviceName);
        this.notifyListeners("deviceDisconnected", ret);
    }

    @Override
    public void bluetoothStateChange(int state) {
        this.notifyListeners("statusUpdate", this.buildStatus());
    }

}
