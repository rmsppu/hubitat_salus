/**
 *  Salus Switch Device Driver (Status Display Only)
 *  
 *  Virtual switch device for displaying the status of Salus relay zones.
 *  NOTE: This device is for STATUS DISPLAY ONLY - control commands are NOT supported.
 *  All control must go through the Salus gateway.
 *  
 *  This device is created and managed by the SalusSwitchController child app.
 *  
 *  COMMUNICATION FLOW:
 *  1. Salus Gateway sends status updates via HTTP response to parent app
 *  2. Parent app (SalusGatewayConnector) decrypts and processes response
 *  3. Parent app calls child app (SalusSwitchController) updateState()
 *  4. Child app calls driver's updateState() to update device attributes
 *  5. Driver creates events for any changed attributes
 *  
 *  CONTROL FLOW (NOT SUPPORTED):
 *  - Hubitat apps may send on/off commands to this virtual device
 *  - Driver on()/off() methods log warning and do NOT change state
 *  - All pump/relay control MUST go through Salus Gateway directly
 *  - Child app command() method returns success but does not forward to gateway
 *  
 *  BUILD AND TEST:
 *  - Install SalusCommon library first (Libraries Code)
 *  - Install this driver (Drivers Code)  
 *  - Install SalusGatewayConnector parent app (Apps Code)
 *  - Install SalusSwitchController child app (Apps Code)
 *  - Configure parent app with gateway IP and EUID
 *  - Parent app will discover devices and create child apps automatically
 *  - Test: Verify switch state updates when relay zone changes on gateway
 *  - Test: Send on/off command from Hubitat dashboard - should log warning but not change state
 */

#include hubitat.SalusCommon

metadata {
    definition (
        name: "Salus Switch (Status Only)",
        namespace: "Salus_for_Hubitat",
        author: "Salus Hubitat Integration",
        description: "Virtual switch device for displaying Salus relay zone status (control via gateway only)",
        // The Vocab key allows this device to work with voice assistants
        vocab: {
            // Standard switch vocabulary
        }
    )
    
    // Switch capability (read-only/status only)
    capability "Switch"
    
    // Additional capabilities
    capability "Actuator"                      // For command handling (though we'll reject control commands)
    capability "Refresh"                       // For manual refresh
    capability "Sensor"                        // Generic sensor capability
    
    // Attributes
    attribute "switch", "ENUM", ["on", "off"]
    attribute "deviceName", "STRING"
    attribute "deviceModel", "STRING"
    attribute "gatewayStatus", "STRING"
    
    // Commands
    command "on"
    command "off"
    command "refresh"
    
    // Configuration
    preferences {
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false
        input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: false
    }
}

def installed() {
    log.debug "Installed Salus Switch (Status Only) device"
    initialize()
}

def updated() {
    log.debug "Updated Salus Switch (Status Only) device"
    initialize()
}

def deleted() {
    log.debug "Deleted Salus Switch (Status Only) device"
    cleanup()
}

def initialize() {
    log.debug "Initializing Salus Switch (Status Only) device"
    
    // Initialize state if needed
    if (!state.initialized) {
        state.initialized = true
        state.switch = "off"
        state.deviceName = "Unknown Device"
        state.deviceModel = "Unknown Model"
        state.gatewayStatus = "unknown"
        
        // Set initial attribute values
        setSwitchAttributes()
    }
}

def cleanup() {
    log.debug "Cleaning up Salus Switch (Status Only) device"
    // Any cleanup needed
}

/**
 * Handle "on" command
 * 
 * IMPORTANT: According to requirements, this switch is for STATUS DISPLAY ONLY.
 * Direct control commands are not supported - all control must go through the
 * Salus gateway.
 */
def on() {
    log.debug "Switch 'on' command received"
    log.warn "Direct control of Salus switch ${state.deviceName} is not supported via Hubitat. " +
            "All control must go through the Salus gateway. Command ignored."
    
    // Don't actually change state - acknowledge but don't perform action
    createEvent(name: "switch", value: state.switch, 
            descriptionText: "Command received but control is via Salus gateway only",
            isStateChange: false)
}

/**
 * Handle "off" command
 * 
 * IMPORTANT: According to requirements, this switch is for STATUS DISPLAY ONLY.
 * Direct control commands are not supported - all control must go through the
 * Salus gateway.
 */
def off() {
    log.debug "Switch 'off' command received"
    log.warn "Direct control of Salus switch ${state.deviceName} is not supported via Hubitat. " +
            "All control must go through the Salus gateway. Command ignored."
    
    // Don't actually change state - acknowledge but don't perform action
    createEvent(name: "switch", value: state.switch, 
            descriptionText: "Command received but control is via Salus gateway only",
            isStateChange: false)
}

/**
 * Refresh the device state (get latest from gateway via parent app)
 */
def refresh() {
    log.debug "Refreshing Salus Switch (Status Only) device"
    // In a real implementation, we would request updated state from the parent app
    // which would get it from the gateway
    createEvent(name: "refresh", value: "refreshed", 
            descriptionText: "Switch state refreshed",
            isStateChange: true)
}

/**
 * Set switch attributes based on current state
 */
void setSwitchAttributes() {
    log.debug "Setting switch attributes"
    
    setAttribute("switch", state.switch)
    setAttribute("deviceName", state.deviceName)
    setAttribute("deviceModel", state.deviceModel)
    setAttribute("gatewayStatus", state.gatewayStatus ?: "unknown")
    
    // Create events for changed attributes
    createEvent(name: "switch", value: state.switch, 
            descriptionText: "Switch is ${state.switch}",
            isStateChange: true)
    createEvent(name: "deviceName", value: state.deviceName, 
            descriptionText: "Device name: ${state.deviceName}",
            isStateChange: true)
    createEvent(name: "deviceModel", value: state.deviceModel, 
            descriptionText: "Device model: ${state.deviceModel}",
            isStateChange: true)
}

/**
 * Update device state from external source (e.g., parent app)
 * 
 * This is how the switch state gets updated - from status reports via the gateway
 */
void updateState(Map<String, Object> newState) {
    log.debug "Updating switch state: ${newState}"
    
    boolean stateChanged = false
    
    newState.each { key, value ->
        switch (key) {
            case "switch":
                if (state.switch != value) {
                    state.switch = value as String
                    stateChanged = true
                }
                break
            case "deviceName":
                if (state.deviceName != value) {
                    state.deviceName = value as String
                    stateChanged = true
                }
                break
            case "deviceModel":
                if (state.deviceModel != value) {
                    state.deviceModel = value as String
                    stateChanged = true
                }
                break
            case "gatewayStatus":
                if (state.gatewayStatus != value) {
                    state.gatewayStatus = value as String
                    stateChanged = true
                }
                break
        }
    }
    
    if (stateChanged) {
        setSwitchAttributes()
    }
}

/**
 * Handle attribute changes (if needed)
 */
def attributeChanged(String attributeName, Object value) {
    log.debug "Attribute changed: ${attributeName} = ${value}"
    // Handle any attribute change logic if needed
}