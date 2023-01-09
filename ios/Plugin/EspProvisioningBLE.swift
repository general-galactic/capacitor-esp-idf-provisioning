import Capacitor
import ESPProvision
import CoreBluetooth
import Foundation

enum ESPProvisioningError: Error {
    case missingRequiredField(_ missingFieldName: String)
    case libraryError(_ errorMessage: String, errorCode: Int? )
    case deviceNotFound(_ deviceName: String)
    case failedToConnect(_ deviceName: String, _ error: Error?)
    case disconnected(_ deviceName: String)
    case unableToConvertStringToData
    case unableToConvertDataToString
}

extension ESPProvisioningError: LocalizedError {
    public var message: String {
        switch self {
        case .missingRequiredField(let missingFieldName):
            return NSLocalizedString("\(missingFieldName) is required", comment: "Missing required field")
        case .libraryError(let errorMessage, let errorCode):
            return NSLocalizedString("\(errorMessage) [\(errorCode ?? 0)]", comment: "ESPProvisioning error")
        case .deviceNotFound(let deviceName):
            return NSLocalizedString("Device not found: \(deviceName)", comment: "Device not found error")
        case .failedToConnect(let deviceName, let error):
            return NSLocalizedString("Failed to connect to device: \(deviceName) \(String(describing: error))", comment: "Failed to connect to device")
        case .disconnected(let deviceName):
            return NSLocalizedString("Device disconnected: \(deviceName)", comment: "Device disconnected error")
        case .unableToConvertStringToData:
            return NSLocalizedString("Error converting custom data string to Data", comment: "Unable to convert string to data error")
        case .unableToConvertDataToString:
            return NSLocalizedString("Error converting response Data to string ", comment: "Unable to convert data to string error")
        }
    }
}


class ConnectionDelegate: ESPDeviceConnectionDelegate {

    private let proofOfPossesion: String
    
    init(proofOfPossesion: String) {
        self.proofOfPossesion = proofOfPossesion
    }
    
    func getProofOfPossesion(forDevice: ESPDevice, completionHandler: @escaping (String) -> Void) {
        completionHandler(self.proofOfPossesion)
    }

}

public enum CapacitorPermissionStatus : String {
    case granted = "granted"
    case denied = "denied"
    case prompt = "prompt"
}

public class EspProvisioningPermissionsStatus {
    
    let ble: CapacitorPermissionStatus
    let location: CapacitorPermissionStatus = CapacitorPermissionStatus.granted // Android requires location permissions, we just lie on iOS to conform
    
    init(ble: CapacitorPermissionStatus) {
        self.ble = ble
    }
    
    public func toDict() -> [String:String] {
        [
            "ble": self.ble.rawValue,
            "location": self.location.rawValue
        ]
    }
    
}

public class EspProvisioningBluetoothStatus {
    
    let supported: Bool
    let allowed: Bool
    let poweredOn: Bool
    
    init(supported: Bool, allowed: Bool, poweredOn: Bool) {
        self.supported = supported
        self.allowed = allowed
        self.poweredOn = poweredOn
    }

    public func toDict() -> [String: Bool] {
        return [
            "supported": self.supported,
            "allowed": self.allowed,
            "poweredOn": self.poweredOn
        ]
    }
    
}

public class EspProvisioningStatus {
    
    let ble: EspProvisioningBluetoothStatus
    
    init(ble: EspProvisioningBluetoothStatus) {
        self.ble = ble
    }

    public func toDict() -> [String: Any] {
        return [
            "ble": self.ble.toDict(),
            "location": [
                "allowed": true // adding this to have it match the android signature
            ]
        ]
    }
    
}

public class EspProvisioningBLE: NSObject, ESPBLEDelegate, CBCentralManagerDelegate {
 
    private let plugin: CAPPlugin
    private var deviceMap = [String: ESPDevice]()
    private var loggingEnabled = false
    
    private var connectedDevice: ESPDevice?

    private lazy var centralManager: CBCentralManager = {
        return CBCentralManager(delegate: self, queue: nil, options: [CBCentralManagerOptionShowPowerAlertKey: false])
    }()

    private var centralManagerState: CBManagerState {
        get {
            return self.centralManager.state
        }
    }
    
    @available(iOS 13.0, *)
    private var centralManagerAuthorizationState: CBManagerAuthorization {
        get {
            return self.centralManager.authorization
        }
    }
    
    init(_ plugin: CAPPlugin){
        self.plugin = plugin
    }
    
    public func centralManagerDidUpdateState(_ central: CBCentralManager) {
        self.checkStatus { status in
            if let status = status {
                self.plugin.notifyListeners("statusUpdate", data: status.toDict())
            }
        }
    }

    public func checkStatus(completionHandler: @escaping (EspProvisioningStatus?) -> Void) -> Void {
        self.checkStatus(count: 0, completionHandler: completionHandler)
    }
    
    // wait up to 2 seconds for state to leave unknown
    private func checkStatus(count: Int, completionHandler: @escaping (EspProvisioningStatus?) -> Void) -> Void {
        if (count > 3) {
            // After 2 seconds we give up and will report the BLE status as whatever it is right now
            completionHandler(self.buildEspProvisioningStatus())
            return
        }
        
        if (self.centralManagerState == CBManagerState.unknown) {
            // The state can be unknown when first starting up. Give it a half second and check again.
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                self.checkStatus(count: count + 1, completionHandler: completionHandler)
            }
            return
        }
        
        completionHandler(self.buildEspProvisioningStatus())
    }
    
    private func buildEspProvisioningStatus() -> EspProvisioningStatus {
        let supported = self.centralManagerState != CBManagerState.unsupported
        let poweredOn = self.centralManagerState == CBManagerState.poweredOn || self.centralManagerState == CBManagerState.resetting
       
        let ble = EspProvisioningBluetoothStatus(supported: supported, allowed: self.bluetoothIsAllowed(), poweredOn: poweredOn)
        
        return EspProvisioningStatus(ble: ble)
    }
    
    private func bluetoothIsAllowed() -> Bool {
        if #available(iOS 13.0, *) {
            switch self.centralManagerAuthorizationState {
            case .allowedAlways:
                return true
            case .restricted:
                return true
            case .denied:
                return false
            case .notDetermined:
                return false
            @unknown default:
                return false // WTF should we do here?
            }
        }
        
        switch self.centralManagerState {
            case .poweredOn:
                return true
            case .poweredOff:
                return true
            case .unauthorized:
                return false
            case .resetting:
                return true
            case .unknown, .unsupported:
                return false
            @unknown default:
                return false
        }
    }
                                                 
    private func gatherPermissions() -> EspProvisioningPermissionsStatus {
        var bluetoothState: CapacitorPermissionStatus

        if #available(iOS 13.0, *) {
            switch self.centralManagerAuthorizationState {
            case .allowedAlways:
                bluetoothState = .granted
            case .restricted:
                bluetoothState = .granted
            case .denied:
                bluetoothState = .denied
            case .notDetermined:
                bluetoothState = .denied // WTF?
            @unknown default:
                bluetoothState = .denied // WTF?
            }
        } else {
            if (self.centralManagerState == .unauthorized) {
                bluetoothState = .denied
            } else {
                bluetoothState = .granted
            }
        }

        return EspProvisioningPermissionsStatus(ble: bluetoothState)
    }
    
    public func checkPermissions() -> EspProvisioningPermissionsStatus {
        return self.gatherPermissions()
    }

    public func requestPermissions() -> EspProvisioningPermissionsStatus {
        // TODO: should this even be implemented on iOS?
        return self.gatherPermissions()
    }
    
    func enableLogging() -> Void {
        self.loggingEnabled = true
        ESPProvisionManager.shared.enableLogs(true)
        self.debug("ENABLED LOGGING")
    }
    
    func disableLogging() -> Void {
        self.debug("DISABLED LOGGING")
        self.loggingEnabled = false
        ESPProvisionManager.shared.enableLogs(false)
    }
    
    func debug(_ message: String){
        if(self.loggingEnabled) {
            print("EspProvisioning: \(message)")
        }
    }

    func searchESPDevices(devicePrefix: String, transport: ESPTransport, security: ESPSecurity, completionHandler: @escaping ([ESPDevice]?, ESPProvisioningError?) -> Void) -> Void {
        // guard bluetoothIsAccessible(call) else { return } // TODO: make these guards work, this one fails the first time because BLE is slow as fuck
        
        self.deviceMap = [:] // reset our cached devices

        self.debug("Running searchESPDevices: devicePrefix=\(devicePrefix); transport=\(transport); security=\(security);")
        ESPProvisionManager.shared.searchESPDevices(devicePrefix: devicePrefix, transport: transport, security: security) { devices, error in
            self.debug("searchESPDevices callback: devicePrefix=\(devicePrefix); transport=\(transport); security=\(security);")

            if let error = error {
                if( error.code == 27){ // No Devices Found
                    completionHandler([], nil)
                }
                self.debug("searchESPDevices error: code=\(error.code); description=\(error.description);")
                completionHandler(nil, ESPProvisioningError.libraryError(error.description, errorCode: error.code))
            }
            
            // We need to cache devices so we can retain references to them while the capacitor bridge is used to
            // perform operations
            if let devices = devices {
                for device in devices {
                    self.deviceMap[device.name] = device
                }
            }

            completionHandler(devices, nil)
        }
    }
    
    func setConnectedDevice(_ newDevice: ESPDevice?){
        if let lastDevice = self.connectedDevice {
            if let newDevice = newDevice {
                if( newDevice.name == lastDevice.name ){
                    return // do nothing since nothing is changing
                }
            }
            
            lastDevice.bleDelegate = nil
            lastDevice.disconnect()
            self.connectedDevice = nil
        }
        
        if let newDevice = newDevice {
            self.connectedDevice = newDevice
            self.connectedDevice?.bleDelegate = self
            self.debug("Connected to device: \(newDevice.name)")
        }else{
            self.connectedDevice = nil
            self.debug("Cleared connected device")
        }
    }

    // TODO: Proof of Possession
    func connect(deviceName: String, proofOfPossession: String, completionHandler: @escaping (Bool, ESPProvisioningError?, Error?) -> Void) -> Void {
        guard let device = self.deviceMap[deviceName] else {
            return completionHandler(false, ESPProvisioningError.deviceNotFound(deviceName), nil)
        }

        self.debug("Connecting to device: \(deviceName) \(proofOfPossession)")

        device.connect(delegate: ConnectionDelegate(proofOfPossesion: proofOfPossession)) { status in
            switch status {
            case .connected:
                self.setConnectedDevice(device)
                return completionHandler(true, nil, nil)
            case .failedToConnect(let error):
                self.debug("Failed to connect to device: deviceName=\(deviceName) code=\(error.code) localizedDescription=\(error.localizedDescription)")
                return completionHandler(false, ESPProvisioningError.failedToConnect(deviceName, error), error)
            case .disconnected:
                return completionHandler(false, ESPProvisioningError.disconnected(deviceName), nil)
            }
        }
    }

    func scanWifiList(deviceName: String, completionHandler: @escaping ([ESPWifiNetwork]?, ESPProvisioningError?) -> Void) -> Void {
        guard let device = self.deviceMap[deviceName] else {
            return completionHandler(nil, ESPProvisioningError.deviceNotFound(deviceName))
        }

        self.debug("Scanning for WiFi from device: \(deviceName)")

        device.scanWifiList { networks, error in
            if let error = error {
                self.debug("Error scanning wifi: \(error)")
                completionHandler(nil, ESPProvisioningError.libraryError(error.description, errorCode: error.code))
                return
            }

            self.debug("Got networks: \(String(describing: networks))")

            completionHandler(networks, nil)
        }
    }

    func provision(deviceName: String, ssid: String, passPhrase: String? = "", completionHandler: @escaping (Bool, ESPProvisioningError?) -> Void) -> Void {
        guard let device = self.deviceMap[deviceName] else {
            return completionHandler(false, ESPProvisioningError.deviceNotFound(deviceName))
        }
        
        self.debug("Provisioning device: \(deviceName)")
        
        device.provision(ssid: ssid, passPhrase: passPhrase) { status in
            switch status {
            case .success:
                self.debug("Provisioned Device: \(deviceName)");
                completionHandler(true, nil)
            case .configApplied:
                self.debug("WiFi Config applied");
            case .failure(let error):
                completionHandler(false, ESPProvisioningError.libraryError(error.description, errorCode: error.code))
            }
        }
    }

    func sendCustomDataString(deviceName: String, path: String, dataString: String, completionHandler: @escaping (String?, ESPProvisioningError?) -> Void) -> Void {
        guard let stringData = dataString.data(using: .utf8) else {
            completionHandler(nil, ESPProvisioningError.unableToConvertStringToData)
            return
        }
        guard let device = self.deviceMap[deviceName] else {
            return completionHandler(nil, ESPProvisioningError.deviceNotFound(deviceName))
        }
        
        self.debug("Sending custom data[deviceName=\(deviceName)]: \(path), \(dataString)")
        
        device.sendData(path: path, data: stringData) { data, error in
            if let error = error {
                self.debug("Error sending custom data: \(error)")
                completionHandler(nil, ESPProvisioningError.libraryError(error.description, errorCode: error.code))
                return
            }
            
            if let data = data {
                if let returnString = String(data: data, encoding: .utf8) {
                    self.debug("Sent custom data: returnString=\(returnString)")
                    completionHandler(returnString, nil)
                }else{
                    completionHandler(nil, ESPProvisioningError.unableToConvertDataToString)
                }
            }else{
                completionHandler(nil, nil) // No Response Data
            }
        }
    }

    func disconnect(deviceName: String, completionHandler: @escaping (Bool, ESPProvisioningError?) -> Void) {
        self.debug("Device Disconnected: connectedDevice=\(String(describing: self.connectedDevice?.name))")

        guard let device = self.deviceMap[deviceName] else {
            return completionHandler(false, ESPProvisioningError.deviceNotFound(deviceName))
        }

        self.deviceMap = [:]
        self.setConnectedDevice(nil)
        device.disconnect()
        completionHandler(true, nil)
    }
    
    // MARK: ESPBLEDelegate
    
    public func peripheralConnected() {
        self.debug("Connected to device")
    }
    
    public func peripheralDisconnected(peripheral: CBPeripheral, error: Error?) {
        self.debug("Peripheral Disconnected \(String(describing: error?.localizedDescription))")

        // TODO: ensure disconnecting device is the connected device
        var data = ["peripheralName": peripheral.name ?? "unknown"]
        if let lastDevice = self.connectedDevice {
            data["deviceName"] = lastDevice.name
        }
        self.setConnectedDevice(nil)
        
        do {
            let jsonData = try JSONSerialization.data(withJSONObject: data, options: .prettyPrinted)
            self.debug("Notifying Disconnection Listeners: data=\(String(describing: data))")
        } catch {
            self.debug("Notifying Disconnection Listeners: data=can't print")
        }

        self.plugin.notifyListeners("deviceDisconnected", data: data)
    }
    
    public func peripheralFailedToConnect(peripheral: CBPeripheral?, error: Error?) {
        // TODO: ensure disconnecting device is the connected device
        var data = ["peripheralName": peripheral?.name ?? "unknown"]
        if let lastDevice = self.connectedDevice {
            data["deviceName"] = lastDevice.name
        }
        
        self.setConnectedDevice(nil)
        self.debug("Failed to connect to device: \(String(describing: peripheral?.name))")
        self.plugin.notifyListeners("deviceDisconnected", data: data)
    }

}
