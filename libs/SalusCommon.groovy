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

/* Id: 3de7b7a */
/* Date: 2026-09-27 15:55:41 */
/* Commit: 238 */

import groovy.transform.Field
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.crypto.spec.IvParameterSpec

library (
    author: "Salus Hubitat Integration",
    category: "Utilities",
    description: "Common constants and utility functions for Salus devices",
    name: "SalusCommon",
    namespace: "hubitat",
    version: "0.1.0",
    importUrl: "https://raw.githubusercontent.com/rmsppu/hubitat_salus/main/libs/SalusCommon.groovy"
)

// =============================================================================
// Salus Device Models
// =============================================================================
@Field static final String MODEL_AWRT10RF = "AWRT10RF"
@Field static final String MODEL_AS20WRF = "AS20WRF"
@Field static final String MODEL_AKL04P = "AKL04P"

// =============================================================================
// Salus Device Types
// =============================================================================
@Field static final String DEVICE_TYPE_THERMOSTAT = "climate"
@Field static final String DEVICE_TYPE_SWITCH = "switch"
@Field static final String DEVICE_TYPE_BINARY_SENSOR = "binary_sensor"
@Field static final String DEVICE_TYPE_COVER = "cover"
@Field static final String DEVICE_TYPE_SENSOR = "sensor"
@Field static final String DEVICE_TYPE_LOCK = "lock"

// =============================================================================
// Thermostat Constants (radiator heating only)
// =============================================================================
@Field static final List<String> THERMOSTAT_PRESETS = [
    "follow_schedule",
    "permanent_hold",
    "temporary_hold",
    "standby",
    "away"
]

@Field static final List<String> THERMOSTAT_MODES = ["off", "heat"]
@Field static final List<String> THERMOSTAT_FAN_MODES = ["auto"]

// =============================================================================
// Temperature Conversion Utilities
// =============================================================================

/**
 * Convert Celsius to Fahrenheit.
 */
static double celsiusToFahrenheit(double c) {
    return (c * 9/5) + 32
}

/**
 * Convert Fahrenheit to Celsius.
 */
static double fahrenheitToCelsius(double f) {
    return (f - 32) * 5/9
}

/**
 * Convert temperature to user's preferred unit.
 * @param celsius Temperature in Celsius
 * @param temperatureUnit "C" for Celsius, "F" for Fahrenheit
 */
static double convertToPreferredUnit(double celsius, String temperatureUnit) {
    if (temperatureUnit == "F") {
        return celsiusToFahrenheit(celsius)
    }
    return celsius
}

/**
 * Convert from user's preferred unit to Celsius (for gateway communication).
 * @param value Temperature in user's preferred unit
 * @param temperatureUnit "C" for Celsius, "F" for Fahrenheit
 */
static double convertFromPreferredUnit(double value, String temperatureUnit) {
    if (temperatureUnit == "F") {
        return fahrenheitToCelsius(value)
    }
    return value
}

/**
 * Calculate temperature in Celsius from raw value if needed.
 */
static Number normalizeTemperature(Number temp) {
    return temp  // Assuming Salus already provides Celsius
}

// =============================================================================
// AES-256-CBC Encryption Utilities
// =============================================================================
// The scheme below (fixed IV, MD5-derived key from "Salus-{euid}", PKCS7 padding)
// is dictated by the legacy UGE600/UG800 gateway firmware and is reproduced here
// for interoperability only. It is NOT a design choice and MUST NOT be "hardened".

@Field static final byte[] IV = [
    0x88, 0xA6, 0xB0, 0x79, 0x5D, 0x85, 0xDB, 0xFC,
    0xE6, 0xE0, 0xB3, 0xE9, 0xA6, 0x29, 0x65, 0x4B
] as byte[]

/**
 * Generate the AES-256 encryption key from EUID.
 * Key = MD5("Salus-{euid.lower()}") + 16 zero bytes (32 bytes total)
 */
static byte[] generateEncryptionKey(String euid) {
    MessageDigest md = MessageDigest.getInstance("MD5")
    byte[] keyMaterial = md.digest("Salus-${euid.toLowerCase()}".getBytes())
    byte[] key = new byte[32]
    System.arraycopy(keyMaterial, 0, key, 0, 16)
    System.arraycopy(new byte[16], 0, key, 16, 16)
    return key
}

/**
 * Encrypt a JSON payload using AES-256-CBC with PKCS7 padding.
 */
static byte[] encryptPayload(String jsonPayload, String euid) {
    byte[] key = generateEncryptionKey(euid)
    Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(IV))
    return cipher.doFinal(jsonPayload.getBytes("UTF-8"))
}

/**
 * Decrypt a response from the Salus gateway.
 * Strips any non-block-aligned trailer and decrypts the response.
 */
static String decryptPayload(byte[] ciphertext, String euid) {
    byte[] key = generateEncryptionKey(euid)
    Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(IV))
    
    // Strip any non-block-aligned trailer
    int remainder = ciphertext.length % 16
    if (remainder > 0) {
        ciphertext = ciphertext[0..(ciphertext.length - remainder - 1)]
    }
    
    byte[] decrypted = cipher.doFinal(ciphertext)
    return new String(decrypted, "UTF-8")
}

// =============================================================================
// Validation Utilities
// =============================================================================

/**
 * Validate if a thermostat preset is supported.
 */
static Boolean isValidThermostatPreset(String preset) {
    return THERMOSTAT_PRESETS.contains(preset?.toLowerCase().trim())
}

/**
 * Validate if a thermostat mode is supported.
 */
static Boolean isValidThermostatMode(String mode) {
    return THERMOSTAT_MODES.contains(mode?.toLowerCase().trim())
}

/**
 * Validate if a thermostat fan mode is supported.
 */
static Boolean isValidThermostatFanMode(String mode) {
    return THERMOSTAT_FAN_MODES.contains(mode?.toLowerCase().trim())
}

// =============================================================================
// Mode Conversion Utilities
// =============================================================================

/**
 * Convert Salus HVAC mode to Hubitat thermostat mode.
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
 * Convert Hubitat thermostat mode to Salus HVAC mode.
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
 * Convert Salus preset mode to Hubitat preset mode.
 */
static String salusToHubitatPresetMode(String salusPreset) {
    // Salus and Hubitat preset modes are similar enough to use directly
    return salusPreset?.toLowerCase().trim()
}

/**
 * Convert Hubitat preset mode to Salus preset mode.
 */
static String hubitatToSalusPresetMode(String hubitatPreset) {
    // Hubitat and Salus preset modes are similar enough to use directly
    return hubitatPreset?.toLowerCase().trim()
}

// =============================================================================
// String Utilities
// =============================================================================

/**
 * Safe string truncation.
 */
static String truncate(String input, Integer maxLength) {
    if (!input) return null
    if (input.length() <= maxLength) return input
    return input.substring(0, maxLength) + "..."
}

/**
 * Check if a string is null or empty.
 */
static Boolean isBlank(String str) {
    return !str || str.trim() == ""
}

/**
 * Non-null safe version of isBlank.
 */
static Boolean isNotBlank(String str) {
    return !isBlank(str)
}