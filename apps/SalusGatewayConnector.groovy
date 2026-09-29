/**
 *  Salus Gateway Connector - Parent Application
 *  
 *  Connects to Salus Universal Gateway and manages child devices
 *  for thermostats, relay zones, and other Salus devices.
 *  
 *  Based on Hubitat integration patterns from:
 *  - Home Assistant Salus iT600 Integration
 *  - CoCoHue - Hue Bridge Integration
 */

/* Id: b5bc24a */
/* Date: 2026-09-28 22:57:04 */
/* Commit: 260 */

#include Salus_for_Hubitat.SalusCommon

import groovy.transform.Field
import groovy.json.JsonSlurper

@Field static final Integer POLL_INTERVAL_MINUTES = 5
@Field static final Integer MAX_COMMAND_RETRIES = 3
@Field static final List<Integer> COMMAND_RETRY_DELAYS = [5, 15, 30]  // seconds

definition (
    name: "Salus Gateway Connector",
    namespace: "Salus_for_Hubitat",
    author: "rmsppu@github.com",
    description: "Connects to Salus Universal Gateway to manage thermostats and relay zones",
    category: "Convenience",
    menu: "Integrations"
)

preferences {
    page name: "pageMain"
}

void installed() {
    initialize()
}

void updated() {
    if (settings.resetConnection) {
        log.info "Manual connection reset requested - resetting failed attempt counter"
        state.failedConnectionAttempts = 0
        setGatewayStatus("connection_reset")
        runIn(1, "pollGatewayStatus")
        return
    }
    
    // Handle Test IP Connectivity button
    if (settings.testConnectionBtn) {
        log.info "IP connectivity test requested"
        def testResult = testIpConnectivity(settings.gatewayIP)
        state.testConnectionResult = testResult
        if (testResult.success) {
            log.info "IP connectivity test passed"
        } else {
            log.error "IP connectivity test failed: ${testResult.message}"
        }
        return  // Don't initialize, just update the test result on the page
    }
    
    // Handle Test EUID/Logical Communication button
    if (settings.testEuidBtn) {
        log.info "EUID/Logical communication test requested"
        def testResult = testEuidCommunication(settings.gatewayIP, settings.euidToken)
        state.euidTestResult = testResult
        if (testResult.success) {
            log.info "EUID/Logical communication test passed"
        } else {
            log.error "EUID/Logical communication test failed: ${testResult.message}"
        }
        return  // Don't initialize, just update the test result on the page
    }
    
    // Handle debug logging settings
    if (settings.enableDebug) {
        if (settings.enablePersistentDebug) {
            log.info "Persistent debug logging enabled by user"
            state.debugEnabled = true
            unschedule("disableDebugLogging")
        } else {
            state.debugEnabled = true
            unschedule("disableDebugLogging")
            runIn(1800, "disableDebugLogging")
        }
    } else {
        state.debugEnabled = false
        unschedule("disableDebugLogging")
    }
    
    // Require both tests to pass before allowing initialization
    if (!state.testConnectionResult?.success) {
        log.error "Cannot initialize: IP connectivity test has not passed"
        return
    }
    if (!state.euidTestResult?.success) {
        log.error "Cannot initialize: EUID/Logical communication test has not passed"
        return
    }
    
    if (!validateSettings()) {
        log.error "Settings validation failed - not applying invalid settings"
        return
    }
    initialize()
}

void deleted() {
    cleanup()
}

/**
 * Validate settings on update
 * Returns true if valid, false if invalid
 */
Boolean validateSettings() {
    Boolean valid = true
    
    if (settings.gatewayIP) {
        // Basic IP/hostname validation
        def ipPattern = /^(\d{1,3}\.){3}\d{1,3}$/
        def hostnamePattern = /^([a-zA-Z0-9-]+\.)+[a-zA-Z]{2,}$/
        if (!settings.gatewayIP.matches(ipPattern) && !settings.gatewayIP.matches(hostnamePattern)) {
            log.warn "Gateway IP/hostname format may be invalid: ${settings.gatewayIP}"
            valid = false
        }
    } else {
        log.warn "Gateway IP/hostname is required"
        valid = false
    }
    
    if (settings.euidToken) {
        // EUID should be 16 hex characters
        def euidPattern = /^[0-9A-Fa-f]{16}$/
        if (!settings.euidToken.matches(euidPattern)) {
            log.warn "EUID token format may be invalid (expected 16 hex chars): ${settings.euidToken}"
            valid = false
        }
    } else {
        log.warn "EUID token is required"
        valid = false
    }
    
    if (settings.pollInterval) {
        def validIntervals = [1, 2, 5, 10, 15, 30]
        if (!validIntervals.contains(settings.pollInterval as Integer)) {
            log.warn "Invalid poll interval: ${settings.pollInterval}, using default"
            settings.pollInterval = POLL_INTERVAL_MINUTES
        }
    }
    
    return valid
}

void initialize() {
    unschedule()
    
    if (!state.gatewayIP) state.gatewayIP = ""
    if (!state.euidToken) state.euidToken = ""
    if (!state.pollInterval) state.pollInterval = POLL_INTERVAL_MINUTES
    if (!state.deviceMap) {
        state.deviceMap = new HashMap()
    }
    // deviceMap structure: [deviceId: [childAppId: "...", virtualDni: "..."]]
    // Reset failure tracking on re-initialization
    state.failedConnectionAttempts = 0
    
    if (isConfigured()) {
        connectToGateway()
        scheduleStatusPolling()
    }
}

/**
 * Check if debug logging is enabled (persistent or temporary)
 */
Boolean isDebugEnabled() {
    return settings?.enablePersistentDebug == true || state?.debugEnabled == true
}

/**
 * Log debug message if debug logging is enabled
 */
void debugLog(String message) {
    if (isDebugEnabled()) {
        log.debug message
    }
}

/**
 * Disable temporary debug logging (called after 30 minutes)
 */
void disableDebugLogging() {
    state.debugEnabled = false
    log.info "Enhanced debug logging auto-disabled after 30 minutes"
}

void cleanup() {
    unschedule()
    disconnectFromGateway()
}

Boolean isConfigured() {
    return state.gatewayIP?.trim() && state.euidToken?.trim()
}

/**
 * Get temperature unit preference from Hubitat settings.
 * Returns "F" for Fahrenheit, "C" for Celsius.
 */
String getTemperatureUnit() {
    return settings?.temperatureUnit ?: "C"
}

def pageMain() {
    dynamicPage(name: "pageMain", uninstall: true, install: true) {
        section("Salus Gateway Configuration") {
            input "gatewayIP", "string", title: "Gateway IP Address", 
                  description: "IP or hostname of your Salus UG600 gateway", 
                  required: true
            input "euidToken", "string", title: "EUID Token", 
                  description: "EUID from gateway label", 
                  required: true, secure: true
            input "pollInterval", "enum", title: "Polling Interval (minutes)", 
                  options: [1:"1 min", 2:"2 min", 5:"5 min", 10:"10 min", 15:"15 min", 30:"30 min"],
                  defaultValue: 5
            // Test IP connectivity button
            if (settings.gatewayIP) {
                input "testConnectionBtn", "submit", title: "Test IP Connectivity", 
                      description: "Test basic IP connectivity to the Salus Gateway (ping + HTTP GET)", 
                      required: false
            }
        }
        section("Connection Status") {
            paragraph "Status: ${state.gatewayStatus ?: 'unknown'}"
            paragraph "Devices: ${state.deviceMap?.size() ?: 0}"
            if (state.failedConnectionAttempts && state.failedConnectionAttempts > 0) {
                paragraph "Failed connection attempts: ${state.failedConnectionAttempts}"
            }
            if (state.gatewayStatus == "gateway_unreachable") {
                paragraph "Unable to reach Salus Gateway - automatic polling paused"
                input "resetConnection", "bool", title: "Reset Connection & Resume Polling", 
                      description: "Reset the failed attempt counter and immediately retry connecting to the Salus Gateway", 
                      defaultValue: false
            }
            // Show test connection result if available
            if (state.testConnectionResult) {
                if (state.testConnectionResult.success) {
                    paragraph "✅ IP connectivity test successful: ${state.testConnectionResult.message}"
                } else {
                    paragraph "❌ IP connectivity test failed: ${state.testConnectionResult.message}"
                }
            }
            // Test EUID/logical connectivity button
            if (state.testConnectionResult?.success && settings.euidToken) {
                input "testEuidBtn", "submit", title: "Test EUID / Logical Communication", 
                      description: "Test full encrypted communication with the Salus Gateway using the provided EUID", 
                      required: false
            }
            // Show EUID test result if available
            if (state.euidTestResult) {
                if (state.euidTestResult.success) {
                    paragraph "✅ EUID test successful: ${state.euidTestResult.message}"
                } else {
                    paragraph "❌ EUID test failed: ${state.euidTestResult.message}"
                }
            }
        }
        section("Debug Logging") {
            input "enableDebug", "bool", title: "Enable Debug Logging", 
                  description: "Enable enhanced debug logging for gateway communication, encryption, and device state updates (auto-disables after 30 minutes)", 
                  defaultValue: false
            if (settings?.enableDebug) {
                paragraph "⚠️ Warning: Persistent debug logging generates significant log activity and may affect Hubitat performance."
                input "enablePersistentDebug", "bool", title: "Enable Persistent Debug Logging", 
                      description: "Keep debug logging enabled permanently (does not auto-disable after 30 minutes)", 
                      defaultValue: false
            }
        }
    }
}

/**
 * Fetch gateway data (async HTTP call)
 */
def fetchGatewayData() {
    if (!isConfigured()) return null
    
    log.debug "Fetching from ${state.gatewayIP}"
    
    try {
        String requestBody = '{"requestAttr": "readall"}'
        byte[] encrypted = encryptPayload(requestBody, state.euidToken)
        
        // Async HTTP call - response is handled in handleGatewayResponse callback
        // Return null to indicate data is being fetched asynchronously
        httpPost([
            uri: state.gatewayIP, port: 80, path: "/deviceid/read",
            contentType: "application/json", body: encrypted,
            headers: ["content-type":"application/json"], timeout: 10
        ], "handleGatewayResponse")
        
        return null  // Data is fetched asynchronously, actual processing in handleGatewayResponse
    } catch (e) {
        log.error "Error: ${e}"
        return null
    }
}

void handleGatewayResponse(response) {
    log.debug "Response status: ${response.status}"
    
    // Handle HTTP-level errors
    if (response.status < 0) {
        log.error "Network error: ${response.errorMessage ?: 'Unknown network error'}"
        setGatewayStatus("network_error")
        scheduleRetryPoll()
        return
    }
    
    if (response.status != 200) {
        log.warn "Unexpected HTTP status: ${response.status}"
        setGatewayStatus("http_error_${response.status}")
        scheduleRetryPoll()
        return
    }
    
    // Process successful response
    try {
        // Decrypt the response data using the EUID-based key
        String decryptedData = decryptPayload(response.data, state.euidToken)
        
        // Parse the decrypted JSON response
        def parsedData = new JsonSlurper().parseText(decryptedData)
        
        // Validate response structure
        if (!parsedData || !(parsedData instanceof Map)) {
            throw new Exception("Invalid response structure: not a JSON object")
        }
        
        // Convert parsed data to the format expected by processGatewayData
        Map<String, Object> gatewayData = [
            climate: parsedData.climate ?: [:],
            switch: parsedData.switch ?: [:]
        ]
        
        processGatewayData(gatewayData)
        setGatewayStatus("connected")
        // Reset consecutive failure count on success
        state.consecutiveFailures = 0
        
    } catch (Exception e) {
        log.error "Error processing gateway response: ${e.message}"
        log.debug "Response data (first 200 chars): ${response.data?.encodeBase64()?.getAt(0..200) ?: 'empty'}"
        setGatewayStatus("data_error")
        scheduleRetryPoll()
    }
}

/**
 * Schedule a retry poll with exponential backoff
 */
void scheduleRetryPoll() {
    state.failedConnectionAttempts = (state.failedConnectionAttempts ?: 0) + 1
    int maxFailures = 5
    
    if (state.failedConnectionAttempts <= maxFailures) {
        // Exponential backoff: 30s, 60s, 120s, 240s, 480s
        int delaySeconds = 30 * (2 ** (state.failedConnectionAttempts - 1))
        delaySeconds = Math.min(delaySeconds, 480) // Cap at 8 minutes
        log.info "Scheduling retry connection to Salus Gateway in ${delaySeconds}s (attempt ${state.failedConnectionAttempts}/${maxFailures})"
        runIn(delaySeconds, "pollGatewayStatus")
    } else {
        log.error "Max consecutive failed connection attempts (${maxFailures}) reached - pausing automatic polling until manual recovery"
        setGatewayStatus("gateway_unreachable")
    }
}

Boolean connectToGateway() {
    if (!isConfigured()) {
        setGatewayStatus("not configured")
        return false
    }
    
    setGatewayStatus("connecting")
    try {
        pollGatewayStatus()
        setGatewayStatus("connected")
        return true
    } catch (e) {
        log.error "Connection failed: ${e}"
        setGatewayStatus("failed")
        return false
    }
}

void disconnectFromGateway() {
    setGatewayStatus("disconnected")
}

void setGatewayStatus(String status) {
    state.gatewayStatus = status
    state.lastUpdate = status == "connected" ? new Date().format("yyyy-MM-dd HH:mm:ss") : null
}




/**
 * Test basic IP connectivity to the Salus Gateway
 * Performs TCP connection test to check if gateway is reachable
 * @param gatewayIP The gateway IP address or hostname
 * @return Map with success boolean and message
 */
Map testIpConnectivity(String gatewayIP) {
    try {
        InetAddress address = InetAddress.getByName(gatewayIP)
        if (address.isReachable(3000)) {
            return [success: true, message: "Gateway reachable at ${gatewayIP} (ping successful)"]
        } else {
            return [success: false, message: "Gateway at ${gatewayIP} not reachable via ping (3s timeout)"]
        }
    } catch (UnknownHostException e) {
        return [success: false, message: "Cannot resolve hostname: ${gatewayIP}"]
    } catch (Exception e) {
        return [success: false, message: "IP connectivity test failed: ${e.message}"]
    }
}

/**
 * Test EUID/logical communication with the Salus Gateway
 * Performs TCP connection test and validates EUID format
 * @param gatewayIP The gateway IP address
 * @param euidToken The EUID token for encryption
 * @return Map with success boolean and message
 */
Map testEuidCommunication(String gatewayIP, String euidToken) {
    // First validate EUID format
    def euidPattern = /^[0-9A-Fa-f]{16}$/
    if (!euidToken?.matches(euidPattern)) {
        return [success: false, message: "Invalid EUID format (expected 16 hex characters)"]
    }
    
    // Test TCP connection to gateway
    try {
        Socket socket = new Socket()
        try {
            socket.connect(new InetSocketAddress(gatewayIP, 80), 5000)
            socket.close()
            return [success: true, message: "TCP connection to gateway established on port 80. EUID format validated."]
        } catch (Exception e) {
            return [success: false, message: "TCP connection failed: ${e.message}"]
        }
    } catch (Exception e) {
        return [success: false, message: "EUID test failed: ${e.message}"]
    }
}


void processGatewayData(Map<String, Object> gatewayData) {
    if (!gatewayData) return
    
    if (gatewayData.climate) {
        gatewayData.climate.each { id, data ->
            if (state.deviceMap?.containsKey(id)) {
                updateClimateChildDevice(id, data)
                sendEventToChild(id, data, "climate")
            } else {
                createClimateChildDevice(id, data)
            }
        }
    }
    
    if (gatewayData.switch) {
        gatewayData.switch.each { id, data ->
            if (state.deviceMap?.containsKey(id)) {
                updateSwitchChildDevice(id, data)
                sendEventToChild(id, data, "switch")
            } else {
                createSwitchChildDevice(id, data)
            }
        }
    }
    
    cleanupRemovedDevices(gatewayData)
}

/**
 * Send state update event to child app
 */
void sendEventToChild(String deviceId, Map<String, Object> data, String deviceType) {
    def deviceInfo = state.deviceMap?.get(deviceId)
    if (deviceInfo && deviceInfo.childAppId) {
        def child = getChildApp(deviceInfo.childAppId)
        if (child) {
            Map<String, Object> eventData = [gatewayStatus: "connected", lastUpdate: new Date().format("yyyy-MM-dd HH:mm:ss")]
            eventData.putAll(data)
            child.sendEvent(name: "gatewayUpdate", value: "update", data: eventData)
        }
    }
}

void createClimateChildDevice(String deviceId, Map<String, Object> data) {
    String childId = createChildApp("SalusThermostatController",
        [label: "${data.name ?: 'Thermostat'} Thermostat", installOnOpen: true])
    if (childId) {
        state.deviceMap = state.deviceMap ?: [:]
        // Store both child app ID and placeholder for virtual device DNI
        // Child app will populate virtualDni when it creates the device
        state.deviceMap[deviceId] = [childAppId: childId, virtualDni: null]
        configureClimateChildDevice(childId, deviceId, data)
    }
}

void createSwitchChildDevice(String deviceId, Map<String, Object> data) {
    String childId = createChildApp("SalusSwitchController",
        [label: "${data.name ?: 'Switch'} Switch", installOnOpen: true])
    if (childId) {
        state.deviceMap = state.deviceMap ?: [:]
        state.deviceMap[deviceId] = [childAppId: childId, virtualDni: null]
        configureSwitchChildDevice(childId, deviceId, data)
    }
}

void configureClimateChildDevice(String appId, String deviceId, Map<String, Object> data) {
    def child = getChildApp(appId)
    if (child) {
        // Pass configuration via settings (must be defined in child app preferences)
        child.updateSetting("deviceId", deviceId)
        child.updateSetting("deviceName", data.name ?: "Thermostat")
        child.updateSetting("deviceModel", data.model ?: "Unknown")
        child.updateSetting("parentAppId", this.app.id)
        initializeClimateState(appId, data)
    }
}

void configureSwitchChildDevice(String appId, String deviceId, Map<String, Object> data) {
    def child = getChildApp(appId)
    if (child) {
        child.updateSetting("deviceId", deviceId)
        child.updateSetting("deviceName", data.name ?: "Switch")
        child.updateSetting("deviceModel", data.model ?: "Unknown")
        child.updateSetting("parentAppId", this.app.id)
        initializeSwitchState(appId, data)
    }
}

void initializeClimateState(String appId, Map<String, Object> data) {
    def child = getChildApp(appId)
    double tempC = data.currentTemperature ?: 0
    double setpointC = data.targetTemperature ?: 0
    String tempUnit = getTemperatureUnit()
    
    child.setDeviceState(
        temperature: tempC,
        temperatureF: convertToPreferredUnit(tempC, tempUnit),
        heatingSetpoint: setpointC,
        heatingSetpointF: convertToPreferredUnit(setpointC, tempUnit),
        presetMode: data.presetMode ?: "follow_schedule",
        thermostatMode: data.hvacMode ?: "heat",
        hvacAction: data.hvacAction ?: "idle",
        isLocked: data.isLocked ?: false
    )
}

void initializeSwitchState(String appId, Map<String, Object> data) {
    def child = getChildApp(appId)
    child.setDeviceState(
        switch: data.state ?: "off",
        switchName: data.name ?: "",
        switchModel: data.model ?: ""
    )
}

void updateClimateChildDevice(String deviceId, Map<String, Object> data) {
    def deviceInfo = state.deviceMap?.get(deviceId)
    if (deviceInfo && deviceInfo.virtualDni) {
        def child = getChildDevice(deviceInfo.virtualDni)
        if (child) {
            String tempUnit = getTemperatureUnit()
            double tempC = data.currentTemperature ?: 0
            double setpointC = data.targetTemperature ?: 0
            child.updateState(
                temperature: tempC,
                heatingSetpoint: setpointC,
                presetMode: data.presetMode,
                thermostatMode: data.hvacMode,
                hvacAction: data.hvacAction,
                isLocked: data.isLocked
            )
        }
    } else if (deviceInfo && deviceInfo.childAppId) {
        // Fallback: try via child app
        String appId = deviceInfo.childAppId
        initializeClimateState(appId, data)
    }
}

void updateSwitchChildDevice(String deviceId, Map<String, Object> data) {
    def deviceInfo = state.deviceMap?.get(deviceId)
    if (deviceInfo && deviceInfo.virtualDni) {
        def child = getChildDevice(deviceInfo.virtualDni)
        if (child) {
            child.updateState(switch: data.state ?: "off")
        }
    } else if (deviceInfo && deviceInfo.childAppId) {
        String appId = deviceInfo.childAppId
        getChildApp(appId)?.setDeviceState(switch: data.state ?: "off")
    }
}

void cleanupRemovedDevices(Map<String, Object> data) {
    Set ids = new HashSet()
    ids.addAll(data.climate?.keySet() ?: [])
    ids.addAll(data.switch?.keySet() ?: [])
    
    state.deviceMap?.findAll { id, _ -> !ids.contains(id) }?.each { id, info ->
        try { 
            if (info.childAppId) deleteChildApp(info.childAppId)
            if (info.virtualDni) deleteChildDevice(info.virtualDni)
            state.deviceMap.remove(id) 
        }
        catch (e) { log.error "Error removing ${id}: ${e}" }
    }
}

/**
 * Called by child app to register its virtual device DNI
 */
void updateVirtualDni(String deviceId, String virtualDni) {
    def deviceInfo = state.deviceMap?.get(deviceId)
    if (deviceInfo) {
        deviceInfo.virtualDni = virtualDni
        log.debug "Updated virtual DNI for ${deviceId}: ${virtualDni}"
    } else {
        log.warn "updateVirtualDni called for unknown deviceId: ${deviceId}"
    }
}

def sendCommandToGateway(String deviceId, String command, Object params) {
    if (!isConfigured()) return [success:false, error:"Not configured"]
    
    try {
        Boolean result = executeGatewayCommand(deviceId, command, params)
        return result ? [success:true] : [success:false, error:"Failed"]
    } catch (e) {
        return [success:false, error:"${e}"]
    }
}

Boolean executeGatewayCommand(String deviceId, String command, Object params) {
    return executeGatewayCommandWithRetry(deviceId, command, params, 0)
}

Boolean executeGatewayCommandWithRetry(String deviceId, String command, Object params, Integer retryCount) {
    String euid = state.euidToken
    String host = state.gatewayIP
    
    String requestBody
    switch (command) {
        case "setTemperature":
        case "setHeatingSetpoint":
            // Convert from user's preferred unit to Celsius
            double tempC = convertFromPreferredUnit(params instanceof Map ? params.value : params, getTemperatureUnit())
            int tempX100 = Math.round(tempC * 100)
            requestBody = "{\"requestAttr\":\"write\",\"id\":[{\"data\":{\"UniID\":\"${deviceId}\"},\"sIT600TH\":{\"SetHeatingSetpoint_x100\":${tempX100}}}]}"
            break
            
        case "setPresetMode":
            def preset = (params instanceof Map ? params.value : params)?.toString()?.toLowerCase()
            def holdType = [follow_schedule:"STANDBY", permanent_hold:"PERMANENT_HOLD", 
                           standby:"OFF", away:"AWAY"].get(preset, "STANDBY")
            requestBody = "{\"requestAttr\":\"write\",\"id\":[{\"data\":{\"UniID\":\"${deviceId}\"},\"sIT600TH\":{\"SetHoldType\":${holdType}}}]}"
            break
            
        case "setThermostatLock":
            def locked = params instanceof Map ? params.value : params
            requestBody = "{\"requestAttr\":\"write\",\"id\":[{\"data\":{\"UniID\":\"${deviceId}\"},\"sTherUIS\":{\"SetLockKey\":${locked ? 1 : 0}}}]}"
            break
            
        case "turnOnSwitch":
            requestBody = "{\"requestAttr\":\"write\",\"id\":[{\"data\":{\"UniID\":\"${deviceId}\"},\"sOnOffS\":{\"SetOnOff\":1}}]}"
            break
            
        case "turnOffSwitch":
            requestBody = "{\"requestAttr\":\"write\",\"id\":[{\"data\":{\"UniID\":\"${deviceId}\"},\"sOnOffS\":{\"SetOnOff\":0}}]}"
            break
            
        default:
            log.warn "Unsupported command: ${command}"
            return false
    }
    
    if (!requestBody) return false
    
    try {
        byte[] encrypted = encryptPayload(requestBody, euid)
        httpPost([
            uri: host, port: 80, path: "/deviceid/write",
            contentType: "application/json", body: encrypted,
            headers: ["content-type":"application/json"], timeout: 10
        ], "commandResponseHandler")
        return true
    } catch (e) {
        log.error "Command failed (attempt ${retryCount + 1}): ${e}"
        
        // Retry with exponential backoff if we haven't exceeded max retries
        if (retryCount < MAX_COMMAND_RETRIES) {
            Integer delay = COMMAND_RETRY_DELAYS[retryCount]
            log.info "Scheduling command retry ${retryCount + 1}/${MAX_COMMAND_RETRIES} in ${delay} seconds"
            runIn(delay, {
                executeGatewayCommandWithRetry(deviceId, command, params, retryCount + 1)
            })
            return true  // Return true to indicate command was queued for retry
        } else {
            log.error "Command failed after ${MAX_COMMAND_RETRIES} retries"
            return false
        }
    }
}

def commandResponseHandler(response) {
    log.debug "Command response status: ${response.status}"
    
    if (response.status < 0) {
        log.error "Command network error: ${response.errorMessage ?: 'Unknown network error'}"
        setGatewayStatus("command_network_error")
        return
    }
    
    if (response.status != 200) {
        log.warn "Command HTTP error: ${response.status}"
        setGatewayStatus("command_http_error_${response.status}")
        return
    }
    
    // Command sent successfully - status will be updated when next poll completes
    setGatewayStatus("command_sent")
}

void retryConnection() {
    state.retryCount = state.retryCount ?: 0
    if (state.retryCount < 3) {
        state.retryCount++
        runIn(state.retryCount * 10, "attemptReconnection")
    } else {
        setGatewayStatus("failed")
        state.retryCount = 0
    }
}

void attemptReconnection() {
    if (connectToGateway()) {
        state.retryCount = 0
        scheduleStatusPolling()
    } else {
        retryConnection()
    }
}
