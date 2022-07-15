package io.generalgalactic.capacitor.esp_idf_provisioning;

import com.espressif.provisioning.ESPDevice;

public interface ConnectListener {

    public void connected(ESPDevice device);

    public void deviceNotFound();

    public void connectionTimedOut();

    public void connectionFailed();

}
