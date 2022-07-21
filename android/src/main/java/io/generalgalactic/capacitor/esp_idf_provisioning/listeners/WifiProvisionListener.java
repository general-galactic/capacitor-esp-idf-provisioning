package io.generalgalactic.capacitor.esp_idf_provisioning.listeners;

public interface WifiProvisionListener extends UsesESPDevice {

    public void provisioningSuccess();

    public void provisioningFailed(Error error);

}
