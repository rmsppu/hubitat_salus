/**
 *  Salus Gateway Connector - Parent Application
 *  
 *  Connects to Salus Universal Gateway (UG) 600 and manages child devices
 *  for thermostats, relay zones, and other Salus devices.
 *  
 *  Based on Hubitat integration patterns from:
 *  - Home Assistant Salus iT600 Integration (https://github.com/custom-components/salus)
 *  - CoCoHue - Hue Bridge Integration for Hubitat (https://github.com/HubitatCommunity/CoCoHue)
 *  - BlubButtons Hubitat Integration
 *  - Kasa Hubitat Integration
 *  
 *  Author: Salus Hubitat Integration Team
 *  Based on original work by: Home Assistant Salus iT600 contributors
 */

import groovy.transform.Field
import hubitat.scheduling.AsyncResponse
import com.hubitat.app.DeviceWrapper
// Import common library
import libs.SalusCommon

// Configuration constants
@Field static final Integer POLL_INTERVAL_MINUTES = 5
@Field static final Integer CONNECTION_TIMEOUT_SECONDS = 10
@Field static final Integer MAX_RETRY_ATTEMPTS = 3

definition (
    name: "Salus Gateway Connector",
    namespace: "hubitat",
    author: "Salus Hubitat Integration",
    description: "Connects to Salus Universal Gateway to manage thermostats and relay zones",
    category: "Convenience",
    menu: "Integrations",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: ""
)

preferences {
    page name: "pageMain"
}

void installed() {
    log.debug "Installed Salus Gateway Connector"
    initialize()
}

void updated() {
    log.debug "Updated Salus Gateway Connector"
    initialize()
}

void deleted() {
    log.debug "Deleted Salus Gateway Connector"
    cleanup()
}

/**
 * Initialize the gateway connection and start polling
 */
void initialize() {
    log.debug "Initializing Salus Gateway Connector"
    
    // Cancel any existing scheduled tasks
    unschedule()
    
    // Initialize state if needed
    if (!state.gatewayIP) {
        state.gatewayIP = ""
    }
    if (!state.euidToken) {
        state.euidToken = ""
    }
    if (!state.pollInterval) {
        state.pollInterval = POLL_INTERVAL_MINUTES
    }
    if (!state.deviceMap) {
        state.deviceMap = [:] // Map of deviceId -> childAppId
    }
    if (!state.gatewayStatus) {
        state.gatewayStatus = "disconnected"
    }
    
    // Validate configuration
    if (isConfigured()) {
        connectToGateway()
        scheduleStatusPolling()
    } else {
        log.warn "Salus gateway not configured. Please configure IP address and EUID token."
        setGatewayStatus("not configured")
    }
}

/**
 * Clean up resources when app is deleted
 */
void cleanup() {
    log.debug "Cleaning up Salus Gateway Connector"
    unschedule()
    disconnectFromGateway()
}

/**
 * Check if the gateway is properly configured
 */
Boolean isConfigured() {
    return state.gatewayIP && state.gatewayIP.trim() != "" && 
           state.euidToken && state.euidToken.trim() != ""
}

/**
 * Page configuration
 */
def pageMain() {
    dynamicPage(name: "pageMain", uninstall: true, install: true) {
        section("Salus Gateway Configuration") {
            input name: "gatewayIP", type: "string", title: "Gateway IP Address", 
                  description: "Enter the IP address of your Salus UG600 gateway", 
                  required: true, defaultValue: state.gatewayIP ?: ""
            input name: "euidToken", type: "string", title: "EUID Token", 
                  description: "Enter the EUID token for your Salus gateway", 
                  required: true, defaultValue: state.euidToken ?: "", 
                  secure: true
            input name: "pollInterval", type: "enum", title: "Polling Interval (minutes)", 
                  options: [1: "1 minute", 2: "2 minutes", 5: "5 minutes (default)", 
                           10: "10 minutes", 15: "15 minutes", 30: "30 minutes", 60: "60 minutes"],
                  defaultValue: state.pollInterval ?: POLL_INTERVAL_MINUTES, 
                  required: true
        }
        section("Gateway Status") {
            paragraph "Status: ${state.gatewayStatus ?: 'unknown'}"
            if (state.gatewayStatus == "connected") {
                paragraph "Last updated: ${state.lastUpdate ?: 'never'}"
            }
            input name: "btnRefreshStatus", type: "button", title: "Refresh Status Now"
        }
        section("Managed Devices") {
            paragraph "Number of devices managed: ${state.deviceMap?.size() ?: 0}"
            if (state.deviceMap && state.deviceMap.size() > 0) {
                paragraph "Device IDs: ${state.deviceMap.keySet().join(', ')}"
            }
        }
    }
}

/**
 * Handle preference updates
 */
void updated() {
    log.debug "Updated preferences: ${settings}"
    
    // Update state from settings
    state.gatewayIP = settings.gatewayIP?.trim()
    state.euidToken = settings.euidToken?.trim()
    state.pollInterval = (settings.pollInterval instanceof Integer) ? 
                         settings.pollInterval : Integer.parseInt(settings.pollInterval ?: "${POLL_INTERVAL_MINUTES}")
    
    // Reinitialize if configuration changed
    initialize()
}

/**
 * Button handler for manual refresh
 */
void refreshStatus() {
    log.debug "Manual refresh requested"
    if (isConfigured()) {
        pollGatewayStatus()
    }
}

/**
 * Connect to the Salus gateway
 */
void connectToGateway() {
    if (!isConfigured()) {
        log.warn "Cannot connect: gateway not configured"
        setGatewayStatus("not configured")
        return false
    }
    
    log.debug "Attempting to connect to Salus gateway at ${state.gatewayIP}"
    setGatewayStatus("connecting...")
    
    // In a real implementation, we would establish the actual connection here
    // For now, we'll simulate by setting status and attempting a poll
    try {
        // Test connection by trying to poll status
        pollGatewayStatus()
        setGatewayStatus("connected")
        return true
    } catch (Exception e) {
        log.error "Failed to connect to Salus gateway: ${e}"
        setGatewayStatus("connection failed")
        return false
    }
}

/**
 * Disconnect from the Salus gateway
 */
void disconnectFromGateway() {
    log.debug "Disconnecting from Salus gateway"
    setGatewayStatus("disconnected")
    // In a real implementation, we would close the actual connection here
}

/**
 * Set the gateway connection status
 */
void setGatewayStatus(String status) {
    state.gatewayIP = state.gatewayIP ?: ""  // Ensure not null
    state.gatewayStatus = status
    state.lastUpdate = (status == "connected") ? new Date().format("yyyy-MM-dd HH:mm:ss") : null
    
    // Update any child devices that might be monitoring gateway status
    updateChildDevicesGatewayStatus()
}

/**
 * Poll the gateway for status updates
 */
void scheduleStatusPolling() {
    log.debug "Scheduling status polling every ${state.pollInterval} minutes"
    
    // Cancel existing schedule
    unschedule("pollGatewayStatus")
    
    // Schedule new polling
    if (state.pollInterval > 0) {
        schedule("*/${state.pollInterval} * * * ? * * *", "pollGatewayStatus")
        log.debug "Status polling scheduled"
    }
}

/**
 * Poll the gateway for current status and update devices
 */
void pollGatewayStatus() {
    if (!isConfigured()) {
        log.debug "Skip polling: gateway not configured")
        setGatewayStatus("not configured")
        return
    }
    
    log.debug "Polling Salus gateway for status updates"
    setGatewayStatus("polling...")
    
    try {
        // In a real implementation, we would call the actual gateway API here
        // For now, we'll simulate the polling process
        
        // Simulate getting device data from gateway
        Map<String, Object> gatewayData = fetchGatewayData()
        
        if (gatewayData) {
            // Process the gateway data and update/create child devices
            processGatewayData(gatewayData)
            setGatewayStatus("connected")
        } else {
            log.warn "No data received from gateway")
            setGatewayStatus("no data")
        }
    } catch (Exception e) {
        log.error "Error polling gateway: ${e}"
        setGatewayStatus("polling failed")
        
        // Attempt to reconnect on failure
        retryConnection()
    }
}

/**
 * Fetch data from the Salus gateway API
 * 
 * In a real implementation, this would make HTTP calls to the gateway
 * Based on the salus_it600 Python library and Home Assistant implementation
 */
    log.debug "Processing gateway data: ${gatewayData}"
/**
 * Fetch data from the Salus gateway API
 * Makes HTTP GET requests to the gateway to retrieve device status
 * Based on common patterns from IoT device APIs and the salus_it600 library
 *
 * @return Map containing device data organized by type, or null on failure
 */
Map<String, Object> fetchGatewayData() {
    if (!isConfigured()) {
        log.debug "Cannot fetch gateway data: not configured"
        return null
    }
    
    log.debug "Fetching gateway data from http://${state.gatewayIP}:80/api/status"
    
    try {
        // Note: This is a placeholder implementation that needs to be adapted to the actual Salus gateway API
        // The actual endpoints and data format should be determined by:
        // 1. Consulting the Salus gateway documentation
        // 2. Analyzing network traffic from the official Salus app
        // 3. Referring to the salus_it600 Python library source if available
        
        // For now, we return simulated data to demonstrate the structure
        // In a production implementation, this would make actual HTTP calls
        
        log.warn "Using simulated gateway data - replace with actual HTTP calls to Salus gateway API"
        
        // Return mock data structure for development/testing
        // THIS SHOULD BE REPLACED WITH ACTUAL HTTP CALLS IN PRODUCTION
        return [
            climate: [
                "thermostat_1": [
                    id: "thermostat_1",
                    name: "Living Room Thermostat",
                    model: "AWRT10RF",
                    currentTemperature: 21.5,
                    targetTemperature: 22.0,
                    presetMode: "follow_schedule",
                    availablePresets: ["follow_schedule", "permanent_hold", "temporary_hold", "standby", "away"],
                    hvacMode: "heat",
                    hvacAction: "idle",
                    isLocked: false
                ],
                "thermostat_2": [
                    id: "thermostat_2", 
                    name: "Bedroom Thermostat",
                    model: "AS20WRF",
                    currentTemperature: 20.0,
                    targetTemperature: 20.5,
                    presetMode: "follow_schedule",
                    availablePresets: ["follow_schedule", "permanent_hold", "temporary_hold", "standby", "away"],
                    hvacMode: "heat",
                    hvacAction: "heating",
                    isLocked: true
                ]
            ],
            switch: [
                "relay_zone_1": [
                    id: "relay_zone_1",
                    name: "Zone 1 Pump",
                    model: "AKL04P",
                    state: "on"
                ],
                "relay_zone_2": [
                    id: "relay_zone_2",
                    name: "Zone 2 Pump", 
                    model: "AKL04P",
                    state: "off"
                ],
                "relay_zone_3": [
                    id: "relay_zone_3",
                    name: "Zone 3 Pump",
                    model: "AKL04P", 
                    state: "on"
                ],
                "relay_zone_4": [
                    id: "relay_zone_4",
                    name: "Zone 4 Pump",
                    model: "AKL04P",
                    state: "off"
                ]
            ]
            // Additional device types would go here (binary_sensor, cover, sensor, lock)
        ]
    } catch (Exception e) {
        log.error "Error in fetchGatewayData: ${e}"
        return null
    }
}
    
    // Process climate devices (thermostats)
    if (gatewayData.climate) {
        processClimateDevices(gatewayData.climate)
    }
    
    // Process switch devices (relay zones)
    if (gatewayData.switch) {
        processSwitchDevices(gatewayData.switch)
    }
    
    // TODO: Process other device types (binary_sensor, cover, sensor, lock)
    
    // Remove devices that are no longer present
    cleanupRemovedDevices(gatewayData)
}

/**
 * Process climate devices (thermostats)
 */
void processClimateDevices(Map<String, Object> climateDevices) {
    log.debug "Processing ${climateDevices.size()} climate devices"
    
    climateDevices.each { deviceId, deviceData ->
        log.debug "Processing climate device: ${deviceId} - ${deviceData.name}"
        
        // Check if we already have a child app for this device
        if (state.deviceMap && state.deviceMap.containsKey(deviceId)) {
            // Update existing child app
            updateClimateChildDevice(deviceId, deviceData)
        } else {
            // Create new child app for this device
            createClimateChildDevice(deviceId, deviceData)
        }
    }
}

/**
 * Process switch devices (relay zones)
 */
void processSwitchDevices(Map<String, Object> switchDevices) {
    log.debug "Processing ${switchDevices.size()} switch devices"
    
    switchDevices.each { deviceId, deviceData ->
        log.debug "Processing switch device: ${deviceId} - ${deviceData.name} (${deviceData.state})"
        
        // Check if we already have a child app for this device
        if (state.deviceMap && state.deviceMap.containsKey(deviceId)) {
            // Update existing child app
            updateSwitchChildDevice(deviceId, deviceData)
        } else {
            // Create new child app for this device
            createSwitchChildDevice(deviceId, deviceData)
        }
    }
}

/**
 * Create a new child application for a climate device (thermostat)
 */
void createClimateChildDevice(String deviceId, Map<String, Object> deviceData) {
    log.debug "Creating climate child device for ${deviceId}"
    
    try {
        // Create child application instance
        String childAppId = createChildApp(
                "SalusThermostatController",  // child app name
                [
                    label: "${deviceData.name} Thermostat",
                    description: "Controls Salus thermostat ${deviceData.name}",
                    installOnOpen: true
                ]
        )
        
        if (childAppId) {
            // Store the mapping
            if (!state.deviceMap) {
                state.deviceMap = [:]
            }
            state.deviceMap[deviceId] = childAppId
            
            // Configure the child app with device information
            configureClimateChildDevice(childAppId, deviceId, deviceData)
            
            log.debug "Successfully created climate child device ${deviceId} with app ID ${childAppId}"
        } else {
            log.error "Failed to create climate child device for ${deviceId}"
        }
    } catch (Exception e) {
        log.error "Error creating climate child device for ${deviceId}: ${e}"
    }
}

/**
 * Create a new child application for a switch device (relay zone)
 */
void createSwitchChildDevice(String deviceId, Map<String, Object> deviceData) {
    log.debug "Creating switch child device for ${deviceId}"
    
    try {
        // Create child application instance
        String childAppId = createChildApp(
                "SalusSwitchController",  // child app name
                [
                    label: "${deviceData.name} Switch",
                    description: "Controls Salus switch ${deviceData.name}",
                    installOnOpen: true
                ]
        )
        
        if (childAppId) {
            // Store the mapping
            if (!state.deviceMap) {
                state.deviceMap = [:]
            }
            state.deviceMap[deviceId] = childAppId
            
            // Configure the child app with device information
            configureSwitchChildDevice(childAppId, deviceId, deviceData)
            
            log.debug "Successfully created switch child device ${deviceId} with app ID ${childAppId}"
        } else {
            log.error "Failed to create switch child device for ${deviceId}"
        }
    } catch (Exception e) {
        log.error "Error creating switch child device for ${deviceId}: ${e}"
    }
}

/**
 * Configure a climate child device with device information
 */
void configureClimateChildDevice(String childAppId, String deviceId, Map<String, Object> deviceData) {
    log.debug "Configuring climate child device ${childAppId} for device ${deviceId}"
    
    // Get the child application wrapper
    DeviceWrapper childApp = getChildDevice(childAppId)
    
    if (childApp) {
        // Pass device information to the child app via settings or data
        childApp.updateSetting("deviceId", deviceId)
        childApp.updateSetting("deviceName", deviceData.name ?: "")
        childApp.updateSetting("deviceModel", deviceData.model ?: "")
        childApp.updateSetting("parentAppId", device.id)  // Our own app ID
        
        // Initialize the child app's device state
        initializeClimateDeviceState(childAppId, deviceData)
    } else {
        log.warn "Could not find child app ${childAppId} to configure"
    }
}

/**
 * Configure a switch child device with device information
 */
void configureSwitchChildDevice(String childAppId, String deviceId, Map<String, Object> deviceData) {
    log.debug "Configuring switch child device ${childAppId} for device ${deviceId}"
    
    // Get the child application wrapper
    DeviceWrapper childApp = getChildDevice(childAppId)
    
    if (childApp) {
        // Pass device information to the child app via settings or data
        childApp.updateSetting("deviceId", deviceId)
        childApp.updateSetting("deviceName", deviceData.name ?: "")
        childApp.updateSetting("deviceModel", deviceData.model ?: "")
        childApp.updateSetting("parentAppId", device.id)  // Our own app ID
        
        // Initialize the child app's device state
        initializeSwitchDeviceState(childAppId, deviceData)
    } else {
        log.warn "Could not find child app ${childAppId} to configure"
    }
}

/**
 * Update an existing climate child device with new data
 */
void updateClimateChildDevice(String deviceId, Map<String, Object> deviceData) {
    log.debug "Updating climate child device for ${deviceId}"
    
    String childAppId = state.deviceMap ? state.deviceMap.get(deviceId) : null
    if (childAppId) {
        // Update the child app's device state
        updateClimateDeviceState(childAppId, deviceData)
    } else {
        log.warn "No child app found for climate device ${deviceId}"
    }
}

/**
 * Update an existing switch child device with new data
 */
void updateSwitchChildDevice(String deviceId, Map<String, Object> deviceData) {
    log.debug "Updating switch child device for ${deviceId}"
    
    String childAppId = state.deviceMap ? state.deviceMap.get(deviceId) : null
    if (childAppId) {
        // Update the child app's device state
        updateSwitchDeviceState(childAppId, deviceData)
    } else {
        log.warn "No child app found for switch device ${deviceId}"
    }
}

/**
 * Initialize the state of a climate device in its child app
 */
void initializeClimateDeviceState(String childAppId, Map<String, Object> deviceData) {
    log.debug "Initializing climate device state for ${childAppId}"
    
    DeviceWrapper childApp = getChildDevice(childAppId)
    if (childApp) {
        // Send initial state to the child app
        childApp.setDeviceState(
                "temperature": deviceData.currentTemperature ?: 0,
                "heatingSetpoint": deviceData.targetTemperature ?: 0,
                "presetMode": deviceData.presetMode ?: "",
                "hvacMode": deviceData.hvacMode ?: "",
                "hvacAction": deviceData.hvacAction ?: "",
                "isLocked": deviceData.isLocked ?: false,
                "availablePresets": deviceData.availablePresets ?: []
        )
    }
}

/**
 * Initialize the state of a switch device in its child app
 */
void initializeSwitchDeviceState(String childAppId, Map<String, Object> deviceData) {
    log.debug "Initializing switch device state for ${childAppId}"
    
    DeviceWrapper childApp = getChildDevice(childAppId)
    if (childApp) {
        // Send initial state to the child app
        childApp.setDeviceState(
                "switch": deviceData.state ?: "off",
                "deviceName": deviceData.name ?: "",
                "deviceModel": deviceData.model ?: ""
        )
    }
}

/**
 * Update the state of a climate device in its child app
 */
void updateClimateDeviceState(String childAppId, Map<String, Object> deviceData) {
    log.debug "Updating climate device state for ${childAppId}"
    
    DeviceWrapper childApp = getChildDevice(childAppId)
    if (childApp) {
        // Send updated state to the child app
        childApp.setDeviceState(
                "temperature": deviceData.currentTemperature ?: 0,
                "heatingSetpoint": deviceData.targetTemperature ?: 0,
                "presetMode": deviceData.presetMode ?: "",
                "hvacMode": deviceData.hvacMode ?: "",
                "hvacAction": deviceData.hvacAction ?: "",
                "isLocked": deviceData.isLocked ?: false
        )
    }
}

/**
 * Update the state of a switch device in its child app
 */
void updateSwitchDeviceState(String childAppId, Map<String, Object> deviceData) {
    log.debug "Updating switch device state for ${childAppId}"
    
    DeviceWrapper childApp = getChildDevice(childAppId)
    if (childApp) {
        // Send updated state to the child app
        childApp.setDeviceState(
                "switch": deviceData.state ?: "off"
        )
    }
}

/**
 * Remove child apps for devices that are no longer present
 */
void cleanupRemovedDevices(Map<String, Object> gatewayData) {
    log.debug "Checking for removed devices"
    
    // Get current device IDs from gateway data
    Set<String> currentDeviceIds = new HashSet()
    
    if (gatewayData.climate) {
        currentDeviceIds.addAll(gatewayData.climate.keySet())
    }
    if (gatewayData.switch) {
        currentDeviceIds.addAll(gatewayData.switch.keySet())
    }
    // Add other device types as needed
    
    // Check our tracked devices
    if (state.deviceMap) {
        state.deviceMap.findAll { deviceId, childAppId ->
            !currentDeviceIds.contains(deviceId)
        }.each { deviceId, childAppId ->
            log.debug "Removing child app for removed device: ${deviceId} (${childAppId})"
            removeChildDevice(deviceId, childAppId)
        }
    }
}

/**
 * Remove a child device and clean up its mapping
 */
void removeChildDevice(String deviceId, String childAppId) {
    log.debug "Removing child device ${deviceId} with app ID ${childAppId}"
    
    try {
        // Delete the child application
        deleteChildDevice(childAppId)
        
        // Remove from our mapping
        if (state.deviceMap) {
            state.deviceMap.remove(deviceId)
        }
        
        log.debug "Successfully removed child device ${deviceId}"
    } catch (Exception e) {
        log.error "Error removing child device ${deviceId}: ${e}"
    }
}

/**
 * Handle commands from child applications to send to the gateway
 * 
 * This method would be called by child apps when they need to send commands
 * to the Salus gateway (e.g., set temperature, turn on switch)
 * Based on patterns from the salus_it600 Python library
 */
def sendCommandToGateway(String deviceId, String command, Map<String, Object> params) {
    log.debug "Received command from child app: deviceId=${deviceId}, command=${command}, params=${params}"
    
    if (!isConfigured()) {
        log.warn "Cannot send command: gateway not configured"
        return [success: false, error: "Gateway not configured"]
    }
    
    try {
        // In a real implementation, we would call the appropriate gateway API method here
        Boolean result = executeGatewayCommand(deviceId, command, params)
        
        if (result) {
            log.debug "Successfully executed command ${command} on device ${deviceId}"
            // Optionally trigger an immediate poll to get updated status
            // pollGatewayStatus()
            return [success: true]
        } else {
            log.warn "Failed to execute command ${command} on device ${deviceId}"
            return [success: false, error: "Command failed"]
        }
    } catch (Exception e) {
        log.error "Error executing command ${command} on device ${deviceId}: ${e}"
        return [success: false, error: "${e}"]
    }
}

/**
 * Execute a command on the Salus gateway
 * 
 * In a real implementation, this would make HTTP calls to the gateway API
 * Based on the salus_it600 Python library implementation
 */
Boolean executeGatewayCommand(String deviceId, String command, Map<String, Object> params) {
    log.debug "Executing gateway command: deviceId=${deviceId}, command=${command}, params=${params}"
    
    // TODO: Implement actual HTTP communication with Salus gateway
    // This would involve:
    // 1. Making HTTP PUT/POST requests to the gateway API endpoints
    // 2. Handling the response
    // 3. Returning success/failure
    
    // For now, simulate success
    log.debug "Simulating successful execution of ${command} on ${deviceId}"
    return true
}

/**
 * Attempt to reconnect to the gateway after a failure
 */
void retryConnection() {
    if (!state.retryCount) {
        state.retryCount = 0
    }
    
    if (state.retryCount < MAX_RETRY_ATTEMPTS) {
        state.retryCount++
        log.debug "Attempting to reconnect to gateway (attempt ${state.retryCount}/${MAX_RETRY_ATTEMPTS})"
        
        // Wait increasing amounts of time between retries
        Integer delaySeconds = state.retryCount * CONNECTION_TIMEOUT_SECONDS
        runIn(delaySeconds, "attemptReconnection")
    } else {
        log.error "Max retry attempts reached. Giving up on gateway connection."
        setGatewayStatus("connection failed")
        state.retryCount = 0  // Reset for next attempt
    }
}

/**
 * Attempt to reconnect to the gateway
 */
void attemptReconnection() {
    log.debug "Attempting gateway reconnection"
    if (connectToGateway()) {
        state.retryCount = 0  // Reset retry count on success
        scheduleStatusPolling()  // Resume polling
    } else {
        retryConnection()  // Try again
    }
}

/**
 * Update gateway status for all child devices
 */
void updateChildDevicesGatewayStatus() {
    log.debug "Updating gateway status for child devices"
    
    if (state.deviceMap) {
        state.deviceMap.each { deviceId, childAppId ->
            DeviceWrapper childApp = getChildDevice(childAppId)
            if (childApp) {
                childApp.setDeviceState("gatewayStatus": state.gatewayStatus)
            }
        }
    }
}
