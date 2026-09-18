/**
 *  Salus Thermostat Device Driver
 *  
 *  Virtual thermostat device for Salus heating systems (radiator/baseboard).
 *  Compatible with Hubitat Thermostat Scheduler and other thermostat-aware apps.
 *  
 *  This device is created and managed by the SalusThermostatController child app.
 */

import groovy.transform.Field
import com.hubitat.app.DeviceWrapper

// Supported thermostat capabilities and attributes
@Field static final List<String> SUPPORTED_THERMOSTAT_MODES = ["off", "heat"]  // No cool for radiator heating
@Field static final List<String> SUPPORTED_THERMOSTAT_FAN_MODES = ["auto"]     // Fixed for radiator systems
@Field static final List<String> SUPPORTED_THERMOSTAT_PRESETS = ["follow_schedule", "permanent_hold", "temporary_hold", "standby", "away"]

metadata {
    definition (
        name: "Salus Thermostat",
        namespace: "hubitat",
        author: "Salus Hubitat Integration",
        description: "Virtual thermostat device for Salus heating systems",
        // The Vocab key allows this device to work with voice assistants
        vocab: {
            // Standard thermostat vocabulary
        }
    )
    
    // Thermostat capability
    capability "Thermostat"
    
    // Additional capabilities that may be useful
    capability "Relative Humidity Measurement"  // If the thermostat reports humidity
    capability "Battery"                       // If the thermostat reports battery level
    capability "Actuator"                      // For command handling
    capability "Refresh"                       // For manual refresh
    capability "Sensor"                        // Generic sensor capability
    
    // Attributes
    attribute "temperature", "NUMBER"
    attribute "heatingSetpoint", "NUMBER"
    attribute "coolingSetpoint", "NUMBER"
    attribute "thermostatMode", "ENUM", SUPPORTED_THERMOSTAT_MODES
    attribute "thermostatFanMode", "ENUM", SUPPORTED_THERMOSTAT_FAN_MODES
    attribute "thermostatOperation", "STRING"
    attribute "supportedThermostatModes", "LIST", SUPPORTED_THERMOSTAT_MODES
    attribute "supportedThermostatFanModes", "LIST", SUPPORTED_THERMOSTAT_FAN_MODES
    attribute "availableThermostatPresets", "LIST", SUPPORTED_THERMOSTAT_PRESETS
    attribute "presetMode", "ENUM", SUPPORTED_THERMOSTAT_PRESETS
    attribute "isLocked", "BOOL"
    attribute "gatewayStatus", "STRING"
    
    // Commands
    command "setTemperature", "NUMBER"
    command "setHeatingSetpoint", "NUMBER"
    command "setCoolingSetpoint", "NUMBER"
    command "setThermostatMode", "ENUM", SUPPORTED_THERMOSTAT_MODES
    command "setThermostatFanMode", "ENUM", SUPPORTED_THERMOSTAT_FAN_MODES
    command "setPresetMode", "ENUM", SUPPORTED_THERMOSTAT_PRESETS
    command "setThermostatLock", "BOOL"
    command "refresh"
    
    // Optional attributes for extended functionality
    attribute "currentHumidity", "NUMBER"
    attribute "battery", "NUMBER"
    
    // Configuration
    preferences {
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false
        input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: false
    }
}

def installed() {
    log.debug "Installed Salus Thermostat device"
    initialize()
}

def updated() {
    log.debug "Updated Salus Thermostat device"
    initialize()
}

def deleted() {
    log.debug "Deleted Salus Thermostat device"
    cleanup()
}

def initialize() {
    log.debug "Initializing Salus Thermostat device"
    
    // Initialize state if needed
    if (!state.initialized) {
        state.initialized = true
        state.heatingSetpoint = 22.0
        state.temperature = 20.0
        state.thermostatMode = "heat"
        state.thermostatFanMode = "auto"
        state.presetMode = "follow_schedule"
        state.isLocked = false
        state.gatewayStatus = "unknown"
        
        // Set initial attribute values
        setThermostatAttributes()
    }
}

def cleanup() {
    log.debug "Cleaning up Salus Thermostat device"
    // Any cleanup needed
}

/**
 * Handle incoming commands
 */
def command(String commandName, Map<String, Object> params) {
    log.debug "Thermostat command received: ${commandName} with params ${params}"
    
    switch (commandName.toLowerCase()) {
        case "settemperature":
            if (params.value) {
                def temp = params.value as Number
                state.heatingSetpoint = temp
                // For a set temperature command, we might also update current temp optimistically
                // state.temperature = temp
                setAttribute("heatingSetpoint", temp)
                // Note: In a real implementation, we'd send this to the gateway via parent app
                // But the actual command handling is done in the child app
                createEvent(name: "thermostatSetpoint", value: temp, 
                        descriptionText: "Heating setpoint changed to ${temp}°C",
                        isStateChange: true)
            }
            break
            
        case "setheatingsetpoint":
            if (params.value) {
                def temp = params.value as Number
                state.heatingSetpoint = temp
                setAttribute("heatingSetpoint", temp)
                createEvent(name: "thermostatSetpoint", value: temp, 
                        descriptionText: "Heating setpoint changed to ${temp}°C",
                        isStateChange: true)
            }
            break
            
        case "setcoolingsetpoint":
            // Cooling setpoint not supported for radiator heating, but we'll accept it for compatibility
            if (params.value) {
                def temp = params.value as Number
                state.coolingSetpoint = temp
                setAttribute("coolingSetpoint", temp)
                createEvent(name: "thermostatCoolingSetpoint", value: temp, 
                        descriptionText: "Cooling setpoint changed to ${temp}°C",
                        isStateChange: true)
            }
            break
            
        case "setthermostatmode":
            if (params.value) {
                def mode = params.value as String
                if (SUPPORTED_THERMOSTAT_MODES.contains(mode)) {
                    state.thermostatMode = mode
                    setAttribute("thermostatMode", mode)
                    createEvent(name: "thermostatMode", value: mode, 
                            descriptionText: "Thermostat mode changed to ${mode}",
                            isStateChange: true)
                } else {
                    log.warn "Unsupported thermostat mode: ${mode}"
                }
            }
            break
            
        case "setthermostatfanmode":
            // Fan mode is fixed for radiator systems, but we'll accept it for compatibility
            if (params.value) {
                def mode = params.value as String
                if (SUPPORTED_THERMOSTAT_FAN_MODES.contains(mode)) {
                    state.thermostatFanMode = mode
                    setAttribute("thermostatFanMode", mode)
                    createEvent(name: "thermostatFanMode", value: mode, 
                            descriptionText: "Thermostat fan mode changed to ${mode}",
                            isStateChange: true)
                } else {
                    log.warn "Unsupported thermostat fan mode: ${mode}"
                }
            }
            break
            
        case "setpresetmode":
            if (params.value) {
                def preset = params.value as String
                if (SUPPORTED_THERMOSTAT_PRESETS.contains(preset)) {
                    state.presetMode = preset
                    setAttribute("presetMode", preset)
                    createEvent(name: "thermostatPresetMode", value: preset, 
                            descriptionText: "Thermostat preset changed to ${preset}",
                            isStateChange: true)
                } else {
                    log.warn "Unsupported thermostat preset: ${preset}"
                }
            }
            break
            
        case "setthermostatlock":
            if (params.value != null) {
                def locked = params.value as Boolean
                state.isLocked = locked
                setAttribute("isLocked", locked)
                createEvent(name: "thermostatLock", value: locked, 
                        descriptionText: "Thermostat lock changed to ${locked}",
                        isStateChange: true)
            }
            break
            
        case "refresh":
            refresh()
            break
            
        default:
            log.warn "Unsupported command: ${commandName}"
            return false
    }
    
    return true
}

/**
 * Refresh the device state (get latest from gateway via parent app)
 */
def refresh() {
    log.debug "Refreshing Salus Thermostat device"
    // In a real implementation, we would request updated state from the parent app
    // which would get it from the gateway
    // For now, we'll just log the refresh
    createEvent(name: "refresh", value: "refreshed", 
            descriptionText: "Thermostat state refreshed",
            isStateChange: true)
}

/**
 * Set thermostat attributes based on current state
 */
void setThermostatAttributes() {
    log.debug "Setting thermostat attributes"
    
    setAttribute("temperature", state.temperature)
    setAttribute("heatingSetpoint", state.heatingSetpoint)
    setAttribute("coolingSetpoint", state.coolingSetpoint ?: 0)  // Default if not set
    setAttribute("thermostatMode", state.thermostatMode)
    setAttribute("thermostatFanMode", state.thermostatFanMode)
    setAttribute("presetMode", state.presetMode)
    setAttribute("isLocked", state.isLocked ?: false)
    setAttribute("supportedThermostatModes", SUPPORTED_THERMOSTAT_MODES)
    setAttribute("supportedThermostatFanModes", SUPPORTED_THERMOSTAT_FAN_MODES)
    setAttribute("availableThermostatPresets", SUPPORTED_THERMOSTAT_PRESETS)
    setAttribute("gatewayStatus", state.gatewayStatus ?: "unknown")
    
    // Create events for changed attributes
    createEvent(name: "temperature", value: state.temperature, 
            descriptionText: "Current temperature: ${state.temperature}°C",
            unit: "C", isStateChange: true)
    createEvent(name: "heatingSetpoint", value: state.heatingSetpoint, 
            descriptionText: "Heating setpoint: ${state.heatingSetpoint}°C",
            unit: "C", isStateChange: true)
    createEvent(name: "thermostatMode", value: state.thermostatMode, 
            descriptionText: "Thermostat mode: ${state.thermostatMode}",
            isStateChange: true)
    createEvent(name: "presetMode", value: state.presetMode, 
            descriptionText: "Thermostat preset: ${state.presetMode}",
            isStateChange: true)
    createEvent(name: "isLocked", value: state.isLocked ?: false, 
            descriptionText: "Thermostat lock: ${state.isLocked ?: false}",
            isStateChange: true)
}

/**
 * Update device state from external source (e.g., parent app)
 */
void updateState(Map<String, Object> newState) {
    log.debug "Updating thermostat state: ${newState}"
    
    boolean stateChanged = false
    
    newState.each { key, value ->
        switch (key) {
            case "temperature":
                if (state.temperature != value) {
                    state.temperature = value as Number
                    stateChanged = true
                }
                break
            case "heatingSetpoint":
                if (state.heatingSetpoint != value) {
                    state.heatingSetpoint = value as Number
                    stateChanged = true
                }
                break
            case "coolingSetpoint":
                if (state.coolingSetpoint != value) {
                    state.coolingSetpoint = value as Number
                    stateChanged = true
                }
                break
            case "thermostatMode":
                if (state.thermostatMode != value) {
                    state.thermostatMode = value as String
                    stateChanged = true
                }
                break
            case "thermostatFanMode":
                if (state.thermostatFanMode != value) {
                    state.thermostatFanMode = value as String
                    stateChanged = true
                }
                break
            case "presetMode":
                if (state.presetMode != value) {
                    state.presetMode = value as String
                    stateChanged = true
                }
                break
            case "isLocked":
                if (state.isLocked != value) {
                    state.isLocked = value as Boolean
                    stateChanged = true
                }
                break
            case "gatewayStatus":
                if (state.gatewayStatus != value) {
                    state.gatewayStatus = value as String
                    stateChanged = true
                }
                break
            case "currentHumidity":
                if (state.currentHumidity != value) {
                    state.currentHumidity = value as Number
                    stateChanged = true
                }
                break
            case "battery":
                if (state.battery != value) {
                    state.battery = value as Number
                    stateChanged = true
                }
                break
        }
    }
    
    if (stateChanged) {
        setThermostatAttributes()
    }
}

/**
 * Handle attribute changes (if needed)
 */
def attributeChanged(String attributeName, Object value) {
    log.debug "Attribute changed: ${attributeName} = ${value}"
    // Handle any attribute change logic if needed
}
