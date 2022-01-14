import Capacitor
import ESPProvision
import CoreBluetooth
import Foundation

enum ESPProvisioningError: Error {
    case missingRequiredField(_ missingFieldName: String)
    case libraryError(_ errorMessage: String, errorCode: Int? )
    case deviceNotFound(_ deviceId: String)
    case failedToConnect(_ deviceId: String)
    case disconnected(_ deviceId: String)
    case unableToConvertStringToData
    case unableToConvertDataToString
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


@objc public class EspProvisioningBLE: NSObject {

    private var deviceMap = [String: ESPDevice]()
    private var loggingEnabled = false

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
    }
    
    func disableLogging() -> Void {
        self.loggingEnabled = false
        ESPProvisionManager.shared.enableLogs(false)
    }
    
    func print(_ message: String){
        if(self.loggingEnabled) {
            print(message)
        }
    }

    func searchESPDevices(devicePrefix: String, transport: ESPTransport, security: ESPSecurity, completionHandler: @escaping ([ESPDevice]?, Error?) -> Void) -> Void {
        // guard bluetoothIsAccessible(call) else { return } // TODO: make these guards work, this one fails the first time because BLE is slow as fuck
        
        self.deviceMap = [:] // reset our cached devices

        self.print("Running searchESPDevices: devicePrefix=\(devicePrefix); transport=\(transport); security=\(security);")
        ESPProvisionManager.shared.searchESPDevices(devicePrefix: devicePrefix, transport: transport, security: security) { devices, error in
            self.print("searchESPDevices callback: devicePrefix=\(devicePrefix); transport=\(transport); security=\(security);")

            if let error = error {
                if( error.code == 27){ // No Devices Found
                    completionHandler([], nil)
                }
                self.print("searchESPDevices error: code=\(error.code); description=\(error.description);")
                completionHandler(nil, ESPProvisioningError.libraryError(error.description, errorCode: error.code))
            }

            completionHandler(devices, nil)
        }
    }

    // TODO: Proof of Possession
    func connect(deviceId: String, proofOfPossession: String, completionHandler: @escaping (Bool, Error?) -> Void) -> Void {
        guard let device = self.deviceMap[deviceId] else {
            return completionHandler(false, ESPProvisioningError.deviceNotFound(deviceId))
        }

        self.print("Connecting to device: \(deviceId)")

        device.connect(delegate: ConnectionDelegate(proofOfPossesion: proofOfPossession)) { status in
            switch status {
            case .connected:
                self.print("Connected to device: \(deviceId)")
                return completionHandler(true, nil)
            case .failedToConnect(_):
                return completionHandler(false, ESPProvisioningError.failedToConnect(deviceId))
            case .disconnected:
                return completionHandler(false, ESPProvisioningError.disconnected(deviceId))
            }
        }
    }

    func scanWifiList(deviceId: String, completionHandler: @escaping ([ESPWifiNetwork]?, Error?) -> Void) -> Void {
        guard let device = self.deviceMap[deviceId] else {
            return completionHandler(nil, ESPProvisioningError.deviceNotFound(deviceId))
        }

        self.print("Scanning for WiFi from device: \(deviceId)")

        device.scanWifiList { networks, error in
            if let error = error {
                self.print("Error scanning wifi: \(error)")
                completionHandler(nil, ESPProvisioningError.libraryError(error.description, errorCode: error.code))
                return
            }

            self.print("Got networks: \(String(describing: networks))")

            completionHandler(networks, nil)
        }
    }

    func provision(deviceId: String, ssid: String, passPhrase: String, completionHandler: @escaping (Bool, Error?) -> Void) -> Void {
        guard let device = self.deviceMap[deviceId] else {
            return completionHandler(false, ESPProvisioningError.deviceNotFound(deviceId))
        }
        
        self.print("Provisioning device: \(deviceId)")
        
        device.provision(ssid: ssid, passPhrase: passPhrase) { status in
            switch status {
            case .success:
                self.print("Provisioned Device: \(deviceId)");
                completionHandler(true, nil)
            case .configApplied:
                self.print("WiFi Config applied");
            case .failure(let error):
                completionHandler(false, ESPProvisioningError.libraryError(error.description, errorCode: error.code))
            }
        }
    }

    func sendCustomDataString(deviceId: String, path: String, string: String, completionHandler: @escaping (String?, Error?) -> Void) -> Void {
        guard let stringData = string.data(using: .utf8) else {
            completionHandler(nil, ESPProvisioningError.unableToConvertStringToData)
            return
        }
        guard let device = self.deviceMap[deviceId] else {
            return completionHandler(nil, ESPProvisioningError.deviceNotFound(deviceId))
        }
        
        self.print("Sending custom data[deviceId=\(deviceId)]: \(path), \(string)")
        
        device.sendData(path: path, data: stringData) { data, error in
            if let error = error {
                self.print("Error sending custom data: \(error)")
                completionHandler(nil, ESPProvisioningError.libraryError(error.description, errorCode: error.code))
                return
            }
            
            if let data = data {
                if let returnString = String(data: data, encoding: .utf8) {
                    self.print("Sent custom data: returnString=\(returnString)")
                    completionHandler(returnString, nil)
                }else{
                    completionHandler(nil, ESPProvisioningError.unableToConvertDataToString)
                }
            }else{
                completionHandler(nil, nil) // No Response Data
            }
        }
    }

    func disconnect(deviceId: String, completionHandler: @escaping (Bool, Error?) -> Void) {
        guard let device = self.deviceMap[deviceId] else {
            return completionHandler(false, ESPProvisioningError.deviceNotFound(deviceId))
        }

        device.disconnect()
        completionHandler(true, nil)
    }

    public func getProofOfPossesion(forDevice: ESPDevice, completionHandler: @escaping (String) -> Void) {
        completionHandler("secret") // TODO: need to actually do this
    }

}
