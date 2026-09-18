/**
 *  Salus Switch Controller - Child Application
 *  
 *  Manages a single Salus switch device (relay zone) and creates a virtual switch
 *  device in Hubitat for status display only (control is via gateway).
 *  
 *  This child app is created and managed by the SalusGatewayConnector parent app.
 */

import groovy.transform.Field
import com.hubitat.app.DeviceWrapper

definition (
    name: "Salus Switch Controller",
    namespace: "hubitat",
    author: "Salus Hubitat Integration",
    description: "Controls a Salus switch (relay zone) for status display",
    category: "Convenience"
)

preferences {
    page name: "pageMain"
}

// Note: This is for status display only - control commands are NOT supported
// All control must go through the Salus gateway

void installed() {
    log.debug "Installed Salus Switch Controller"
    initialize()
}

void updated() {
    log.debug "Updated Salus Switch Controller"
    initialize()
}

void deleted() {
    log.debug "Deleted Salus Switch Controller"
    cleanup()
}

/**
 * Initialize the child application
 */
void initialize() {
    log.debug "Initializing Salus Switch Controller"
    
    // Get configuration from parent app or settings
    def deviceId = getDeviceId()
    def deviceName = getDeviceName()
    def deviceModel = getDeviceModel()
    def parentAppId = getParentAppId()
    
    log.debug "Initializing switch controller for device ${deviceId} (${deviceName})"
    
    // Create the virtual switch device if it doesn't exist
    createOrUpdateSwitchDevice()
    
    // Set up event subscriptions if needed
    subscribeToEvents()
}

/**
 * Clean up resources when child app is deleted
 */
void cleanup() {
    log.debug "Cleaning up Salus Switch Controller"
    // Optionally delete the virtual switch device
    // deleteVirtualSwitchDevice()
}

/**
 * Page configuration (minimal for child apps)
 */
def pageMain() {
    dynamicPage(name: "pageMain", uninstall: true, install: true) {
        section("Switch Information") {
            paragraph "Device ID: ${getDeviceId() ?: 'not set'}"
            paragraph "Device Name: ${getDeviceName() ?: 'not set'}"
            paragraph "Device Model: ${getDeviceModel() ?: 'not set'}"
            paragraph "Parent App: ${getParentAppId() ?: 'not set'}"
        }
        section("Status") {
            paragraph "Gateway Status: ${state.gatewayStatus ?: 'unknown'}"
            paragraph "Last Update: ${state.lastUpdate ?: 'never'}"
            paragraph "Switch State: ${state.switchState ?: 'unknown'}"
        }
    }
}

/**
 * Get device ID from settings
 */
String getDeviceId() {
    return settings.deviceId
}

/**
 * Get device name from settings
 */
String getDeviceName() {
    return settings.deviceName
}

/**
 * Get device model from settings
 */
String getDeviceModel() {
    return settings.deviceModel
}

/**
 * Get parent app ID from settings
 */
String getParentAppId() {
    return settings.parentAppId
}

/**
 * Get the parent application wrapper
 */
DeviceWrapper getParentApp() {
    def parentAppId = getParentAppId()
    if (parentAppId) {
        return getChildDevice(parentAppId)
    }
    return null
}

/**
 * Create or update the virtual switch device
 */
void createOrUpdateSwitchDevice() {
    log.debug "Creating/updating virtual switch device"
    
    // Check if we already have a virtual switch device
    String deviceLabel = "${getDeviceName() ?: 'Salus'} Switch"
    DeviceWrapper existingDevice = getChildDevices().find { 
        it.displayName == deviceLabel && it.deviceNetworkId?.startsWith("salus_switch_")
    }
    
    if (existingDevice) {
        log.debug "Virtual switch device already exists: ${existingDevice.displayName}"
        // Update device properties if needed
        updateVirtualSwitchDevice(existingDevice)
    } else {
        log.debug "Creating new virtual switch device"
        createVirtualSwitchDevice(deviceLabel)
    }
}

/**
 * Create a new virtual switch device
 */
void createVirtualSwitchDevice(String label) {
    log.debug "Creating virtual switch device: ${label}"
    
    try {
        // Create a child device using a switch driver
        // In a real implementation, we would specify the driver name
        String deviceId = createChildDevice(
                "SalusSwitch",  // This would be the driver name
                [
                    name: label,
                    label: label,
                    description: "Virtual switch for Salus ${getDeviceName() ?: 'device'} (status only)",
                    // Pass device-specific data via deviceData or settings
                    data: [
                        deviceId: getDeviceId(),
                        deviceName: getDeviceName(),
                        deviceModel: getDeviceModel(),
                        parentAppId: getParentAppId()
                    ]
                ]
        )
        
        if (deviceId) {
            log.debug "Successfully created virtual switch device: ${deviceId}"
            
            // Initialize the device state
            initializeVirtualSwitchDevice(deviceId)
        } else {
            log.error "Failed to create virtual switch device"
        }
    } catch (Exception e) {
        log.error "Error creating virtual switch device: ${e}"
    }
}

/**
 * Update an existing virtual switch device
 */
void updateVirtualSwitchDevice(DeviceWrapper device) {
    log.debug "Updating virtual switch device: ${device.displayName}"
    
    // Update any changed properties
    // In practice, most properties would be set at creation time
}

/**
 * Initialize the virtual switch device with current state
 */
void initializeVirtualSwitchDevice(String deviceId) {
    log.debug "Initializing virtual switch device state: ${deviceId}"
    
    DeviceWrapper device = getChildDevice(deviceId)
    if (device) {
        // In a real implementation, we would get the current state from the parent app
        // or gateway and set the initial device attributes
        // For now, we'll set some default values
        device.setDeviceState(
                switch: "off",  // Default to off
                supportedSwitchOperations: ["on", "off"] as List
        )
    }
}

/**
 * Update the virtual switch device with new state data
 */
void updateVirtualSwitchDeviceState(Map<String, Object> stateData) {
    log.debug "Updating virtual switch device state: ${stateData}"
    
    // Find the virtual switch device
    String deviceLabel = "${getDeviceName() ?: 'Salus'} Switch"
    DeviceWrapper switchDevice = getChildDevices().find { 
        it.displayName == deviceLabel && it.deviceNetworkId?.startsWith("salus_switch_")
    }
    
    if (switchDevice) {
        log.debug "Updating state for device ${switchDevice.deviceNetworkId}"
        
        // Update the device attributes
        switchDevice.setDeviceState(stateData)
    } else {
        log.warn "Virtual switch device not found for ${getDeviceName()}"
    }
}

/**
 * Handle incoming state updates from the parent app
 */
void updateState(Map<String, Object> newState) {
    log.debug "Received state update: ${newState}"
    
    // Update our local state
    if (newState.gatewayStatus) {
        state.gatewayStatus = newState.gatewayStatus
    }
    if (newState.lastUpdate) {
        state.lastUpdate = newState.lastUpdate
    }
    
    // If we have switch-specific state, update the virtual switch
    if (newState.switch != null) {
        state.switchState = newState.switch
        updateVirtualSwitchDeviceState([switch: newState.switch])
    }
}

/**
 * Handle commands sent to the virtual switch device
 * 
 * IMPORTANT: According to the requirements, this switch is for STATUS DISPLAY ONLY.
 * Control commands should NOT be supported on the virtual device - all control
 * must go through the Salus gateway via the parent app.
 * 
 * However, for compatibility with Hubitat's Switch capability, we'll accept
 * the commands but log that they're not supported for direct control.
 */
def command(String commandName, Map<String, Object> params) {
    log.debug "Received command: ${commandName} with params ${params}"
    
    // For status-only devices, we log that control is not supported directly
    // but we can still forward to the gateway if needed (though requirements say no)
    switch (commandName.toLowerCase()) {
        case "on":
        case "off":
            log.warn "Direct control of Salus switch ${getDeviceName()} is not supported via Hubitat. " +
                    "All control must go through the Salus gateway. Command ${commandName} ignored."
            
            // According to requirements, we should NOT send commands to the gateway from here
            // All pump actions must be done by sending commands to the Salus Gateway on the LAN
            // which will communicate with the relay controller
            
            // Return success but don't actually perform the action
            return [success: true, message: "Control via Hubitat disabled - use Salus gateway directly"]
            
        default:
            log.warn "Unsupported command for Salus switch: ${commandName}"
            return [success: false, error: "Unsupported command: ${commandName}"]
    }
}

/**
 * Subscribe to relevant events
 */
void subscribeToEvents() {
    log.debug "Subscribing to events"
    
    // Subscribe to parent app status changes if needed
    def parentApp = getParentApp()
    if (parentApp) {
        // Subscribe to gateway status changes from parent
        // subscribe(parentApp, "gatewayStatus", "gatewayStatusChanged")
    }
    
    // Subscribe to device events if needed
    // subscribe(device, "someEvent", "eventHandler")
}

/**
 * Handle gateway status changes from parent app
 */
void gatewayStatusChanged(String status) {
    log.debug "Gateway status changed to: ${status}"
    state.gatewayStatus = status
    // Update the virtual switch device to reflect gateway status
    updateVirtualSwitchDeviceState([gatewayStatus: status])
}
