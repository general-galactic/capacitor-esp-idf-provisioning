package io.generalgalactic.capacitor.esp_idf_provisioning;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import androidx.core.content.ContextCompat;

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

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

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
                        alias = "BLUETOOTH_SCAN",
                        strings = {
                                Manifest.permission.BLUETOOTH_SCAN
                        }
                ),
                @Permission(
                        alias = "BLUETOOTH_CONNECT",
                        strings = {
                                Manifest.permission.BLUETOOTH_CONNECT
                        }
                ),
                @Permission(
                        alias = "BLUETOOTH",
                        strings = {
                                Manifest.permission.BLUETOOTH
                        }
                ),
                @Permission(
                        alias = "BLUETOOTH_ADMIN",
                        strings = {
                                Manifest.permission.BLUETOOTH_ADMIN
                        }
                ),
                @Permission(
                        alias = "ACCESS_COARSE_LOCATION",
                        strings = {
                                Manifest.permission.ACCESS_COARSE_LOCATION
                        }
                ),
                @Permission(
                        alias = "ACCESS_FINE_LOCATION",
                        strings = {
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

    public String[] blePermissionAliases(){
        if (Build.VERSION.SDK_INT >= 31) {
            return new String[] { "BLUETOOTH_SCAN", "BLUETOOTH_CONNECT" };
        } else {
            return new String[] { "BLUETOOTH", "BLUETOOTH_ADMIN" };
        }
    }

    public String[] locationPermissionAliases(){
        if (Build.VERSION.SDK_INT >= 31) {
            return new String[] { }; // "ACCESS_FINE_LOCATION"
        } else {
            return new String[] { "ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION" };
        }
    }

    private String[] allPermissionAliases(){
        return Stream.concat(Arrays.stream(this.blePermissionAliases()), Arrays.stream(this.locationPermissionAliases())).toArray(String[]::new);
    }

    public boolean blePermissionsGranted(){
        boolean allPermitted = true;
        for (String alias: this.blePermissionAliases()) {
            PermissionState state = this.getPermissionState(alias);
            Log.d("capacitor-esp-provision", String.format("checking %s -> %s", alias, state.toString()));
            if (state != PermissionState.GRANTED) {
                allPermitted = false;
                Log.d("capacitor-esp-provision", String.format("Permission alias '%s' not permitted", alias));
            }
        }
        return allPermitted;
    }

    public boolean locationPermissionsGranted(){
        boolean allPermitted = true;
        for (String alias: this.locationPermissionAliases()) {
            PermissionState state = this.getPermissionState(alias);
            Log.d("capacitor-esp-provision", String.format("checking %s -> %s", alias, state.toString()));
            if (state != PermissionState.GRANTED) {
                allPermitted = false;
                Log.d("capacitor-esp-provision", String.format("Permission alias '%s' not permitted", alias));
            }
        }
        return allPermitted;
    }

    @PluginMethod
    public void checkPermissions(PluginCall call) {
        call.resolve(this.getPermissions());
    }

    @PluginMethod
    public void requestPermissions(PluginCall call) {
        boolean needsRequest = false;

        if(this.implementation.hasBLEHardware() && !this.blePermissionsGranted()){
            needsRequest = true;
        }

        if(!this.locationPermissionsGranted()){
            needsRequest = true;
        }

        if(needsRequest){
            String[] aliases = this.allPermissionAliases();
            Log.d("capacitor-esp-provision", String.format("Requesting permission aliases: %s", String.join(", ", aliases)));
            requestPermissionForAliases(aliases, call, "permissionsCallback");
        }else{
            this.permissionsCallback(call);
        }

    }

    @PermissionCallback()
    private void permissionsCallback(PluginCall call) {
        String[] aliases = this.allPermissionAliases();
        Log.d("capacitor-esp-provision", String.format("Requested ble permissions [%s]: hasBLEHardware=%b; blePermissionGranted=%b;", String.join(", ", aliases), this.implementation.hasBLEHardware(), this.blePermissionsGranted()));

        if(this.implementation.hasBLEHardware() && !this.blePermissionsGranted()){
            call.reject(String.format("BLE is required [hasBLEHardware=%b; blePermissionsGranted=%b]", this.implementation.hasBLEHardware(), this.blePermissionsGranted()));
            return;
        }

        if(!this.locationPermissionsGranted()){
            call.reject("Location access is required to use BLE");
            return;
        }

        call.resolve(this.getPermissions());
    }

    // TODO: this function seems old - kill it with fire
    private JSObject getPermissions(){
        PermissionState blePermission = this.blePermissionsGranted() ? PermissionState.GRANTED : PermissionState.DENIED;
        PermissionState locationPermission = this.locationPermissionsGranted() ? PermissionState.GRANTED : PermissionState.DENIED;

        JSObject ret = new JSObject();
        ret.put("location", blePermission.toString());
        ret.put("ble", locationPermission.toString());
        return ret;
    }

    @PluginMethod
    public void checkStatus(PluginCall call) {
        call.resolve(this.buildStatus());
    }

    private JSObject buildStatus(){
        JSObject ret = new JSObject();

        JSObject ble = new JSObject();
        ble.put("supported", this.implementation.hasBLEHardware());
        ble.put("allowed", this.blePermissionsGranted());
        ble.put("poweredOn", this.implementation.bleIsEnabled());
        ret.put("ble", ble);

        JSObject location = new JSObject();
        location.put("allowed", this.locationPermissionsGranted());
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

            @Override
            public void initSessionFailed(Exception e) {
                call.reject("Failed to initialise session with the device. [sessionInitError] " + e.getMessage()); // sessionInitError matches the error I receive on the iOS side. Just reusing it here for consistency.
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
