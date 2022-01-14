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

  searchESPDevices(options: { devicePrefix: string, transport: ESPTransport, security: ESPSecurity }): Promise<{ devices?: ESPDevice[] }>;

  connect(options: { id: string }): Promise<{ connected: boolean }>;

  scanWifiList(options: { id: string }): Promise<{ networks?: ESPNetwork[] }>;

  provision(options: { id: string, ssid: string, passPhrase: string }): Promise<{ success: boolean }>;

  sendCustomDataString(options: { id: string, path: string, data: string }): Promise<{ success: boolean, data?: string }>;

  disconnect(options: { id: string }): Promise<void>;

  openLocationSettings(): Promise<{ value: boolean }>;

  openBluetoothSettings(): Promise<{ value: boolean }>;

  openAppSettings(): Promise<{ value: boolean }>;

  enableLogging(): Promise<void>;

}
