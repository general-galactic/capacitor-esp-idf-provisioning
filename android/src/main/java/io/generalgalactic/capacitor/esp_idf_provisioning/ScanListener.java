package io.generalgalactic.capacitor.esp_idf_provisioning;

import java.util.List;

public interface ScanListener {

    public void foundDevices(List<DiscoveredBluetoothDevice> devices);

    public void errorOccurred(Error error);

}
