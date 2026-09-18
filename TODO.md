# TODO.md - Plan for Porting Salus HomeAssistant Integration to Hubitat

## Overview
This plan outlines the steps to port the Salus iT600 HomeAssistant integration to Hubitat Elevation, following the specifications in `Port_to_Hubitat.md` and incorporating best practices observed from production Hubitat projects.

## Supported Devices (Limited Scope)
- Salus Universal Gateway (UG) 600
- Salus Wireless Pump Relay Control (4 Zone) model AKL04P
- Salus Wireless Thermostat model AWRT10RF or AS20WRF

References to other Salus devices will be carried over as comments or stubs for future extension.

## Key Clarifications
- Communication is local LAN only (no cloud fallback)
- Initial scope: thermostat support for radiator/baseboard heating only (no cooling/fan modes)
- Gateway communicates with thermostats and relay controller
- Hubitat app reads status via gateway and sends commands to gateway
- Virtual thermostat devices for each thermostat (accessible by other Hubitat apps like Thermostat Scheduler)
- Relay zone controller shows status (on/off per zone), not direct control
- All pump actions via commands to Salus Gateway

## Current Status
- [x] Project structure established: apps/, drivers/, libs/ directories created
- [x] Port_to_Hubitat.md created and cleaned up
- [x] Parent application framework: SalusGatewayConnector.groovy
- [x] Child applications implemented:
  - SalusThermostatController.groovy
  - SalusSwitchController.groovy
- [x] Device drivers implemented:
  - SalusThermostat.groovy (Thermostat capability)
  - SalusSwitch.groovy (Switch capability for status display)
- [x] Common library created: SalusCommon.groovy
- [x] README.md updated with proper attribution
- [IN PROGRESS] Implementing real HTTP communication with Salus gateway
  - AES-256-CBC encryption with fixed IV
  - HTTP POST to /deviceid/{command} endpoint
  - Key derivation: MD5("Salus-{euid.lower()}")
  - PKCS7 padding

## Phase 1: Project Setup and Exploration
1. [x] Examine the existing HomeAssistant implementation
   - [x] Reviewed salus-it600-client library
   - [x] Understood AES encryption scheme
   - [x] Identified API endpoints and data formats
2. [x] Study example Hubitat projects
3. [x] Set up Groovy project structure

## Phase 2: Implement Real HTTP Communication
5. [IN PROGRESS] Create the parent application: Salus Gateway Connector
   - [x] Implement definition() and preferences
   - [x] Implement lifecycle methods
   - [IN PROGRESS] Implement gateway connection logic
   - [IN PROGRESS] Implement polling mechanism with HTTP calls
   - [ ] Implement command execution methods (set_temperature, set_preset, etc.)
   - [ ] Add robust error handling

## Phase 3: Complete Child Applications
6. [ ] Create the child application template: Salus Device Controller
   - [ ] Implement status update handling
   - [ ] Implement command forwarding

## Phase 4: Device Driver Development
7. [x] Salus Thermostat Driver - completed
   - [x] Implement Thermostat capability
   - [x] Support heatingSetpoint, temperature, presetMode
   - [x] Compatibility with Thermostat Scheduler app
- [ ] Salus Switch Driver - completed (status display only)

## NOT IN SCOPE (per Port_to_Hubitat.md limitations)
- Cover devices (open/close/stop) - use comments/stubs
- Lock devices - use comments/stubs  
- Binary sensors (other than thermostat-related) - use comments/stubs
- Multi-temperature zone thermostats - not supported

## Next Implementation Steps

### Step 1: Update SalusGatewayConnector.groovy with HTTP methods
- Add AES-256-CBC encryption helper methods
- Implement `connect()` method
- Implement `poll_status()` method using HTTP POST
- Implement command methods:
  - `setTemperature(deviceId, temperature)` - for heating only
  - `setPresetMode(deviceId, preset)`
  - `lockThermostat(deviceId, locked)`
- **NOT implementing**: `set_cover_position`, `open_cover`, `close_cover`

### Step 2: Update child applications
- Implement status update reception from parent
- Handle device state changes

### Step 3: Testing
- Verify HTTPS calls work in Hubitat environment
- Test device discovery
- Test thermostat control

## Technical Notes

### Salus Gateway API (from salus-it600-client library)
- Base URL: `http://{host}:80/deviceid/{command}`
- Headers: `Content-Type: application/json`
- Encryption: AES-256-CBC with:
  - Fixed IV: 16 bytes of constants
  - Key: MD5("Salus-{euid.lower()}") + 16 zero bytes
  - Padding: PKCS7
- Commands: "read" (for get data) and "write" (for send commands)

### Hubitat HTTP Implementation
- Use `asynchttp_get/post` for async calls
- Use Java Cipher and SecretKeySpec for AES
- Use MessageDigest for MD5

### Device Endpoints
- `read` + `requestAttr: "readall"` - get all devices
- `read` + `requestAttr: "deviceid"` - get detailed device info by ID

## Rollback Plan
If implementation fails, revert to simulated data and document API requirements for manual implementation.

## Approval
Awaiting approval to proceed with HTTP implementation.
