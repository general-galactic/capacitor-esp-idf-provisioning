package io.generalgalactic.capacitor.esp_idf_provisioning.listeners;

public interface DisconnectListener extends UsesESPDevice {

    public void deviceDisconnected();

}
