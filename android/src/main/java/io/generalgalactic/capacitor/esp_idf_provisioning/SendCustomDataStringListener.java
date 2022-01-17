package io.generalgalactic.capacitor.esp_idf_provisioning;

public interface SendCustomDataStringListener extends UsesESPDevice {

    public void sentCustomDataStringWithResponse(String returnString);

    public void failedToSendCustomDataString(Error error);

}
