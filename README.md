# @general-galactic/capacitor-esp-idf-provisioning

A capacitor plugin that wraps the Espressif IDF Provisioning libraires for iOS and Android.

## Install

```bash
npm install @general-galactic/capacitor-esp-idf-provisioning
npx cap sync
```
## Troublshooting

**Getting error: `ESPProvisioning is not implemented on [iOS|Android]`**
You need to run `npx cap sync`

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
* [`disableLogging()`](#disablelogging)
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

Perform a scan for devices with the given devicePrefix

| Param         | Type                                                                                                                                          |
| ------------- | --------------------------------------------------------------------------------------------------------------------------------------------- |
| **`options`** | <code>{ devicePrefix: string; transport: <a href="#esptransport">ESPTransport</a>; security: <a href="#espsecurity">ESPSecurity</a>; }</code> |

**Returns:** <code>Promise&lt;{ devices?: ESPDevice[]; }&gt;</code>

--------------------


### connect(...)

```typescript
connect(options: { deviceName: string; proofOfPossession: string; }) => Promise<{ connected: boolean; }>
```

Connect to the device with the given name using the given proofOfPossession

| Param         | Type                                                            |
| ------------- | --------------------------------------------------------------- |
| **`options`** | <code>{ deviceName: string; proofOfPossession: string; }</code> |

**Returns:** <code>Promise&lt;{ connected: boolean; }&gt;</code>

--------------------


### scanWifiList(...)

```typescript
scanWifiList(options: { deviceName: string; }) => Promise<{ networks?: ESPNetwork[]; }>
```

Request a list of available WiFi networks from the device with the given name

| Param         | Type                                 |
| ------------- | ------------------------------------ |
| **`options`** | <code>{ deviceName: string; }</code> |

**Returns:** <code>Promise&lt;{ networks?: ESPNetwork[]; }&gt;</code>

--------------------


### provision(...)

```typescript
provision(options: { deviceName: string; ssid: string; passPhrase: string; }) => Promise<{ success: boolean; }>
```

Provision the device onto WiFi using the given ssid and passPhrase

| Param         | Type                                                                   |
| ------------- | ---------------------------------------------------------------------- |
| **`options`** | <code>{ deviceName: string; ssid: string; passPhrase: string; }</code> |

**Returns:** <code>Promise&lt;{ success: boolean; }&gt;</code>

--------------------


### sendCustomDataString(...)

```typescript
sendCustomDataString(options: { deviceName: string; path: string; dataString: string; }) => Promise<{ success: boolean; returnString?: string; }>
```

Send a custom string to the device with the given name

| Param         | Type                                                                   |
| ------------- | ---------------------------------------------------------------------- |
| **`options`** | <code>{ deviceName: string; path: string; dataString: string; }</code> |

**Returns:** <code>Promise&lt;{ success: boolean; returnString?: string; }&gt;</code>

--------------------


### disconnect(...)

```typescript
disconnect(options: { deviceName: string; }) => Promise<void>
```

Disconnect from the device

| Param         | Type                                 |
| ------------- | ------------------------------------ |
| **`options`** | <code>{ deviceName: string; }</code> |

--------------------


### openLocationSettings()

```typescript
openLocationSettings() => Promise<{ value: boolean; }>
```

Open the user's location settings for your app. iOS only.

**Returns:** <code>Promise&lt;{ value: boolean; }&gt;</code>

--------------------


### openBluetoothSettings()

```typescript
openBluetoothSettings() => Promise<{ value: boolean; }>
```

Open the user's bluetooth settings for your app. iOS only.

**Returns:** <code>Promise&lt;{ value: boolean; }&gt;</code>

--------------------


### openAppSettings()

```typescript
openAppSettings() => Promise<{ value: boolean; }>
```

Open the settings for your app

**Returns:** <code>Promise&lt;{ value: boolean; }&gt;</code>

--------------------


### enableLogging()

```typescript
enableLogging() => Promise<void>
```

Enable detailed logging. iOS only.

--------------------


### disableLogging()

```typescript
disableLogging() => Promise<void>
```

Disable detailed logging. iOS only.

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
