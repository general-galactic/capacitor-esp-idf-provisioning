import type { PermissionState } from '@capacitor/core';
import type { Plugin } from '@capacitor/core/types/definitions';

export enum ESPTransport {
  ble = 'ble',
  softap = 'softap'
}

export enum ESPSecurity {
  unsecure = 'unsecure',
  secure = 'secure'
}

export type ESPDeviceAdvertisingData = {
  localName?: string
  isConnectable?: number
  manufacturerData?: any
  serviceUUIDs?: string[]
}

export type ESPDevice = {
  name: string // Your devices must have a unique name
  advertisementData?: ESPDeviceAdvertisingData
}

export type ESPNetwork = {
  ssid: string
  rssi: number
  auth: 'open' | 'wep' | 'wpapsk' | 'wpawpa2psk' | 'wpa2enterprise' | 'unknown'
}

// https://capacitorjs.com/docs/plugins/web#permission-status-definitions
export interface PermissionStatus {
  ble: PermissionState;
  location: PermissionState;
}

export interface EspProvisioningStatus {
  ble: {
    supported: boolean,
    allowed: boolean,
    poweredOn: boolean
  },
  location: {
    allowed: boolean
  }
}

export interface EspProvisioningPlugin extends Plugin {

  /**
   * Check the status of system permissions:
   * - **ble** - Bluetooth access
   * - **location** - Location access, android only
   */
  checkPermissions(): Promise<PermissionStatus>;

  /**
   * Have the system prompt the user for access to the proper permissions - Android only.
   */
  requestPermissions(): Promise<PermissionStatus>;

  /**
   * See if the bluetooth adapter is up and running
   */
  checkStatus(): Promise<EspProvisioningStatus>;

  /**
   * Perform a BLE scan to find devices that are connection with the given devicePrefix. The transport and security
   * parameters map directly to ESPProvision's own values.
   *
   * @param options {{ devicePrefix: string, transport: ESPTransport, security: ESPSecurity }}
   */
  searchESPDevices(options: { devicePrefix: string, transport: ESPTransport, security: ESPSecurity }): Promise<{ devices?: ESPDevice[] }>;

  /**
   * Connect to the device with the given name using the given proofOfPossession.
   *
   * @param options {{ deviceName: string, proofOfPossession: string }}
   */
  connect(options: { deviceName: string, proofOfPossession: string }): Promise<{ connected: boolean }>;

  /**
   * Request a list of available WiFi networks from the device with the given name.
   *
   * @param options {{ deviceName: string }}
   */
  scanWifiList(options: { deviceName: string }): Promise<{ networks?: ESPNetwork[] }>;

  /**
   * Provision the device onto WiFi using the given ssid and passPhrase.
   *
   * @param options {{ deviceName: string, ssid: string, passPhrase: string }}
   */
  provision(options: { deviceName: string, ssid: string, passPhrase?: string }): Promise<{ success: boolean }>;

  /**
   * Send a custom string to the device with the given name. This is usefull if you need to share other data with
   * your device during provisioning. NOTE: Android will truncate returned strings to around 512 bytes. If you need
   * to send more than 512 bytes back on a read you'll need to implement a mechanism to do so.
   *
   * @param options {{ deviceName: string, path: string, dataString: string }}
   * @returns {{ success: boolean, returnString: string }}
   */
  sendCustomDataString(options: { deviceName: string, path: string, dataString: string }): Promise<{ success: boolean, returnString?: string }>;

  /**
   * Disconnect from the device.
   *
   * @param options {{ deviceName: string }}
   */
  disconnect(options: { deviceName: string }): Promise<void>;

  /**
   * Open the user's location settings for your app. Android only.
   */
  openLocationSettings(): Promise<{ value: boolean }>;

  /**
   * Open the user's bluetooth settings for your app. Android only.
   */
  openBluetoothSettings(): Promise<{ value: boolean }>;

  /**
   * Open the OS settings for your app.
   */
  openAppSettings(): Promise<{ value: boolean }>;

  /**
   * Enable extra logging - useful for troubleshooting. Best on iOS because the iOS ESPProvision
   * library offers much more verbose logging when enabled.
   */
  enableLogging(): Promise<void>;

  /**
   * Disable detailed logging.
   */
  disableLogging(): Promise<void>;

}
