package io.generalgalactic.capacitor.esp_idf_provisioning.listeners;

public interface UsesESPDevice extends UsesBluetooth {

    public void deviceNotFound(String deviceName);

}
