# Changelog

All notable changes to the Salus Hubitat Integration project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Fixed
- Converted `SalusCommon.groovy` from a `class` definition to proper Hubitat `library()` format that is compatible with Hubitat's architecture
- Added AES-256-CBC encryption and decryption functions to `SalusCommon.groovy` library
- Updated `handleGatewayResponse` in `SalusGatewayConnector.groovy` to properly decrypt gateway responses using the library methods
- Removed duplicate encryption code from `SalusGatewayConnector.groovy` (now uses library functions)
- Fixed string concatenation error in `SalusGatewayConnector.groovy` - removed extra double quotes in `setThermostatLock` command payload
- Fixed typo in `SalusGatewayConnector.groovy` - changed `state.gollInterval` to `state.pollInterval` in scheduling function
- Fixed Hubitat scheduling cron expression - changed from `"*/${state.pollInterval} * * * ? * * *"` to `"0 */${state.pollInterval} * * * ?"`
- Removed invalid C preprocessor directives (`#ifdef FALSE`, `#if`, `#endif`) from `SalusSwitch.groovy` that would cause Groovy compilation errors
- Removed unused `DeviceWrapper` imports from child apps and drivers
- Fixed `getParentApp()` methods in `SalusSwitchController.groovy` and `SalusThermostatController.groovy` to correctly use Hubitat's `parent` property instead of incorrectly calling `getChildDevice()`
- Replaced generic `command()` method in device drivers with proper Hubitat capability methods (`on()`, `off()`, `refresh()` for switch; `setTemperature()`, `setHeatingSetpoint()`, `setThermostatMode()`, etc. for thermostat)
- Added `#include hubitat.SalusCommon` directives to apps and child apps for library support
- **Removed invalid `com.hubitat.app.DeviceWrapper` import from child apps** (SalusThermostatController.groovy, SalusSwitchController.groovy) - DeviceWrapper is for drivers, not apps
- **Removed logging methods from SalusCommon.groovy library** - `log` variable not available in library context
- **Fixed temperature unit conversion in library** - Removed `getTemperatureUnit()`, `convertToPreferredUnit()`, `convertFromPreferredUnit()` that depended on unavailable `settings` object; now accept `temperatureUnit` parameter
- **Fixed parent-child device tracking** - Parent app now tracks virtual device DNIs via `state.deviceMap[deviceId] = [childAppId: "...", virtualDni: "..."]`; added `updateVirtualDni()` method
- **Fixed child app device lookups** - Child apps now use explicit DNI (`salus_thermostat_${deviceId}`, `salus_switch_${deviceId}`) instead of fragile display name matching
- **Removed `groovy.transform.Field` import from drivers** - `@Field` is for libraries, not drivers
- **Added standard thermostat attributes** - Added `thermostatOperatingState` and `thermostatSetpoint` alias for Thermostat Scheduler compatibility
- **Removed non-standard `supportedSwitchOperations` attribute** from SalusSwitch driver
- **Added command retry logic with exponential backoff** - Added `executeGatewayCommandWithRetry()` with 3 retries at 5s, 15s, 30s delays
- **Created `packageManifest.json`** for Hubitat Package Manager (HPM) compatibility
- **Child apps now use library constants** - Using `SalusCommon.THERMOSTAT_PRESETS`, `THERMOSTAT_MODES`, `THERMOSTAT_FAN_MODES`
- **Explicit deviceNetworkId in child device creation** - Using `salus_thermostat_${deviceId}` and `salus_switch_${deviceId}`
- **Added input validation on parent app settings** - Validates IP/hostname format, EUID format, and poll interval bounds
- **Added event-based parent-child communication** - Parent sends `gatewayUpdate` events; children subscribe and handle via `handleGatewayUpdate()`
- **Added comprehensive build/test documentation to drivers** - Both drivers now include communication flow diagrams and test steps
- **Added logging in handleGatewayResponse** to verify gateway response ID format
- **Fixed `System.arraycopy()` in SalusCommon library** - Replaced with manual loop for Hubitat compatibility
- **Fixed Hubitat driver metadata structure** - Moved all capabilities, attributes, commands, and preferences inside the `definition` closure within `metadata` block
- **Fixed Hubitat driver command syntax** - Removed type parameters from command declarations (e.g., `command "setTemperature"` instead of `command "setTemperature", "NUMBER"`)
- **Fixed `DeviceWrapper` type errors in child apps** - Removed explicit `DeviceWrapper` type declarations; Hubitat child apps don't have access to this class
- **Fixed BigDecimal issue in driver initialization** - Changed decimal literals to double (`22.0d`, `20.0d`) to avoid type mismatch with `setAttribute()`
- **Fixed `setAttribute()` typo** - Corrected `attribute()` to `setAttribute()` for gatewayStatus
- **Fixed primitive double conversion for `setAttribute()`** - Hubitat's `setAttribute()` requires primitive `double`, not `java.lang.Double`; added `toPrimitiveDouble()` helper method using `.doubleValue()` for all numeric `setAttribute()` calls in SalusThermostat driver

### Added
- Event-based parent-child communication architecture
- Input validation for gateway configuration settings
- Hubitat Package Manager (HPM) compatibility via `packageManifest.json`
- Comprehensive build/test documentation in drivers

## [0.1.0] - 2026-09-18

### Added
- Initial port from Home Assistant implementation to Hubitat Elevation
- Parent application: SalusGatewayConnector.groovy for gateway communication
- Child applications: SalusThermostatController.groovy and SalusSwitchController.groovy
- Device drivers: SalusThermostat.groovy and SalusSwitch.groovy
- Common library: SalusCommon.groovy with shared utilities
- AES-256-CBC encryption support for Salus gateway communication
- Configurable polling intervals
- Temperature unit conversion (Celsius/Fahrenheit)
- Support for Salus Universal Gateway (UG) 600
- Support for Salus Wireless Pump Relay Control (AKL04P)
- Support for Salus Wireless Thermostat (AWRT10RF/AS20WRF)

### Security
- EUID token stored as secure setting in Hubitat