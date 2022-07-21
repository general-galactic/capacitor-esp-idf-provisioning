package io.generalgalactic.capacitor.esp_idf_provisioning;

import com.getcapacitor.PluginCall;

import io.generalgalactic.capacitor.esp_idf_provisioning.listeners.UsesBluetooth;

public class BluetoothRequiredCallHandler implements UsesBluetooth {

    private PluginCall call;

    public BluetoothRequiredCallHandler(PluginCall call) {
        this.call = call;
    }

    @Override
    public void bleNotPoweredOn() {
        this.call.reject("Bluetooth must be enabled");
    }

    @Override
    public void bleNotSupported() {
        this.call.reject("Bluetooth is required");
    }

    @Override
    public void blePermissionNotGranted() {
        this.call.reject("Bluetooth (Nearby Devices) and Location permissions are required");
    }
}
