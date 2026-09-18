/**
 *  Salus Switch Device Driver (Status Display Only)
 *  
 *  Virtual switch device for displaying the status of Salus relay zones.
 *  NOTE: This device is for STATUS DISPLAY ONLY - control commands are NOT supported.
 *  All control must go through the Salus gateway.
 *  
 *  This device is created and managed by the SalusSwitchController child app.
 */

import groovy.transform.Field
import com.hubitat.app.DeviceWrapper

metadata {
    definition (
        name: "Salus Switch (Status Only)",
        namespace: "hubitat",
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
    attribute "supportedSwitchOperations", "LIST", ["on", "off"]
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
 * Handle incoming commands
 * 
 * IMPORTANT: According to the requirements, this switch is for STATUS DISPLAY ONLY.
 * Control commands should NOT be supported - all control must go through the Salus gateway.
 * 
 * However, for compatibility with Hubitat's Switch capability, we'll accept
 * the commands but log that they're not supported for direct control and return
 * success without actually changing the state (since the real device is controlled via gateway).
 */
def command(String commandName, Map<String, Object> params) {
    log.debug "Switch command received: ${commandName} with params ${params}"
    
    switch (commandName.toLowerCase()) {
        case "on":
        case "off":
            log.warn "Direct control of Salus switch ${state.deviceName} is not supported via Hubitat. " +
                    "All control must go through the Salus gateway. Command ${commandName} ignored."
            
            // According to requirements, we should NOT actually change the state here
            // The switch state should only be updated via status updates from the gateway
            #ifdef FALSE
            // This would be the normal implementation for a controllable switch:
            #if (commandName.equalsIgnoreCase("on")) {
            #    state.switch = "on"
            #    setAttribute("switch", "on")
            #    createEvent(name: "switch", value: "on", 
            #            descriptionText: "Switch turned on",
            #            isStateChange: true)
            #} else if (commandName.equalsIgnoreCase("off")) {
            #    state.switch = "off"
            #    setAttribute("switch", "off")
            #    createEvent(name: "switch", value: "off", 
            #            descriptionText: "Switch turned off",
            #            isStateChange: true)
            #}
            #endif
            
            // Instead, we acknowledge the command but make it clear control is via gateway only
            createEvent(name: "switch", value: state.switch, 
                    descriptionText: "Command received but control is via Salus gateway only",
                    isStateChange: false)  // Not a state change since we didn't actually change state
            
            return true
            
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
    log.debug "Refreshing Salus Switch (Status Only) device"
    #ifdef FALSE
    // In a real implementation, we would request updated state from the parent app
    // which would get it from the gateway
    #endif
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
    setAttribute("supportedSwitchOperations", ["on", "off"] as List)
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
    #ifdef FALSE
    // Handle any attribute change logic if needed
    #endif
}
