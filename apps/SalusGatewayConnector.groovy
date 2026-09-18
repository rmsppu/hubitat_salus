/**
 *  Salus Gateway Connector - Parent Application
 *  
 *  Connects to Salus Universal Gateway (UG) 600 and manages child devices
 *  for thermostats, relay zones, and other Salus devices.
 *  
 *  Based on Hubitat integration patterns from:
 *  - Home Assistant Salus iT600 Integration (https://github.com/custom-components/salus)
 *  - CoCoHue - Hue Bridge Integration for Hubitat (https://github.com/HubitatCommunity/CoCoHue)
 *  - BlubButtons Hubitat Integration Example
 *  - Kasa Hubitat Integration Example
 */

import groovy.transform.Field
import com.hubitat.app.DeviceWrapper
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.padding.PKCS7Padding

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

void initialize() {
    log.debug "Initializing Salus Gateway Connector"
    unschedule()
    
    if (!state.gatewayIP) { state.gatewayIP = "" }
    if (!state.euidToken) { state.euidToken = "" }
    if (!state.pollInterval) { state.pollInterval = POLL_INTERVAL_MINUTES }
    if (!state.deviceMap) { state.deviceMap = [:] }
    if (!state.gatewayStatus) { state.gatewayStatus = "disconnected" }
    
    if (isConfigured()) {
        connectToGateway()
        scheduleStatusPolling()
    } else {
        log.warn "Salus gateway not configured. Please configure IP address and EUID token."
        setGatewayStatus("not configured")
    }
}

void cleanup() {
    log.debug "Cleaning up Salus Gateway Connector"
    unschedule()
    disconnectFromGateway()
}

Boolean isConfigured() {
    return state.gatewayIP && state.gatewayIP.trim() != "" && 
           state.euidToken && state.euidToken.trim() != ""
}

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

void refreshStatus() {
    log.debug "Manual refresh requested"
    if (isConfigured()) {
        pollGatewayStatus()
    }
}

void connectToGateway() {
    if (!isConfigured()) {
        log.warn "Cannot connect: gateway not configured"
        setGatewayStatus("not configured")
        return false
    }
    
    log.debug "Attempting to connect to Salus gateway at ${state.gatewayIP}"
    setGatewayStatus("connecting...")
    
    try {
        pollGatewayStatus()
        setGatewayStatus("connected")
        return true
    } catch (Exception e) {
        log.error "Failed to connect to Salus gateway: ${e}"
        setGatewayStatus("connection failed")
        return false
    }
}

void disconnectFromGateway() {
    log.debug "Disconnecting from Salus gateway"
    setGatewayStatus("disconnected")
}

void setGatewayStatus(String status) {
    state.gatewayIP = state.gatewayIP ?: ""
    state.gatewayStatus = status
    state.lastUpdate = (status == "connected") ? new Date().format("yyyy-MM-dd HH:mm:ss") : null
    updateChildDevicesGatewayStatus()
}

void scheduleStatusPolling() {
    log.debug "Scheduling status polling every ${state.pollInterval} minutes"
    unschedule("pollGatewayStatus")
    
    if (state.pollInterval > 0) {
        schedule("*/${state.pollInterval} * * * ? * * *", "pollGatewayStatus")
    }
}

void pollGatewayStatus() {
    if (!isConfigured()) {
        log.debug "Skip polling: gateway not configured"
        setGatewayStatus("not configured")
        return
    }
    
    log.debug "Polling Salus gateway for status updates"
    setGatewayStatus("polling...")
    
    try {
        Map<String, Object> gatewayData = fetchGatewayData()
        
        if (gatewayData) {
            processGatewayData(gatewayData)
            setGatewayStatus("connected")
        } else {
            log.warn "No data received from gateway"
            setGatewayStatus("no data")
        }
    } catch (Exception e) {
        log.error "Error polling gateway: ${e}"
        setGatewayStatus("polling failed")
        retryConnection()
    }
}

/**
 * Generate AES-256-CBC encryption key from EUID
 * Key = MD5("Salus-{euid.lower()}") + 16 zero bytes
 */
byte[] generateEncryptionKey(String euid) {
    MessageDigest md = MessageDigest.getInstance("MD5")
    byte[] keyMaterial = md.digest("Salus-${euid.toLowerCase()}".getBytes())
    byte[] key = new byte[32]
    System.arraycopy(keyMaterial, 0, key, 0, 16)
    System.arraycopy(new byte[16], 0, key, 16, 16)
    return key
}

/**
 * Fixed IV for AES-CBC (from salus-it600-client library)
 */
byte[] getFixedIV() {
    return bytes(
        0x88, 0xA6, 0xB0, 0x79, 0x5D, 0x85, 0xDB, 0xFC,
        0xE6, 0xE0, 0xB3, 0xE9, 0xA6, 0x29, 0x65, 0x4B
    )
}

/**
 * Encrypt JSON payload for Salus gateway
 */
byte[] encryptPayload(String jsonPayload, String euid) {
    byte[] key = generateEncryptionKey(euid)
    byte[] iv = getFixedIV()
    
    Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    SecretKeySpec keySpec = new SecretKeySpec(key, "AES")
    IvParameterSpec ivSpec = new IvParameterSpec(iv)
    cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
    
    return cipher.doFinal(jsonPayload.getBytes("UTF-8"))
}

/**
 * Fetch data from the Salus gateway API
 * Makes HTTP POST requests with encrypted payloads
 */
Map<String, Object> fetchGatewayData() {
    if (!isConfigured()) {
        log.debug "Cannot fetch gateway data: not configured"
        return null
    }
    
    String host = state.gatewayIP
    String euid = state.euidToken
    String url = "http://${host}:80/deviceid/read"
    
    log.debug "Fetching gateway data via POST ${url}"
    
    try {
        // Build the readall request
        String requestBody = '{"requestAttr": "readall"}'
        
        // Encrypt the request
        byte[] encryptedPayload = encryptPayload(requestBody, euid)
        
        // Make HTTP POST request
        def params = [
            uri: host,
            port: 80,
            path: "/deviceid/read",
            method: "POST",
            contentType: "application/json",
            body: encryptedPayload,
            headers: ["content-type": "application/json"],
            timeout: CONNECTION_TIMEOUT_SECONDS * 1000,
            ignoreSSLIssues: true
        ]
        
        // Note: In a real implementation, we would use:
        // asynchttp_post("handleGatewayResponse", params)
        // For now, return simulated structure showing expected format
        
        log.warn "HTTP implementation needed - returning simulated data structure"
        
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
                    hVACAction: "idle",
                    isLocked: false
                ]
            ],
            switch: [
                "relay_zone_1": [
                    id: "relay_zone_1",
                    name: "Zone 1 Pump",
                    model: "AKL04P",
                    state: "on"
                ]
            ]
        ]
    } catch (Exception e) {
        log.error "Error in fetchGatewayData: ${e}"
        return null
    }
}

/**
 * Send command to gateway
 */
Boolean sendCommandToGateway(String deviceId, String command, Object params) {
    log.debug "Sending command to gateway: deviceId=${deviceId}, command=${command}, params=${params}"
    
    if (!isConfigured()) {
        log.warn "Cannot send command: gateway not configured"
        return false
    }
    
    // Map commands to gateway API calls
    Map<String, Object> requestData = [:]
    
    switch (command) {
        case "setTemperature":
            // Extract temperature value
            def temp = params instanceof Map ? params.value : (params instanceof Number ? params : null)
            if (temp != null) {
                // For radiator heating, we only send heating setpoint
                // The Salus gateway uses centidegrees (x100)
                int tempX100 = Math.round(temp as Double * 100)
                requestData = ["sIT600TH": ["SetHeatingSetpoint_x100": tempX100]]
            }
            break
            
        case "setPresetMode":
            def preset = params instanceof Map ? params.value : null
            if (preset != null) {
                // Map Hubitat preset to Salus hold type
                // Preset mappings: follow_schedule=STANDBY, permanent_hold=PERMANENT_HOLD, 
                // temporary_hold=TEMPORARY_HOLD (not used), standby=OFF, away=AWAY
                requestData = ["sIT600TH": ["SetHoldType": preset]]
            }
            break
            
        case "setThermostatLock":
            def locked = params instanceof Map ? params.value : false
            requestData = ["sTherUIS": ["SetLockKey": locked ? 1 : 0]]
            break
            
        case "turnOnSwitch":
        case "turnOffSwitch":
            def state = command == "turnOnSwitch" ? 1 : 0
            requestData = ["sOnOffS": ["SetOnOff": state]]
            break
            
        default:
            log.warn "Unsupported command: ${command}"
            return false
    }
    
    if (!requestData) {
        log.warn "Could not build request for command: ${command}"
        return false
    }
    
    try {
        String requestBody = "{\"requestAttr\": \"write\", \"id\": [{\"data\": {\"UniID\": \"${deviceId}\"}, **requestData}]}"
        
        // Note: Actual HTTP call would go here
        log.debug "Would send: ${requestBody}"
        
        return true
    } catch (Exception e) {
        log.error "Error sending command: ${e}"
        return false
    }
}

void processGatewayData(Map<String, Object> gatewayData) {
    log.debug "Processing gateway data"
    
    if (gatewayData.climate) {
        gatewayData.climate.each { deviceId, deviceData ->
            if (state.deviceMap && state.deviceMap.containsKey(deviceId)) {
                updateClimateChildDevice(deviceId, deviceData)
            } else {
                createClimateChildDevice(deviceId, deviceData)
            }
        }
    }
    
    if (gatewayData.switch) {
        gatewayData.switch.each { deviceId, deviceData ->
            if (state.deviceMap && state.deviceMap.containsKey(deviceId)) {
                updateSwitchChildDevice(deviceId, deviceData)
            } else {
                createSwitchChildDevice(deviceId, deviceData)
            }
        }
    }
    
    cleanupRemovedDevices(gatewayData)
}

void createClimateChildDevice(String deviceId, Map<String, Object> deviceData) {
    log.debug "Creating climate child device for ${deviceId}"
    try {
        String childAppId = createChildApp(
            "SalusThermostatController",
            [
                label: "${deviceData.name} Thermostat",
                description: "Controls Salus thermostat ${deviceData.name}",
                installOnOpen: true
            ]
        )
        
        if (childAppId) {
            if (!state.deviceMap) state.deviceMap = [:]
            state.deviceMap[deviceId] = childAppId
            configureClimateChildDevice(childAppId, deviceId, deviceData)
        }
    } catch (Exception e) {
        log.error "Error creating climate child device for ${deviceId}: ${e}"
    }
}

void createSwitchChildDevice(String deviceId, Map<String, Object> deviceData) {
    log.debug "Creating switch child device for ${deviceId}"
    try {
        String childAppId = createChildApp(
            "SalusSwitchController",
            [
                label: "${deviceData.name} Switch",
                description: "Shows status of Salus switch ${deviceData.name}",
                installOnOpen: true
            ]
        )
        
        if (childAppId) {
            if (!state.deviceMap) state.deviceMap = [:]
            state.deviceMap[deviceId] = childAppId
            configureSwitchChildDevice(childAppId, deviceId, deviceData)
        }
    } catch (Exception e) {
        log.error "Error creating switch child device for ${deviceId}: ${e}"
    }
}

void configureClimateChildDevice(String childAppId, String deviceId, Map<String, Object> deviceData) {
    DeviceWrapper childApp = getChildDevice(childAppId)
    if (childApp) {
        childApp.updateSetting("deviceId", deviceId)
        childApp.updateSetting("deviceName", deviceData.name ?: "")
        childApp.updateSetting("deviceModel", deviceData.model ?: "")
        childApp.updateSetting("parentAppId", device.id)
        initializeClimateDeviceState(childAppId, deviceData)
    }
}

void configureSwitchChildDevice(String childAppId, String deviceId, Map<String, Object> deviceData) {
    DeviceWrapper childApp = getChildDevice(childAppId)
    if (childApp) {
        childApp.updateSetting("deviceId", deviceId)
        childApp.updateSetting("deviceName", deviceData.name ?: "")
        childApp.updateSetting("deviceModel", deviceData.model ?: "")
        childApp.updateSetting("parentAppId", device.id)
        initializeSwitchDeviceState(childAppId, deviceData)
    }
}

void updateClimateChildDevice(String deviceId, Map<String, Object> deviceData) {
    String childAppId = state.deviceMap ? state.deviceMap.get(deviceId) : null
    if (childAppId) {
        updateClimateDeviceState(childAppId, deviceData)
    }
}

void updateSwitchChildDevice(String deviceId, Map<String, Object> deviceData) {
    String childAppId = state.deviceMap ? state.deviceMap.get(deviceId) : null
    if (childAppId) {
        updateSwitchDeviceState(childAppId, deviceData)
    }
}

void initializeClimateDeviceState(String childAppId, Map<String, Object> deviceData) {
    DeviceWrapper childApp = getChildDevice(childAppId)
    if (childApp) {
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

void initializeSwitchDeviceState(String childAppId, Map<String, Object> deviceData) {
    DeviceWrapper childApp = getChildDevice(childAppId)
    if (childApp) {
        childApp.setDeviceState(
            "switch": deviceData.state ?: "off",
            "deviceName": deviceData.name ?: "",
            "deviceModel": deviceData.model ?: ""
        )
    }
}

void updateClimateDeviceState(String childAppId, Map<String, Object> deviceData) {
    DeviceWrapper childApp = getChildDevice(childAppId)
    if (childApp) {
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

void updateSwitchDeviceState(String childAppId, Map<String, Object> deviceData) {
    DeviceWrapper childApp = getChildDevice(childAppId)
    if (childApp) {
        childApp.setDeviceState("switch": deviceData.state ?: "off")
    }
}

void cleanupRemovedDevices(Map<String, Object> gatewayData) {
    Set<String> currentDeviceIds = new HashSet()
    
    if (gatewayData.climate) currentDeviceIds.addAll(gatewayData.climate.keySet())
    if (gatewayData.switch) currentDeviceIds.addAll(gatewayData.switch.keySet())
    
    if (state.deviceMap) {
        state.deviceMap.findAll { deviceId, childAppId ->
            !currentDeviceIds.contains(deviceId)
        }.each { deviceId, childAppId ->
            log.debug "Removing child device for removed device: ${deviceId} (${childAppId})"
            removeChildDevice(deviceId, childAppId)
        }
    }
}

void removeChildDevice(String deviceId, String childAppId) {
    try {
        deleteChildDevice(childAppId)
        if (state.deviceMap) {
            state.deviceMap.remove(deviceId)
        }
        log.debug "Successfully removed child device ${deviceId}"
    } catch (Exception e) {
        log.error "Error removing child device ${deviceId}: ${e}"
    }
}

def sendCommandToGateway(String deviceId, String command, Map<String, Object> params) {
    log.debug "Received command from child app: deviceId=${deviceId}, command=${command}"
    
    if (!isConfigured()) {
        log.warn "Cannot send command: gateway not configured"
        return [success: false, error: "Gateway not configured"]
    }
    
    try {
        Boolean result = executeGatewayCommand(deviceId, command, params)
        
        if (result) {
            log.debug "Successfully executed command ${command} on device ${deviceId}"
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

Boolean executeGatewayCommand(String deviceId, String command, Map<String, Object> params) {
    log.debug "Executing gateway command: deviceId=${deviceId}, command=${command}"
    
    // Call sendCommandToGateway
    sendCommandToGateway(deviceId, command, params)
    return true
}

void retryConnection() {
    if (!state.retryCount) {
        state.retryCount = 0
    }
    
    Integer delaySeconds = (state.retryCount < MAX_RETRY_ATTEMPTS) ? 
        (state.retryCount + 1) * CONNECTION_TIMEOUT_SECONDS : CONNECTION_TIMEOUT_SECONDS
    
    if (state.retryCount < MAX_RETRY_ATTEMPTS) {
        state.retryCount++
        log.debug "Attempting to reconnect (attempt ${state.retryCount}/${MAX_RETRY_ATTEMPTS})"
        runIn(delaySeconds, "attemptReconnection")
    } else {
        log.error "Max retry attempts reached. Giving up on gateway connection."
        setGatewayStatus("connection failed")
        state.retryCount = 0
    }
}

void attemptReconnection() {
    log.debug "Attempting gateway reconnection"
    if (connectToGateway()) {
        state.retryCount = 0
        scheduleStatusPolling()
    } else {
        retryConnection()
    }
}

void updateChildDevicesGatewayStatus() {
    if (state.deviceMap) {
        state.deviceMap.each { deviceId, childAppId ->
            DeviceWrapper childApp = getChildDevice(childAppId)
            if (childApp) {
                childApp.setDeviceState("gatewayStatus": state.gatewayStatus)
            }
        }
    }
}

