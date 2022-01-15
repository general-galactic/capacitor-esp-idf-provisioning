import type { PermissionState } from '@capacitor/core';

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

export interface EspProvisioningPlugin {

  checkPermissions(): Promise<PermissionStatus>;

  requestPermissions(): Promise<PermissionStatus>;

  /** Perform a scan for devices with the given devicePrefix */
  searchESPDevices(options: { devicePrefix: string, transport: ESPTransport, security: ESPSecurity }): Promise<{ devices?: ESPDevice[] }>;

  /** Connect to the device with the given name using the given proofOfPossession */
  connect(options: { deviceName: string, proofOfPossession: string }): Promise<{ connected: boolean }>;

  /** Request a list of available WiFi networks from the device with the given name */
  scanWifiList(options: { deviceName: string }): Promise<{ networks?: ESPNetwork[] }>;

  /** Provision the device onto WiFi using the given ssid and passPhrase */
  provision(options: { deviceName: string, ssid: string, passPhrase: string }): Promise<{ success: boolean }>;

  /** Send a custom string to the device with the given name */
  sendCustomDataString(options: { deviceName: string, path: string, dataString: string }): Promise<{ success: boolean, returnString?: string }>;

  /** Disconnect from the device */
  disconnect(options: { deviceName: string }): Promise<void>;

  /** Open the user's location settings for your app. iOS only. */
  openLocationSettings(): Promise<{ value: boolean }>;

  /** Open the user's bluetooth settings for your app. iOS only. */
  openBluetoothSettings(): Promise<{ value: boolean }>;

  /** Open the settings for your app */
  openAppSettings(): Promise<{ value: boolean }>;

  /** Enable detailed logging. iOS only. */
  enableLogging(): Promise<void>;

  /** Disable detailed logging. iOS only. */
  disableLogging(): Promise<void>;

}
