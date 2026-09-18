/**
 *  Salus Thermostat Controller - Child Application
 *  
 *  Manages a single Salus thermostat device and creates a virtual thermostat
 *  device in Hubitat that can be used by other apps like Thermostat Scheduler.
 *  
 *  This child app is created and managed by the SalusGatewayConnector parent app.
 */

import groovy.transform.Field
import com.hubitat.app.DeviceWrapper

definition (
    name: "Salus Thermostat Controller",
    namespace: "hubitat",
    author: "Salus Hubitat Integration",
    description: "Controls a Salus thermostat and creates a virtual thermostat device",
    category: "Convenience"
)

preferences {
    page name: "pageMain"
}

// Supported thermostat attributes and commands (radiator heating only - no cooling/fan)
@Field static final List<String> SUPPORTED_PRESETS = ["follow_schedule", "permanent_hold", "temporary_hold", "standby", "away"]
@Field static final List<String> SUPPORTED_HVAC_MODES = ["off", "heat"]  // No cool for radiator heating
@Field static final List<String> SUPPORTED_FAN_MODES = ["auto"]  // Fixed for radiator systems

void installed() {
    log.debug "Installed Salus Thermostat Controller"
    initialize()
}

void updated() {
    log.debug "Updated Salus Thermostat Controller"
    initialize()
}

void deleted() {
    log.debug "Deleted Salus Thermostat Controller"
    cleanup()
}

/**
 * Initialize the child application
 */
void initialize() {
    log.debug "Initializing Salus Thermostat Controller"
    
    // Get configuration from parent app or settings
    def deviceId = getDeviceId()
    def deviceName = getDeviceName()
    def deviceModel = getDeviceModel()
    def parentAppId = getParentAppId()
    
    log.debug "Initializing thermostat controller for device ${deviceId} (${deviceName})"
    
    // Create the virtual thermostat device if it doesn't exist
    createOrUpdateThermostatDevice()
    
    // Set up event subscriptions if needed
    subscribeToEvents()
}

/**
 * Clean up resources when child app is deleted
 */
void cleanup() {
    log.debug "Cleaning up Salus Thermostat Controller"
    // Optionally delete the virtual thermostat device
    // deleteVirtualThermostatDevice()
}

/**
 * Page configuration (minimal for child apps)
 */
def pageMain() {
    dynamicPage(name: "pageMain", uninstall: true, install: true) {
        section("Thermostat Information") {
            paragraph "Device ID: ${getDeviceId() ?: 'not set'}"
            paragraph "Device Name: ${getDeviceName() ?: 'not set'}"
            paragraph "Device Model: ${getDeviceModel() ?: 'not set'}"
            paragraph "Parent App: ${getParentAppId() ?: 'not set'}"
        }
        section("Status") {
            paragraph "Gateway Status: ${state.gatewayStatus ?: 'unknown'}"
            paragraph "Last Update: ${state.lastUpdate ?: 'never'}"
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
 * Create or update the virtual thermostat device
 */
void createOrUpdateThermostatDevice() {
    log.debug "Creating/updating virtual thermostat device"
    
    // Check if we already have a virtual thermostat device
    String deviceLabel = "${getDeviceName() ?: 'Salus'} Thermostat"
    DeviceWrapper existingDevice = getChildDevices().find { 
        it.displayName == deviceLabel && it.deviceNetworkId?.startsWith("salus_thermostat_")
    }
    
    if (existingDevice) {
        log.debug "Virtual thermostat device already exists: ${existingDevice.displayName}"
        // Update device properties if needed
        updateVirtualThermostatDevice(existingDevice)
    } else {
        log.debug "Creating new virtual thermostat device"
        createVirtualThermostatDevice(deviceLabel)
    }
}

/**
 * Create a new virtual thermostat device
 */
void createVirtualThermostatDevice(String label) {
    log.debug "Creating virtual thermostat device: ${label}"
    
    try {
        // Create a child device using a thermostat driver
        // In a real implementation, we would specify the driver name
        String deviceId = createChildDevice(
                "SalusThermostat",  // This would be the driver name
                [
                    name: label,
                    label: label,
                    description: "Virtual thermostat for Salus ${getDeviceName() ?: 'device'}",
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
            log.debug "Successfully created virtual thermostat device: ${deviceId}"
            
            // Initialize the device state
            initializeVirtualThermostatDevice(deviceId)
        } else {
            log.error "Failed to create virtual thermostat device"
        }
    } catch (Exception e) {
        log.error "Error creating virtual thermostat device: ${e}"
    }
}

/**
 * Update an existing virtual thermostat device
 */
void updateVirtualThermostatDevice(DeviceWrapper device) {
    log.debug "Updating virtual thermostat device: ${device.displayName}"
    
    // Update any changed properties
    // In practice, most properties would be set at creation time
}

/**
 * Initialize the virtual thermostat device with current state
 */
void initializeVirtualThermostatDevice(String deviceId) {
    log.debug "Initializing virtual thermostat device state: ${deviceId}"
    
    DeviceWrapper device = getChildDevice(deviceId)
    if (device) {
        // In a real implementation, we would get the current state from the parent app
        // or gateway and set the initial device attributes
        // For now, we'll set some default values
        device.setDeviceState(
                temperature: 20.0,
                heatingSetpoint: 22.0,
                thermostatMode: "heat",
                thermostatFanMode: "auto",
                supportedThermostatModes: SUPPORTED_HVAC_MODES as List,
                supportedThermostatFanModes: SUPPORTED_FAN_MODES as List,
                availableThermostatPresets: SUPPORTED_PRESETS as List
        )
    }
}

/**
 * Update the virtual thermostat device with new state data
 */
void updateVirtualThermostatDeviceState(Map<String, Object> stateData) {
    log.debug "Updating virtual thermostat device state: ${stateData}"
    
    // Find the virtual thermostat device
    String deviceLabel = "${getDeviceName() ?: 'Salus'} Thermostat"
    DeviceWrapper thermostatDevice = getChildDevices().find { 
        it.displayName == deviceLabel && it.deviceNetworkId?.startsWith("salus_thermostat_")
    }
    
    if (thermostatDevice) {
        log.debug "Updating state for device ${thermostatDevice.deviceNetworkId}"
        
        // Update the device attributes
        thermostatDevice.setDeviceState(stateData)
    } else {
        log.warn "Virtual thermostat device not found for ${getDeviceName()}"
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
    
    // If we have device-specific state, update the virtual thermostat
    if (newState.temperature != null || 
        newState.heatingSetpoint != null ||
        newState.presetMode != null ||
        newState.hvacMode != null ||
        newState.hvacAction != null ||
        newState.isLocked != null) {
        
        Map<String, Object> deviceState = [:]
        if (newState.temperature != null) deviceState.temperature = newState.temperature
        if (newState.heatingSetpoint != null) deviceState.heatingSetpoint = newState.heatingSetpoint
        if (newState.presetMode != null) deviceState.presetMode = newState.presetMode
        if (newState.hvacMode != null) deviceState.thermostatMode = newState.hvacMode
        if (newState.hvacAction != null) deviceState.hvacAction = newState.hvacAction
        if (newState.isLocked != null) deviceState.isLocked = newState.isLocked
        
        updateVirtualThermostatDeviceState(deviceState)
    }
}

/**
 * Handle commands sent to the virtual thermostat device
 * 
 * This method is called when other Hubitat apps (like Thermostat Scheduler)
 * send commands to our virtual thermostat device
 */
def command(String commandName, Map<String, Object> params) {
    log.debug "Received command: ${commandName} with params ${params}"
    
    // Forward the command to the parent app to send to the gateway
    def parentApp = getParentApp()
    if (parentApp) {
        def result = parentApp.sendCommandToGateway(
                getDeviceId(), 
                commandName, 
                params
        )
        
        if (result && result.success) {
            log.debug "Successfully forwarded command ${commandName} to gateway"
            
            // Optionally update local state optimistically
            updateStateOptimistically(commandName, params)
            
            return result
        } else {
            log.warn "Failed to forward command ${commandName} to gateway: ${result?.error}"
            return [success: false, error: "Gateway communication failed: ${result?.error}"]
        }
    } else {
        log.error "Could not find parent app to forward command to"
        return [success: false, error: "Parent app not found"]
    }
}

/**
 * Optimistically update the local state when a command is sent
 */
void updateStateOptimistically(String commandName, Map<String, Object> params) {
    log.debug "Optimistically updating state for command ${commandName}"
    
    Map<String, Object> stateUpdate = [:]
    
    switch (commandName) {
        case "setTemperature":
            if (params.value) {
                stateUpdate.heatingSetpoint = params.value as Number
                // Also update temperature if it's a set temperature command
                stateUpdate.temperature = params.value as Number
            }
            break
            
        case "setHeatingSetpoint":
            if (params.value) {
                stateUpdate.heatingSetpoint = params.value as Number
            }
            break
            
        case "setThermostatMode":
            if (params.value) {
                stateUpdate.thermostatMode = params.value as String
            }
            break
            
        case "setThermostatFanMode":
            if (params.value) {
                stateUpdate.thermostatFanMode = params.value as String
            }
            break
            
        case "setPresetMode":
            if (params.value) {
                stateUpdate.presetMode = params.value as String
            }
            break
            
        case "setThermostatLock":
            if (params.value != null) {
                stateUpdate.isLocked = params.value as Boolean
            }
            break
    }
    
    if (!stateUpdate.isEmpty()) {
        updateState(stateUpdate)
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
    // Update the virtual thermostat device to reflect gateway status
    updateVirtualThermostatDeviceState([gatewayStatus: status])
}
