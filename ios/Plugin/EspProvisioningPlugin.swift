
import Foundation
import Capacitor
import ESPProvision
import CoreBluetooth

/**
 * Please read the Capacitor iOS Plugin Development Guide
 * here: https://capacitorjs.com/docs/plugins/ios
 */
@objc(EspProvisioningPlugin)
public class EspProvisioningPlugin: CAPPlugin {
    
    private lazy var implementation = EspProvisioningBLE(self)
   
    @objc override public func checkPermissions(_ call: CAPPluginCall) {
        let result = implementation.checkPermissions()
        call.resolve(result)
    }

    @objc override public func requestPermissions(_ call: CAPPluginCall) {
        let result = implementation.requestPermissions()
        call.resolve(result)
    }
    
    @objc func enableLogging(_ call: CAPPluginCall) {
        implementation.enableLogging()
        call.resolve()
    }

    @objc func disableLogging(_ call: CAPPluginCall) {
        implementation.disableLogging()
        call.resolve()
    }

    @objc func searchESPDevices(_ call: CAPPluginCall) {
        guard let devicePrefix = call.getString("devicePrefix") else {
            return call.reject("devicePrefix is required")
        }
        guard let transport = self.stringToEspTransport(call.getString("transport")) else {
            return call.reject("transport is required")
        }
        guard let security = self.stringToEspSecurity(call.getString("security")) else {
            return call.reject("security is required")
        }
        
        if(transport == .softap){
            return call.reject("softap transport is not supported")
        }
  
        implementation.searchESPDevices(devicePrefix: devicePrefix, transport: transport, security: security) { devices, error in
            if let error = error {
                call.reject(error.message)
                return
            }
            
            let response = self.searchESPDevicesResponse(devices)
            call.resolve(response)
        }
    }
  
    @objc func connect(_ call: CAPPluginCall) {
        guard let deviceName = call.getString("deviceName") else {
            return call.reject("deviceName is required")
        }
        guard let proofOfPossession = call.getString("proofOfPossession") else {
            return call.reject("proofOfPossession is required")
        }
        
        implementation.connect(deviceName: deviceName, proofOfPossession: proofOfPossession) { success, error, cause in
            if let error = error {
                if let cause = cause {
                    call.reject(error.message, "connect-error-1", cause)
                }else{
                    call.reject(error.message)
                }
                return
            }
            call.resolve(["connected": success])
        }
    }
    
    @objc func scanWifiList(_ call: CAPPluginCall) {
        guard let deviceName = call.getString("deviceName") else {
            return call.reject("deviceName is required")
        }

        implementation.scanWifiList(deviceName: deviceName) { networks, error in
            if let error = error {
                call.reject(error.message)
                return
            }
        
            let response = self.scanWifiListResponse(networks)
            call.resolve(response)
        }
    }
    
    @objc func provision(_ call: CAPPluginCall) {
        guard let deviceName = call.getString("deviceName") else {
            return call.reject("deviceName is required")
        }
        guard let ssid = call.getString("ssid") else {
            return call.reject("ssid is required")
        }
        guard let passPhrase = call.getString("passPhrase") else {
            return call.reject("passPhrase is required")
        }
        
        implementation.provision(deviceName: deviceName, ssid: ssid, passPhrase: passPhrase) { success, error in
            if let error = error {
                call.reject(error.message)
                return
            }
            call.resolve(["success": true])
        }
    }
    
    @objc func sendCustomDataString(_ call: CAPPluginCall){
        guard let deviceName = call.getString("deviceName") else {
            return call.reject("deviceName is required")
        }
        guard let path = call.getString("path") else {
            return call.reject("path is required")
        }
        guard let dataString = call.getString("dataString") else {
            return call.reject("dataString is required")
        }
       
        implementation.sendCustomDataString(deviceName: deviceName, path: path, dataString: dataString) { returnString, error in
            if let error = error {
                call.reject(error.message)
                return
            }
            
            if let returnString = returnString {
                call.resolve(["success": true, "returnString": returnString])
            }else{
                call.resolve(["success": true])
            }
        }
    }
    
    @objc func disconnect(_ call: CAPPluginCall) {
        guard let deviceName = call.getString("deviceName") else {
            return call.reject("deviceName is required")
        }

        implementation.disconnect(deviceName: deviceName) { success, error in
            if let error = error {
                call.reject(error.message)
                return
            }
            call.resolve(["success": success])
        }
    }
    
    @objc func openLocationSettings(_ call: CAPPluginCall) {
        call.unavailable("openLocationSettings is not available on iOS.")
    }

    @objc func openBluetoothSettings(_ call: CAPPluginCall) {
        call.unavailable("openBluetoothSettings is not available on iOS.")
    }

    @objc func openAppSettings(_ call: CAPPluginCall) {
        guard let settingsUrl = URL(string: UIApplication.openSettingsURLString) else {
            call.reject("Cannot open app settings.")
            return
        }

        DispatchQueue.main.async {
            if UIApplication.shared.canOpenURL(settingsUrl) {
                UIApplication.shared.open(settingsUrl, completionHandler: { (success) in
                    call.resolve([
                        "value": success
                    ])
                })
            } else {
                call.reject("Cannot open app settings.")
            }
        }
    }
    
    func scanWifiListResponse(_ networks: [ESPWifiNetwork]? ) -> [String:[[String:Any]]] {
        guard let networks = networks else {
            return ["networks": []]
        }

        var networkOutputs: [[String:Any]] = []
        for network in networks {
            var networkOutput: [String:Any] = [:]
            networkOutput["ssid"] = network.ssid
            networkOutput["rssi"] = network.rssi
            networkOutput["auth"] = self.authModeToString(network.auth)
            networkOutputs.append(networkOutput)
        }
        return ["networks": networkOutputs]
    }
    
    func searchESPDevicesResponse(_ devices: [ESPDevice]? ) -> [String: [[String:Any]]] {
        guard let devices = devices else{
            return ["devices": []]
        }
        
        if(devices.isEmpty) { return ["devices": []] }
        
        var devicesOutput: [Dictionary<String, Any>] = []
        for device in devices {
            var deviceOutput: [String:Any] = [:]
            deviceOutput["name"] = device.name
            if let advertisementData = device.advertisementData {
                deviceOutput["advertisementData"] = self.advertisementDataToJS(advertisementData)
            }
            devicesOutput.append(deviceOutput)
        }
        return ["devices": devicesOutput]
    }
    
    func advertisementDataToJS(_ advertisementData: [String:Any]) -> [String:Any] {
        var output: [String:Any] = [:]

        output["kCBAdvDataLocalName"] = advertisementData["kCBAdvDataLocalName"]
        output["kCBAdvDataIsConnectable"] = advertisementData["kCBAdvDataIsConnectable"]
        output["kCBAdvDataRxSecondaryPHY"] = advertisementData["kCBAdvDataRxSecondaryPHY"]
        output["kCBAdvDataRxPrimaryPHY"] = advertisementData["kCBAdvDataRxPrimaryPHY"]
        output["kCBAdvDataManufacturerData"] = advertisementData["kCBAdvDataManufacturerData"]

        if let kCBAdvDataServiceUUIDs = advertisementData["kCBAdvDataServiceUUIDs"] as? NSArray {
            var convertedServiceUUIDs: [String] = []
            for uuid in kCBAdvDataServiceUUIDs {
                if let uuid = uuid as? CBUUID {
                    convertedServiceUUIDs.append(uuid.uuidString)
                }
            }
            output["kCBAdvDataServiceUUIDs"] = convertedServiceUUIDs
        }
    
        return output
    }

    func stringToEspTransport( _ transport: String? ) -> ESPTransport? {
        switch transport {
        case "ble":
            return ESPTransport.ble
        case "softap":
            return ESPTransport.softap
        default:
            return nil
        }
    }
    
    func espTransportToString( _ transport: ESPTransport ) -> String {
        switch transport {
        case .softap:
            return "softap"
        case .ble:
            return "ble"
        }
    }
    
    func stringToEspSecurity( _ security: String? ) -> ESPSecurity? {
        switch security {
        case "secure":
            return ESPSecurity.secure
        case "unsecure":
            return ESPSecurity.unsecure
        default:
            return nil
        }
    }
    
    func espSecurityToString( _ security: ESPSecurity ) -> String {
        switch security {
        case .secure:
            return "secure"
        case .unsecure:
            return "unsecure"
        }
    }
    
    func authModeToString( _ authMode: Espressif_WifiAuthMode ) -> String {
        switch authMode {
        case .open:
            return "open"
        case .wep:
            return "wep"
        case .wpaPsk:
            return "wpapsk"
        case .wpa2Psk:
            return "wpa2psk"
        case .wpaWpa2Psk:
            return "wpawpa2psk"
        case .wpa2Enterprise:
            return "wpa2enterprise"
        case .UNRECOGNIZED(let int):
            return "unrecognized:\(int)"
        }
    }
    
}
