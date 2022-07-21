package io.generalgalactic.capacitor.esp_idf_provisioning.listeners;

public interface UsesBluetooth {

    public void bleNotSupported();

    public void blePermissionNotGranted();

    public void bleNotPoweredOn();

}
