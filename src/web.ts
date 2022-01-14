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

  connect(_options: { id: string; }): Promise<{ connected: boolean; }> {
    throw new Error('Method not implemented.');
  }

  scanWifiList(_options: { id: string; }): Promise<{ networks?: ESPNetwork[] | undefined; }> {
    throw new Error('Method not implemented.');
  }

  provision(_options: { id: string; ssid: string; passPhrase: string; }): Promise<{ success: boolean; }> {
    throw new Error('Method not implemented.');
  }

  sendCustomDataString(_options: { id: string; path: string; data: string; }): Promise<{ success: boolean; data?: string | undefined; }> {
    throw new Error('Method not implemented.');
  }

  disconnect(_options: { id: string; }): Promise<void> {
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

}
