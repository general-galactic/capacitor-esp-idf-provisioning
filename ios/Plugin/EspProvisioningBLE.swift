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


public class EspProvisioningBLE: NSObject, ESPBLEDelegate {

    private let plugin: CAPPlugin
    private var deviceMap = [String: ESPDevice]()
    private var loggingEnabled = false
    
    private var connectedDevice: ESPDevice?

    private lazy var centralManager: CBCentralManager = {
        return CBCentralManager.init()
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

    public func checkPermissions() -> [String:String] {
        var bluetoothState: String

        // Allowed values defined by PermissionState from capacitor: 'prompt' | 'prompt-with-rationale' | 'granted' | 'denied'
        switch self.centralManagerState {
        case .poweredOn:
            if #available(iOS 13.0, *) {
                switch self.centralManagerAuthorizationState {
                case .allowedAlways:
                    bluetoothState = "granted"
                case .restricted:
                    bluetoothState = "granted"
                case .denied:
                    bluetoothState = "denied"
                case .notDetermined:
                    bluetoothState = "prmompt"
                @unknown default:
                    bluetoothState = "denied"
                }
            } else {
                // TODO: is this right?
                bluetoothState = "granted"
            }
        case .poweredOff:
            bluetoothState = "prompt"
        case .unauthorized:
            bluetoothState = "denied"
        case .resetting:
            bluetoothState = "granted" // TODO: is this right?
        case .unknown, .unsupported:
            bluetoothState = "denied"
        @unknown default:
            bluetoothState = "denied"
        }

        return [
            "ble": bluetoothState,
            "location": "granted" // Android requires location permissions, we just lie on iOS
        ]
    }

    public func requestPermissions() -> [String:String] {
        // TODO: should this even be implemented?
        return self.checkPermissions()
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

    func provision(deviceName: String, ssid: String, passPhrase: String, completionHandler: @escaping (Bool, ESPProvisioningError?) -> Void) -> Void {
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
        self.debug("Device disconnected \(String(describing: error?.localizedDescription))")
        
        var data = ["peripheralName": peripheral.name ?? "unknown"]
        if let lastDevice = self.connectedDevice {
            data["deviceName"] = lastDevice.name
        }
        self.setConnectedDevice(nil)
        
        self.plugin.notifyListeners("deviceDisconnected", data: data)
    }
    
    public func peripheralFailedToConnect(peripheral: CBPeripheral?, error: Error?) {
        self.setConnectedDevice(nil)
        self.debug("Failed to connect to device: \(String(describing: peripheral?.name))")
    }

}
