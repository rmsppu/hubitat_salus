/**
 *  Salus Common Library
 *  
 *  Shared functionality for Salus Hubitat integration.
 *  
 *  Inspired by the kasaCommon library in the Kasa Hubitat integration.
 *  
 *  Based on work from:
 *  - Home Assistant Salus iT600 Integration (https://github.com/custom-components/salus)
 *  - CoCoHue - Hue Bridge Integration for Hubitat (https://github.com/HubitatCommunity/CoCoHue)
 *  - BlubButtons Hubitat Integration
 *  - Kasa Hubitat Integration
 */

import groovy.transform.Field

/**
 * Common constants and utility functions for Salus devices
 */
class SalusCommon {
    
    // Salus device models
    static final String MODEL_AWRT10RF = "AWRT10RF"
    static final String MODEL_AS20WRF = "AS20WRF"
    static final String MODEL_AKL04P = "AKL04P"
    
    // Salus device types
    static final String DEVICE_TYPE_THERMOSTAT = "climate"
    static final String DEVICE_TYPE_SWITCH = "switch"
    static final String DEVICE_TYPE_BINARY_SENSOR = "binary_sensor"
    static final String DEVICE_TYPE_COVER = "cover"
    static final String DEVICE_TYPE_SENSOR = "sensor"
    static final String DEVICE_TYPE_LOCK = "lock"
    
    // Thermostat presets (radiator heating only)
    static final List<String> THERMOSTAT_PRESETS = [
            "follow_schedule", 
            "permanent_hold", 
            "temporary_hold", 
            "standby", 
            "away"
    ]
    
    // Thermostat modes (radiator heating only - no cooling)
    static final List<String> THERMOSTAT_MODES = ["off", "heat"]
    
    // Thermostat fan modes (fixed for radiator systems)
    static final List<String> THERMOSTAT_FAN_MODES = ["auto"]
    
    /**
     * Validate if a thermostat preset is supported
     */
    static Boolean isValidThermostatPreset(String preset) {
        return THERMOSTAT_PRESETS.contains(preset?.toLowerCase().trim())
    }
    
    /**
     * Validate if a thermostat mode is supported
     */
    static Boolean isValidThermostatMode(String mode) {
        return THERMOSTAT_MODES.contains(mode?.toLowerCase().trim())
    }
    
    /**
     * Validate if a thermostat fan mode is supported
     */
    static Boolean isValidThermostatFanMode(String mode) {
        return THERMOSTAT_FAN_MODES.contains(mode?.toLowerCase().trim())
    }
    
    /**
     * Convert Salus HVAC mode to Hubitat thermostat mode
     */
    static String salusToHubitatThermostatMode(String salusMode) {
        switch (salusMode?.toLowerCase()) {
            case "off":
                return "off"
            case "heat":
                return "heat"
            case "cool":
                return "cool"  // Not used for radiator heating but kept for completeness
            case "auto":
                return "auto"  // Not used for radiator heating but kept for completeness
            default:
                return "off"
        }
    }
    
    /**
     * Convert Hubitat thermostat mode to Salus HVAC mode
     */
    static String hubitatToSalusThermostatMode(String hubitatMode) {
        switch (hubitatMode?.toLowerCase()) {
            case "off":
                return "off"
            case "heat":
                return "heat"
            case "cool":
                return "cool"  // Not used for radiator heating
            case "auto":
                return "auto"  // Not used for radiator heating
            default:
                return "off"
        }
    }
    
    /**
     * Convert Salus preset mode to Hubitat preset mode
     */
    static String salusToHubitatPresetMode(String salusPreset) {
        // Salus and Hubitat preset modes are similar enough to use directly
        return salusPreset?.toLowerCase().trim()
    }
    
    /**
     * Convert Hubitat preset mode to Salus preset mode
     */
    static String hubitatToSalusPresetMode(String hubitatPreset) {
        // Hubitat and Salus preset modes are similar enough to use directly
        return hubitatPreset?.toLowerCase().trim()
    }
    
    /**
     * Calculate temperature in Celsius from raw value if needed
     */
    static Number normalizeTemperature(Number temp) {
        return temp  // Assuming Salus already provides Celsius
    }
    
    /**
     * Logging helper methods
     */
    static void logDebug(String message, Object logger = null) {
        if (logger) {
            logger.debug message
        }
        // In a real implementation, we might want to check a global debug setting
    }
    
    static void logInfo(String message, Object logger = null) {
        if (logger) {
            logger.info message
        }
    }
    
    static void logWarn(String message, Object logger = null) {
        if (logger) {
            logger.warn message
        }
    }
    
    static void logError(String message, Object logger = null) {
        if (logger) {
            logger.error message
        }
    }
    
    /**
     * Safe string truncation
     */
    static String truncate(String input, Integer maxLength) {
        if (!input) return null
        if (input.length() <= maxLength) return input
        return input.substring(0, maxLength) + "..."
    }
    
    /**
     * Check if a string is null or empty
     */
    static Boolean isBlank(String str) {
        return !str || str.trim() == ""
    }
    
    /**
     * Non-null safe version of isBlank
     */
    static Boolean isNotBlank(String str) {
        return !isBlank(str)
    }
}

// Export the class for use in other scripts
