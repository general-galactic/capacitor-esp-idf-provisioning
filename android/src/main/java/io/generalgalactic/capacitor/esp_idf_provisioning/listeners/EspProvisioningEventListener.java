package io.generalgalactic.capacitor.esp_idf_provisioning.listeners;

public interface EspProvisioningEventListener {

    public void deviceDisconnectedUnexpectedly(String deviceName);

    public void bluetoothStateChange(int state);

}
