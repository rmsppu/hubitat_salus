/* Id: $Id$ */
/* Date: $Date$ */
/* Commit: $NextCommitNum$ */

# Changelog

All notable changes to the Salus Hubitat Integration project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Fixed
- Fixed string concatenation error in `SalusGatewayConnector.groovy` - removed extra double quotes in `setThermostatLock` command payload that would cause malformed JSON
- Fixed typo in `SalusGatewayConnector.groovy` - changed `state.gollInterval` to `state.pollInterval` in scheduling function
- Fixed hubitat scheduling cron expression in `SalusGatewayConnector.groovy` - changed from `"*/${state.pollInterval} * * * ? * * *"` to `"0 */${state.pollInterval} * * * ?"` for proper Hubitat compatibility
- Removed invalid C preprocessor directives (`#ifdef FALSE`, `#if`, `#endif`) from `SalusSwitch.groovy` that would cause Groovy compilation errors
- Removed unused import `com.hubitat.app.DeviceWrapper` from `SalusThermostat.groovy`

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
