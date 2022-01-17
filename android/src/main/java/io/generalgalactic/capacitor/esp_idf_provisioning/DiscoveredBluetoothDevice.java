package io.generalgalactic.capacitor.esp_idf_provisioning;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanResult;

public class DiscoveredBluetoothDevice {

    private BluetoothDevice bluetoothDevice;
    private String serviceUUID;
    private Number rssi;

    public DiscoveredBluetoothDevice(BluetoothDevice bluetoothDevice, ScanResult scanResult) {
        this.bluetoothDevice = bluetoothDevice;
        this.rssi = scanResult.getRssi();

        if (scanResult.getScanRecord().getServiceUuids() != null && scanResult.getScanRecord().getServiceUuids().size() > 0) {
            this.serviceUUID = scanResult.getScanRecord().getServiceUuids().get(0).toString();
        }
    }

    public String getName() {
        return this.bluetoothDevice.getName();
    }

    public BluetoothDevice getBluetoothDevice() {
        return this.bluetoothDevice;
    }

    public Number getRssi() {
        return rssi;
    }

    public String getServiceUUID() {
        return serviceUUID;
    }


}