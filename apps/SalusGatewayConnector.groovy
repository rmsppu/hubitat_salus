/**
 *  Salus Gateway Connector - Parent Application
 *  
 *  Connects to Salus Universal Gateway (UG) 600 and manages child devices
 *  for thermostats, relay zones, and other Salus devices.
 *  
 *  Based on Hubitat integration patterns from:
 *  - Home Assistant Salus iT600 Integration
 *  - CoCoHue - Hue Bridge Integration
 */

import groovy.transform.Field
import com.hubitat.app.DeviceWrapper
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.crypto.spec.IvParameterSpec

@Field static final Integer POLL_INTERVAL_MINUTES = 5

definition (
    name: "Salus Gateway Connector",
    namespace: "hubitat",
    author: "Salus Hubitat Integration",
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
    initialize()
}

void deleted() {
    cleanup()
}

void initialize() {
    unschedule()
    
    if (!state.gatewayIP) state.gatewayIP = ""
    if (!state.euidToken) state.euidToken = ""
    if (!state.pollInterval) state.pollInterval = POLL_INTERVAL_MINUTES
    if (!state.deviceMap) state.deviceMap = [:]
    
    if (isConfigured()) {
        connectToGateway()
        scheduleStatusPolling()
    }
}

void cleanup() {
    unschedule()
    disconnectFromGateway()
}

Boolean isConfigured() {
    return state.gatewayIP?.trim() && state.euidToken?.trim()
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
        }
        section("Status") {
            paragraph "Status: ${state.gatewayStatus ?: 'unknown'}"
            paragraph "Devices: ${state.deviceMap?.size() ?: 0}"
        }
    }
}

/**
 * Temperature conversion helpers
 */
static double celsiusToFahrenheit(double c) { return (c * 9/5) + 32 }
static double fahrenheitToCelsius(double f) { return (f - 32) * 5/9 }

/**
 * Get temperature unit preference from Hubitat settings
 * Returns "F" for Fahrenheit, "C" for Celsius
 */
String getTemperatureUnit() {
    // Hubitat provides a global temperature unit preference
    // This is accessed through the settings map
    return settings?.temperatureUnit ?: "C"
}

/**
 * Convert temperature to user's preferred unit
 */
double convertToPreferredUnit(double celsius) {
    if (getTemperatureUnit() == "F") {
        return celsiusToFahrenheit(celsius)
    }
    return celsius
}

/**
 * Convert from user's preferred unit to Celsius (for gateway)
 */
double convertFromPreferredUnit(double value) {
    if (getTemperatureUnit() == "F") {
        return fahrenheitToCelsius(value)
    }
    return value
}

/**
 * AES-256-CBC Encryption
 */
byte[] encryptPayload(String jsonPayload, String euid) {
    MessageDigest md = MessageDigest.getInstance("MD5")
    byte[] keyMaterial = md.digest("Salus-${euid.toLowerCase()}".getBytes())
    byte[] key = new byte[32]
    System.arraycopy(keyMaterial, 0, key, 0, 16)
    System.arraycopy(new byte[16], 0, key, 16, 16)
    
    byte[] iv = [0x88,0xA6,0xB0,0x79,0x5D,0x85,0xDB,0xFC,
                 0xE6,0xE0,0xB3,0xE9,0xA6,0x29,0x65,0x4B] as byte[]
    
    Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv))
    
    return cipher.doFinal(jsonPayload.getBytes("UTF-8"))
}

def fetchGatewayData() {
    if (!isConfigured()) return null
    
    log.debug "Fetching from ${state.gatewayIP}"
    
    try {
        String requestBody = '{"requestAttr": "readall"}'
        byte[] encrypted = encryptPayload(requestBody, state.euidToken)
        
        httpPost([
            uri: state.gatewayIP, port: 80, path: "/deviceid/read",
            contentType: "application/json", body: encrypted,
            headers: ["content-type":"application/json"], timeout: 10
        ], "handleGatewayResponse")
        
        return [climate:[:], switch:[:]]
    } catch (e) {
        log.error "Error: ${e}"
        return null
    }
}

void handleGatewayResponse(response) {
    log.debug "Response status: ${response.status}"
    if (response.status == 200) {
        processGatewayData(response.data)
    }
}

void connectToGateway() {
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

void scheduleStatusPolling() {
    if (state.gollInterval > 0) {
        schedule("*/${state.pollInterval} * * * ? * * *", "pollGatewayStatus")
    }
}

void pollGatewayStatus() {
    if (!isConfigured()) {
        setGatewayStatus("not configured")
        return
    }
    
    try {
        def data = fetchGatewayData()
        if (data) {
            processGatewayData(data)
            setGatewayStatus("connected")
        } else {
            setGatewayStatus("no data")
        }
    } catch (e) {
        log.error "Poll failed: ${e}"
        setGatewayStatus("failed")
        retryConnection()
    }
}

void processGatewayData(Map<String, Object> gatewayData) {
    if (!gatewayData) return
    
    if (gatewayData.climate) {
        gatewayData.climate.each { id, data ->
            if (state.deviceMap?.containsKey(id)) {
                updateClimateChildDevice(id, data)
            } else {
                createClimateChildDevice(id, data)
            }
        }
    }
    
    if (gatewayData.switch) {
        gatewayData.switch.each { id, data ->
            if (state.deviceMap?.containsKey(id)) {
                updateSwitchChildDevice(id, data)
            } else {
                createSwitchChildDevice(id, data)
            }
        }
    }
    
    cleanupRemovedDevices(gatewayData)
}

void createClimateChildDevice(String deviceId, Map<String, Object> data) {
    String childId = createChildApp("SalusThermostatController",
        [label: "${data.name ?: 'Thermostat'} Thermostat", installOnOpen: true])
    if (childId) {
        state.deviceMap = state.deviceMap ?: [:]
        state.deviceMap[deviceId] = childId
        configureClimateChildDevice(childId, deviceId, data)
    }
}

void createSwitchChildDevice(String deviceId, Map<String, Object> data) {
    String childId = createChildApp("SalusSwitchController",
        [label: "${data.name ?: 'Switch'} Switch", installOnOpen: true])
    if (childId) {
        state.deviceMap = state.deviceMap ?: [:]
        state.deviceMap[deviceId] = childId
        configureSwitchChildDevice(childId, deviceId, data)
    }
}

void configureClimateChildDevice(String appId, String deviceId, Map<String, Object> data) {
    def child = getChildDevice(appId)
    if (child) {
        child.updateSetting("deviceId", deviceId)
        child.updateSetting("parentAppId", device.id)
        initializeClimateState(appId, data)
    }
}

void configureSwitchChildDevice(String appId, String deviceId, Map<String, Object> data) {
    def child = getChildDevice(appId)
    if (child) {
        child.updateSetting("deviceId", deviceId)
        child.updateSetting("parentAppId", device.id)
        initializeSwitchState(appId, data)
    }
}

void initializeClimateState(String appId, Map<String, Object> data) {
    def child = getChildDevice(appId)
    double tempC = data.currentTemperature ?: 0
    double setpointC = data.targetTemperature ?: 0
    
    child.setDeviceState(
        temperature: tempC,
        temperatureF: celsiusToFahrenheit(tempC),
        heatingSetpoint: setpointC,
        heatingSetpointF: celsiusToFahrenheit(setpointC),
        presetMode: data.presetMode ?: "follow_schedule",
        thermostatMode: data.hvacMode ?: "heat",
        hvacAction: data.hvacAction ?: "idle",
        isLocked: data.isLocked ?: false
    )
}

void initializeSwitchState(String appId, Map<String, Object> data) {
    def child = getChildDevice(appId)
    child.setDeviceState(
        switch: data.state ?: "off",
        switchName: data.name ?: "",
        switchModel: data.model ?: ""
    )
}

void updateClimateChildDevice(String deviceId, Map<String, Object> data) {
    String appId = state.deviceMap?.get(deviceId)
    if (appId) initializeClimateState(appId, data)
}

void updateSwitchChildDevice(String deviceId, Map<String, Object> data) {
    String appId = state.deviceMap?.get(deviceId)
    if (appId) {
        getChildDevice(appId)?.setDeviceState(switch: data.state ?: "off")
    }
}

void cleanupRemovedDevices(Map<String, Object> data) {
    Set ids = new HashSet()
    ids.addAll(data.climate?.keySet() ?: [])
    ids.addAll(data.switch?.keySet() ?: [])
    
    state.deviceMap?.findAll { id, _ -> !ids.contains(id) }?.each { id, appId ->
        try { deleteChildDevice(appId); state.deviceMap.remove(id) }
        catch (e) { log.error "Error removing ${id}: ${e}" }
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
    String euid = state.euidToken
    String host = state.gatewayIP
    
    String requestBody
    switch (command) {
        case "setTemperature":
        case "setHeatingSetpoint":
            // Convert from user's preferred unit to Celsius
            double tempC = convertFromPreferredUnit(params instanceof Map ? params.value : params)
            int tempX100 = Math.round(tempC * 100)
            requestBody = "{\"requestAttr\":\"write\",\"id\":[{\"data\":{\"UniID\":\"${deviceId}\"},\"sIT600TH\":{\"SetHeatingSetpoint_x100\":${tempX100}}}]}""
            break
            
        case "setPresetMode":
            def preset = (params instanceof Map ? params.value : params)?.toString()?.toLowerCase()
            def holdType = [follow_schedule:"STANDBY", permanent_hold:"PERMANENT_HOLD", 
                           standby:"OFF", away:"AWAY"].get(preset, "STANDBY")
            requestBody = "{\"requestAttr\":\"write\",\"id\":[{\"data\":{\"UniID\":\"${deviceId}\"},\"sIT600TH\":{\"SetHoldType\":${holdType}}}]}""
            break
            
        case "setThermostatLock":
            def locked = params instanceof Map ? params.value : params
            requestBody = "{\"requestAttr\":\"write\",\"id\":[{\"data\":{\"UniID\":\"${deviceId}\"},\"sTherUIS\":{\"SetLockKey\":${locked ? 1 : 0}}}]}""
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
        log.error "Command failed: ${e}"
        return false
    }
}

def commandResponseHandler(response) {
    setGatewayStatus(response.status == 200 ? "connected" : "command_error")
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
