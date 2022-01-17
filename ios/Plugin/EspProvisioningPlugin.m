#import <Foundation/Foundation.h>
#import <Capacitor/Capacitor.h>

// Define the plugin using the CAP_PLUGIN Macro, and
// each method the plugin supports using the CAP_PLUGIN_METHOD macro.
CAP_PLUGIN(EspProvisioningPlugin, "EspProvisioning",
    CAP_PLUGIN_METHOD(searchESPDevices, CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(connect, CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(scanWifiList, CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(provision, CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(disconnect, CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(openLocationSettings, CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(openBluetoothSettings, CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(openAppSettings, CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(enableLogging, CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(disableLogging, CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(checkPermissions, CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(requestPermissions, CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(sendCustomDataString, CAPPluginReturnPromise);
)
