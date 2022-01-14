# @general-galactic/capacitor-esp-idf-provisioning

A capacitor plugin that wraps the Espressif IDF Provisioning libraires for iOS and Android.

## Install

```bash
npm install @general-galactic/capacitor-esp-idf-provisioning
npx cap sync
```

## API

<docgen-index>

* [`checkPermissions()`](#checkpermissions)
* [`requestPermissions()`](#requestpermissions)
* [`searchESPDevices(...)`](#searchespdevices)
* [`connect(...)`](#connect)
* [`scanWifiList(...)`](#scanwifilist)
* [`provision(...)`](#provision)
* [`sendCustomDataString(...)`](#sendcustomdatastring)
* [`disconnect(...)`](#disconnect)
* [`openLocationSettings()`](#openlocationsettings)
* [`openBluetoothSettings()`](#openbluetoothsettings)
* [`openAppSettings()`](#openappsettings)
* [`enableLogging()`](#enablelogging)
* [Interfaces](#interfaces)
* [Type Aliases](#type-aliases)
* [Enums](#enums)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

### checkPermissions()

```typescript
checkPermissions() => Promise<PermissionStatus>
```

**Returns:** <code>Promise&lt;<a href="#permissionstatus">PermissionStatus</a>&gt;</code>

--------------------


### requestPermissions()

```typescript
requestPermissions() => Promise<PermissionStatus>
```

**Returns:** <code>Promise&lt;<a href="#permissionstatus">PermissionStatus</a>&gt;</code>

--------------------


### searchESPDevices(...)

```typescript
searchESPDevices(options: { devicePrefix: string; transport: ESPTransport; security: ESPSecurity; }) => Promise<{ devices?: ESPDevice[]; }>
```

| Param         | Type                                                                                                                                          |
| ------------- | --------------------------------------------------------------------------------------------------------------------------------------------- |
| **`options`** | <code>{ devicePrefix: string; transport: <a href="#esptransport">ESPTransport</a>; security: <a href="#espsecurity">ESPSecurity</a>; }</code> |

**Returns:** <code>Promise&lt;{ devices?: ESPDevice[]; }&gt;</code>

--------------------


### connect(...)

```typescript
connect(options: { id: string; }) => Promise<{ connected: boolean; }>
```

| Param         | Type                         |
| ------------- | ---------------------------- |
| **`options`** | <code>{ id: string; }</code> |

**Returns:** <code>Promise&lt;{ connected: boolean; }&gt;</code>

--------------------


### scanWifiList(...)

```typescript
scanWifiList(options: { id: string; }) => Promise<{ networks?: ESPNetwork[]; }>
```

| Param         | Type                         |
| ------------- | ---------------------------- |
| **`options`** | <code>{ id: string; }</code> |

**Returns:** <code>Promise&lt;{ networks?: ESPNetwork[]; }&gt;</code>

--------------------


### provision(...)

```typescript
provision(options: { id: string; ssid: string; passPhrase: string; }) => Promise<{ success: boolean; }>
```

| Param         | Type                                                           |
| ------------- | -------------------------------------------------------------- |
| **`options`** | <code>{ id: string; ssid: string; passPhrase: string; }</code> |

**Returns:** <code>Promise&lt;{ success: boolean; }&gt;</code>

--------------------


### sendCustomDataString(...)

```typescript
sendCustomDataString(options: { id: string; path: string; data: string; }) => Promise<{ success: boolean; data?: string; }>
```

| Param         | Type                                                     |
| ------------- | -------------------------------------------------------- |
| **`options`** | <code>{ id: string; path: string; data: string; }</code> |

**Returns:** <code>Promise&lt;{ success: boolean; data?: string; }&gt;</code>

--------------------


### disconnect(...)

```typescript
disconnect(options: { id: string; }) => Promise<void>
```

| Param         | Type                         |
| ------------- | ---------------------------- |
| **`options`** | <code>{ id: string; }</code> |

--------------------


### openLocationSettings()

```typescript
openLocationSettings() => Promise<{ value: boolean; }>
```

**Returns:** <code>Promise&lt;{ value: boolean; }&gt;</code>

--------------------


### openBluetoothSettings()

```typescript
openBluetoothSettings() => Promise<{ value: boolean; }>
```

**Returns:** <code>Promise&lt;{ value: boolean; }&gt;</code>

--------------------


### openAppSettings()

```typescript
openAppSettings() => Promise<{ value: boolean; }>
```

**Returns:** <code>Promise&lt;{ value: boolean; }&gt;</code>

--------------------


### enableLogging()

```typescript
enableLogging() => Promise<void>
```

--------------------


### Interfaces


#### PermissionStatus

| Prop           | Type                                                        |
| -------------- | ----------------------------------------------------------- |
| **`ble`**      | <code><a href="#permissionstate">PermissionState</a></code> |
| **`location`** | <code><a href="#permissionstate">PermissionState</a></code> |


### Type Aliases


#### PermissionState

<code>'prompt' | 'prompt-with-rationale' | 'granted' | 'denied'</code>


#### ESPDevice

<code>{ name: string // Your devices must have a unique name advertisementData?: <a href="#espdeviceadvertisingdata">ESPDeviceAdvertisingData</a> }</code>


#### ESPDeviceAdvertisingData

<code>{ localName?: string isConnectable?: number manufacturerData?: any serviceUUIDs?: string[] }</code>


#### ESPNetwork

<code>{ ssid: string rssi: number auth: 'open' | 'wep' | 'wpapsk' | 'wpawpa2psk' | 'wpa2enterprise' | 'unknown' }</code>


### Enums


#### ESPTransport

| Members      | Value                 |
| ------------ | --------------------- |
| **`ble`**    | <code>'ble'</code>    |
| **`softap`** | <code>'softap'</code> |


#### ESPSecurity

| Members        | Value                   |
| -------------- | ----------------------- |
| **`unsecure`** | <code>'unsecure'</code> |
| **`secure`**   | <code>'secure'</code>   |

</docgen-api>
