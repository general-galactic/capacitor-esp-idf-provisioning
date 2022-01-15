/* eslint-disable @typescript-eslint/no-unused-vars */
import { WebPlugin } from '@capacitor/core';

import type { ESPDevice, ESPNetwork, EspProvisioningPlugin, ESPSecurity, ESPTransport, PermissionStatus } from './definitions';

export class EspProvisioningWeb extends WebPlugin implements EspProvisioningPlugin {

  // TODO: offer a fake implementation to be used when testing in a browser
  
  checkPermissions(): Promise<PermissionStatus> {
    throw new Error('Method not implemented.');
  }

  requestPermissions(): Promise<PermissionStatus> {
    throw new Error('Method not implemented.');
  }

  searchESPDevices(_options: { devicePrefix: string; transport: ESPTransport; security: ESPSecurity; }): Promise<{ devices?: ESPDevice[] | undefined; }> {
    throw new Error('Method not implemented.');
  }

  connect(_options: { deviceName: string; proofOfPossession: string }): Promise<{ connected: boolean; }> {
    throw new Error('Method not implemented.');
  }

  scanWifiList(_options: { deviceName: string; }): Promise<{ networks?: ESPNetwork[] | undefined; }> {
    throw new Error('Method not implemented.');
  }

  provision(_options: { deviceName: string; ssid: string; passPhrase: string; }): Promise<{ success: boolean; }> {
    throw new Error('Method not implemented.');
  }

  sendCustomDataString(_options: { deviceName: string; path: string; dataString: string; }): Promise<{ success: boolean; returnString?: string | undefined; }> {
    throw new Error('Method not implemented.');
  }

  disconnect(_options: { deviceName: string; }): Promise<void> {
    throw new Error('Method not implemented.');
  }

  openLocationSettings(): Promise<{ value: boolean; }> {
    throw new Error('Method not implemented.');
  }

  openBluetoothSettings(): Promise<{ value: boolean; }> {
    throw new Error('Method not implemented.');
  }

  openAppSettings(): Promise<{ value: boolean; }> {
    throw new Error('Method not implemented.');
  }

  enableLogging(): Promise<void> {
    throw new Error('Method not implemented.');
  }

  disableLogging(): Promise<void> {
    throw new Error('Method not implemented.');
  }
  
}
