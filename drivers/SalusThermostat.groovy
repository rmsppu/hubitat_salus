/**
 *  Salus Thermostat Device Driver
 *  
 *  Virtual thermostat device for Salus heating systems -- radiator/baseboard.
 *  Compatible with Hubitat Thermostat Scheduler and other thermostat-aware apps.
 *  
 *  This device is created and managed by the SalusThermostatController child app.
 *  
 *  COMMUNICATION FLOW:
 *  1. Hubitat apps -- e.g., Thermostat Scheduler -- send commands to this virtual device
 *  2. Driver command methods -- setTemperature, setHeatingSetpoint, etc. -- are called
 *  3. Child app -- SalusThermostatController -- receives command via its command method
 *  4. Child app forwards command to parent app -- SalusGatewayConnector
 *  5. Parent app encrypts command and sends via HTTP POST to Salus Gateway
 *  6. Gateway responds asynchronously to parent app's handleGatewayResponse
 *  7. Parent app decrypts response and calls child app's updateState
 *  8. Child app calls driver's updateState to update device attributes
 *  
 *  BUILD AND TEST:
 *  - Install SalusCommon library first -- Libraries Code
 *  - Install this driver -- Drivers Code  
 *  - Install SalusGatewayConnector parent app -- Apps Code
 *  - Install SalusThermostatController child app -- Apps Code
 *  - Configure parent app with gateway IP and EUID
 *  - Parent app will discover devices and create child apps automatically
 *  - Test: Use Thermostat Scheduler to change setpoint, verify gateway receives command
 *  - Test: Change thermostat mode via Hubitat dashboard, verify state updates
 */

#include Salus_for_Hubitat.SalusCommon

metadata {
    definition (
        name: "Salus Thermostat",
        namespace: "Salus_for_Hubitat",
        author: "rmsppu@github.com",
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
    attribute "thermostatMode", "ENUM", THERMOSTAT_MODES
    attribute "thermostatFanMode", "ENUM", THERMOSTAT_FAN_MODES
    attribute "thermostatOperation", "STRING"
    attribute "thermostatOperatingState", "STRING"  // Standard attribute for Thermostat Scheduler
    attribute "supportedThermostatModes", "LIST", THERMOSTAT_MODES
    attribute "supportedThermostatFanModes", "LIST", THERMOSTAT_FAN_MODES
    attribute "availableThermostatPresets", "LIST", THERMOSTAT_PRESETS
    attribute "presetMode", "ENUM", THERMOSTAT_PRESETS
    attribute "isLocked", "BOOL"
    attribute "gatewayStatus", "STRING"
    
    // Commands
    command "setTemperature", "NUMBER"
    command "setHeatingSetpoint", "NUMBER"
    command "setCoolingSetpoint", "NUMBER"
    command "setThermostatMode", "ENUM", THERMOSTAT_MODES
    command "setThermostatFanMode", "ENUM", THERMOSTAT_FAN_MODES
    command "setPresetMode", "ENUM", THERMOSTAT_PRESETS
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
        state.thermostatOperatingState = "idle"  // Standard thermostat operating state
        
        // Set initial attribute values
        setThermostatAttributes()
    }
}

def cleanup() {
    log.debug "Cleaning up Salus Thermostat device"
    // Any cleanup needed
}

/**
 * Set the heating setpoint (also used as general temperature setpoint)
 */
def setTemperature(Number value) {
    log.debug "setTemperature received: ${value}"
    state.heatingSetpoint = value
    setAttribute("heatingSetpoint", value)
    setAttribute("thermostatSetpoint", value)  // Alias for compatibility
    createEvent(name: "thermostatSetpoint", value: value, 
            descriptionText: "Heating setpoint changed to ${value}°C",
            isStateChange: true)
}

/**
 * Set the heating setpoint
 */
def setHeatingSetpoint(Number value) {
    log.debug "setHeatingSetpoint received: ${value}"
    state.heatingSetpoint = value
    setAttribute("heatingSetpoint", value)
    setAttribute("thermostatSetpoint", value)  // Alias for compatibility
    createEvent(name: "thermostatSetpoint", value: value, 
            descriptionText: "Heating setpoint changed to ${value}°C",
            isStateChange: true)
}

/**
 * Set cooling setpoint (not supported for radiator heating, but accepted for compatibility)
 */
def setCoolingSetpoint(Number value) {
    log.warn "Cooling setpoint not supported for radiator heating"
    state.coolingSetpoint = value
    setAttribute("coolingSetpoint", value)
    createEvent(name: "thermostatCoolingSetpoint", value: value, 
            descriptionText: "Cooling setpoint changed to ${value}°C",
            isStateChange: true)
}

/**
 * Set thermostat mode
 */
def setThermostatMode(String mode) {
    log.debug "setThermostatMode received: ${mode}"
    if (THERMOSTAT_MODES.contains(mode)) {
        state.thermostatMode = mode
        setAttribute("thermostatMode", mode)
        // Update operating state based on mode
        state.thermostatOperatingState = (mode == "off") ? "idle" : "heating"
        setAttribute("thermostatOperatingState", state.thermostatOperatingState)
        createEvent(name: "thermostatMode", value: mode, 
                descriptionText: "Thermostat mode changed to ${mode}",
                isStateChange: true)
    } else {
        log.warn "Unsupported thermostat mode: ${mode}"
    }
}

/**
 * Set thermostat fan mode (fixed for radiator systems, but accepted for compatibility)
 */
def setThermostatFanMode(String mode) {
    log.debug "setThermostatFanMode received: ${mode}"
    if (THERMOSTAT_FAN_MODES.contains(mode)) {
        state.thermostatFanMode = mode
        setAttribute("thermostatFanMode", mode)
        createEvent(name: "thermostatFanMode", value: mode, 
                descriptionText: "Thermostat fan mode changed to ${mode}",
                isStateChange: true)
    } else {
        log.warn "Unsupported thermostat fan mode: ${mode}"
    }
}

/**
 * Set preset mode
 */
def setPresetMode(String preset) {
    log.debug "setPresetMode received: ${preset}"
    if (THERMOSTAT_PRESETS.contains(preset)) {
        state.presetMode = preset
        setAttribute("presetMode", preset)
        createEvent(name: "thermostatPresetMode", value: preset, 
                descriptionText: "Thermostat preset changed to ${preset}",
                isStateChange: true)
    } else {
        log.warn "Unsupported thermostat preset: ${preset}"
    }
}

/**
 * Set thermostat lock
 */
def setThermostatLock(Boolean locked) {
    log.debug "setThermostatLock received: ${locked}"
    state.isLocked = locked
    setAttribute("isLocked", locked)
    createEvent(name: "thermostatLock", value: locked, 
            descriptionText: "Thermostat lock changed to ${locked}",
            isStateChange: true)
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
    setAttribute("supportedThermostatModes", THERMOSTAT_MODES)
    setAttribute("supportedThermostatFanModes", THERMOSTAT_FAN_MODES)
    setAttribute("availableThermostatPresets", THERMOSTAT_PRESETS)
    setAttribute("gatewayStatus", state.gatewayStatus ?: "unknown")
    setAttribute("thermostatOperatingState", state.thermostatOperatingState ?: "idle")
    setAttribute("thermostatSetpoint", state.heatingSetpoint)  // Alias for compatibility
    
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
            case "thermostatOperatingState":
                if (state.thermostatOperatingState != value) {
                    state.thermostatOperatingState = value as String
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
