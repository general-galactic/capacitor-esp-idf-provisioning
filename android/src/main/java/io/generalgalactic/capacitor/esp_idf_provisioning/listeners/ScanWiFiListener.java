package io.generalgalactic.capacitor.esp_idf_provisioning.listeners;

import com.espressif.provisioning.WiFiAccessPoint;

import java.util.List;

public interface ScanWiFiListener extends UsesESPDevice {

    public void foundWiFiNetworks( List<WiFiAccessPoint> networks );

    public void wiFiScanFailed(Exception error);

}
