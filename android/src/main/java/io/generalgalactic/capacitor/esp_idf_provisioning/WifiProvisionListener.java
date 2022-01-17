package io.generalgalactic.capacitor.esp_idf_provisioning;

public interface WifiProvisionListener extends UsesESPDevice {

    public void provisioningSuccess();

    public void provisioningFailed(Error error);

}
