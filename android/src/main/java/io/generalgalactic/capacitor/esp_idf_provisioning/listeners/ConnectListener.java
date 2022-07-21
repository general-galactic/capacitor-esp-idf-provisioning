package io.generalgalactic.capacitor.esp_idf_provisioning.listeners;

import com.espressif.provisioning.ESPDevice;

public interface ConnectListener extends UsesESPDevice {

    public void connected(ESPDevice device);

    public void connectionTimedOut();

    public void connectionFailed();

}
