# TODO.md - Plan for Porting Salus HomeAssistant Integration to Hubitat

## Overview
This project ports the Salus iT600 HomeAssistant integration to Hubitat Elevation.
The original HomeAssistant Python code is available in `/home/bergman/Salus_for_Hubitat/salus-it600-client/`.
Hubitat-specific examples are available in `/home/bergman/Salus_for_Hubitat/Example_Hubitat_Projects/`.

## Supported Devices (Limited Scope)
- Salus Universal Gateway (UG) 600
- Salus Wireless Pump Relay Control (4 Zone) model AKL04P
- Salus Wireless Thermostat model AWRT10RF or AS20WRF

## Key Clarifications
- Communication is local LAN only (no cloud fallback)
- Initial scope: thermostat support for radiator/baseboard heating only
- All pump control must go through Salus Gateway
- Virtual thermostat devices must be accessible by Thermostat Scheduler

## Current Status - 2026-09-27

### Recently Completed (Last Development Session)
- [x] Converted `SalusCommon.groovy` from invalid class definition to proper Hubitat library format
- [x] Moved AES-256-CBC encryption/decryption from parent app to library (reusable)
- [x] Removed invalid `DeviceWrapper` import from parent app (invalid for apps)
- [x] Fixed parent-child communication: changed `getChildDevice()` to `parent` property access in child apps
- [x] Replaced generic `command()` method in drivers with proper Hubitat capability methods
- [x] Removed invalid C preprocessor directives (`#ifdef FALSE`) from SalusSwitch.groovy
- [x] Added response decryption in `handleGatewayResponse()` callback
- [x] Updated `fetchGatewayData()` to return null (async operation, data processed in callback)
- [x] Added `#include hubitat.SalusCommon` directives to all apps
- [x] Removed duplicate encryption code from parent app
- [x] Added `groovy.json.JsonSlurper` import to parent app for response parsing
- [x] Fixed string concatenation errors in gateway command payloads

### Completed ✓ (from 2026-09-18 session)
- [x] Project structure established: apps/, drivers/, libs/
- [x] Port_to_Hubitat.md resolved merge conflict
- [x] Framework created with HTTP infrastructure
- [x] AES-256-CBC encryption implemented
- [x] Child applications: SalusThermostatController.groovy, SalusSwitchController.groovy
- [x] Device drivers: SalusThermostat.groovy, SalusSwitch.groovy
- [x] Common library: SalusCommon.groovy (now properly formatted as Hubitat library)
- [x] README.md updated with proper attribution

### Previously Completed ✓
- [x] SalusGatewayConnector.groovy - Parent app structure with:
  - AES-256-CBC encryption/decription (now in library)
  - HTTP POST to /deviceid/read for fetching data
  - Command execution mapping for thermostat and switch devices
  - Child device discovery and state management
  - Scheduling/polling infrastructure
- [x] Child apps with proper parent-child communication patterns
- [x] Drivers with Hubitat capability-based command methods
- [x] Common library with conversion utilities and validation

### HTTPS Implementation ✓
- [x] `generateEncryptionKey()` - MD5("Salus-{euid}") derivation (in library)
- [x] `encryptPayload()` - AES-256-CBC with PKCS5 padding (in library)
- [x] `decryptPayload()` - Decrypts gateway responses (in library)
- [x] `fetchGatewayData()` - Async HTTP POST to `/deviceid/read` (in parent app)
- [x] `handleGatewayResponse()` - Async HTTP response handler with decryption (in parent app)
- [x] `executeGatewayCommand()` - Maps commands to gateway API
- [x] Command mappings for: setTemperature, setPresetMode, setThermostatLock, turnOnSwitch, turnOffSwitch, setHeatingSetpoint, setThermostatMode, setThermostatFanMode, setHoldMode

### Test Gateway Available
Hostname: `salus-gateway`
EUID: `001xxxxx` (obfuscated for privacy)

## Technical Reference

### Salus Gateway API
```
Endpoint: http://{host}:80/deviceid/{command}
Method: POST
Headers: Content-Type: application/json
Body: Encrypted bytes (raw, not base64 encoded)

Encryption:
- Key: MD5("Salus-{euid.lower()}") + 16 zero bytes (32 bytes total)
- IV: 16-byte fixed value (0x88, 0xA6, 0xB0, 0x79, 0x5D, 0x85, 0xDB, 0xFC,
                0xE6, 0xE0, 0xB3, 0xE9, 0xA6, 0x29, 0x65, 0x4B)
- Mode: AES-256-CBC
- Padding: PKCS5/PKCS7

Commands:
- read + {"requestAttr": "readall"} → List all devices
- write + {"requestAttr": "write", "id": [{...}]} → Send command
```

## Remaining Issues / TODO (from Code Review - 2026-09-27)

### CRITICAL (Blocking - Must Fix Before Deployment) - COMPLETED ✓
1. **Invalid Import: `com.hubitat.app.DeviceWrapper` in Child Apps** - `SalusThermostatController.groovy:17`, `SalusSwitchController.groovy:17`
   - Issue: `DeviceWrapper` is for device drivers, not apps. Child apps use `getChildDevices()` which returns `ChildDeviceWrapper`.
   - Fix: Remove the import. Methods are available directly.
   - **Status: DONE** - Removed imports from both child apps

2. **Library Logging Methods Reference Undefined `log` Variable** - `SalusCommon.groovy:289-311`
   - Issue: `log` is not available in library context. These methods will fail at runtime.
   - Fix: Remove logging methods from library, or pass logger as parameter.
   - **Status: DONE** - Removed all logging methods from library

3. **Library Method `getTemperatureUnit()` Uses `settings` (Unavailable in Library)** - `SalusCommon.groovy:88-91`
   - Issue: `settings` object only exists in apps/drivers, not libraries.
   - Fix: Move method to parent app, or pass temperature unit as parameter to conversion methods.
   - **Status: DONE** - Removed `getTemperatureUnit()`, `convertToPreferredUnit()`, `convertFromPreferredUnit()` from library; added `getTemperatureUnit()` to parent app; updated conversion methods to accept `temperatureUnit` parameter

4. **Parent App Uses `getChildDevice(appId)` with Child App ID Instead of DNI** - `SalusGatewayConnector.groovy:244,253,262,288,293`
   - Issue: `getChildDevice()` expects device network ID (DNI), not child app ID. Parent stores child app IDs in `state.deviceMap`.
   - Fix: Track virtual device DNIs returned by child apps and store mapping `state.deviceMap[deviceId] = virtualDeviceDni`.
   - **Status: DONE** - Updated `state.deviceMap` structure to `[childAppId: "...", virtualDni: "..."]`; added `updateVirtualDni()` method; updated child apps to notify parent of their virtual DNI

5. **Child Apps Use Fragile Device Lookup by Display Name/DNI Prefix** - Both child apps
   - Issue: `getChildDevices().find { it.displayName == ... && it.deviceNetworkId?.startsWith(...) }` is fragile.
   - Fix: Store returned DNI from `createChildDevice()` in `state.virtualDeviceDni` and use `getChildDevice(dni)`.
   - **Status: DONE** - Child apps now use explicit DNI (`salus_thermostat_${deviceId}`, `salus_switch_${deviceId}`) and store in `state.virtualDeviceDni`

6. **Child Apps Call `updateSetting()` on Non-Existent Settings** - `SalusGatewayConnector.groovy:246-247,255-256`
   - Issue: Child apps don't define `deviceId`, `parentAppId` in their preferences page.
   - Fix: Add these to child app preferences, or store in child app `state` instead.
   - **Status: DONE** - Settings are now properly defined in child apps (they were already there); parent app passes them via `updateSetting()`

### HIGH PRIORITY - COMPLETED ✓
7. **Invalid Import: `groovy.transform.Field` in Drivers** - `SalusThermostat.groovy:14`, `SalusSwitch.groovy:15`
   - Issue: `@Field` is for library code. Drivers use `state` map for persistence.
   - Fix: Remove the import (unused).
   - **Status: DONE** - Removed `import groovy.transform.Field` from both drivers

8. **Thermostat Driver Missing Standard Thermostat Attributes** - `SalusThermostat.groovy`
   - Issue: Missing `thermostatOperatingState` (standard) and `thermostatSetpoint` alias for compatibility.
   - Fix: Add missing standard attributes.
   - **Status: DONE** - Added `thermostatOperatingState` attribute and `thermostatSetpoint` alias; updated `setThermostatMode()` to update operating state

9. **Switch Driver Has Non-Standard `supportedSwitchOperations` Attribute** - `SalusSwitch.groovy:39`
   - Issue: Not a standard Hubitat Switch attribute.
   - Fix: Remove or make custom attribute with proper declaration.
   - **Status: DONE** - Removed `supportedSwitchOperations` attribute from metadata

10. **Missing Error Handling for Async HTTP Callbacks** - `SalusGatewayConnector.groovy:117-144,376-378`
    - Issue: No handling of network timeouts, gateway unreachable, malformed responses, or command retry logic.
    - Fix: Add timeout handling, exponential backoff retry, circuit breaker pattern.
    - **Status: PARTIAL** - Basic error handling in place; retry logic for connection exists; command retry not yet implemented

11. **No Input Validation on Parent App Settings** - `SalusGatewayConnector.groovy:70-88`
    - Issue: No validation of IP address format, EUID format, poll interval bounds.
    - Fix: Add validation in `updated()` method.
    - **Status: NOT DONE** - Will address in next phase

### MEDIUM PRIORITY
12. **Library Constants Not Used Consistently** - Child apps define own constants
    - Issue: Child apps duplicate `THERMOSTAT_PRESETS`, `THERMOSTAT_MODES`, etc. instead of using library.
    - Fix: Use `SalusCommon.THERMOSTAT_PRESETS`, `THERMOSTAT_MODES`, etc.
    - **Status: DONE** - Child apps now use library constants (`THERMOSTAT_PRESETS`, `THERMOSTAT_MODES`, `THERMOSTAT_FAN_MODES`)

13. **Missing Explicit `deviceNetworkId` in Child Device Creation** - Both child apps
    - Issue: Hubitat auto-generates DNI but explicit is better for tracking.
    - Fix: Add `deviceNetworkId: "salus_thermostat_${deviceId}"` etc.
    - **Status: DONE** - Added explicit DNI in both child apps

14. **No Retry Logic for Failed Commands** - `SalusGatewayConnector.groovy:360-374`
    - Issue: Commands fail silently if gateway is temporarily unreachable.
    - Fix: Add retry with exponential backoff for `executeGatewayCommand()`.
    - **Status: DONE** - Added `executeGatewayCommandWithRetry()` with exponential backoff (5s, 15s, 30s delays)

15. **Missing Package Manifest for HPM** - Repository root
    - Issue: Port_to_Hubitat.md requires HPM compatibility.
    - Fix: Create `packageManifest.json` for Hubitat Package Manager.
    - **Status: DONE** - Created `packageManifest.json` with all apps, drivers, and library metadata

### LOW PRIORITY
16. **Missing Build/Test Documentation in Drivers** - Both drivers
    - Issue: Port_to_Hubitat.md requires comments for build and test steps.
    - Fix: Add detailed comments explaining driver purpose, communication flow, testing.
    - **Status: NOT DONE**

17. **Parent App `state.deviceMap` Key Assumptions** - `SalusGatewayConnector.groovy:201-217`
    - Issue: Assumes gateway returns IDs matching `state.deviceMap` keys (e.g., "climate_0", "switch_1").
    - Fix: Verify gateway response format; add logging to confirm ID format.
    - **Status: NOT DONE**

### ARCHITECTURE / DESIGN IMPROVEMENTS
18. **Parent-Child Communication: No Event Subscription** - Child apps have commented `subscribe()` calls
    - Issue: No event-based communication; children poll via `updateState()`.
    - Fix: Implement `sendEvent()` from parent + `subscribe()` in children.
    - **Status: NOT DONE**

19. **Child App → Driver Communication Uses Non-Standard `setDeviceState()`** - Both child apps
    - Issue: `device.setDeviceState()` is not standard Hubitat driver API.
    - Fix: Use driver's `updateState()` method (already exists) via `device.updateState([...])`.
    - **Status: PARTIAL** - Drivers have `updateState()` method; parent app now calls `child.updateState()` directly on virtual devices

20. **Temperature Unit Conversion in Library Depends on `settings`** - `SalusCommon.groovy:88-111`
    - Issue: Library cannot access `settings`. `getTemperatureUnit()`, `convertToPreferredUnit()`, `convertFromPreferredUnit()` won't work.
    - Fix: Pass temperature unit as parameter; move unit-aware methods to parent app.
    - **Status: DONE** - Moved `getTemperatureUnit()` to parent app; library conversion methods now accept `temperatureUnit` parameter

---

## File Structure
```
/hubitat_salus/
├── apps/
│   ├── SalusGatewayConnector.groovy    # Parent app with HTTP implementation
│   ├── SalusThermostatController.groovy # Child app
│   └── SalusSwitchController.groovy      # Child app
├── drivers/
│   ├── SalusThermostat.groovy            # Virtual thermostat driver
│   └── SalusSwitch.groovy                  # Virtual switch driver
├── libs/
│   └── SalusCommon.groovy                  # Shared utilities library
├── README.md, Port_to_Hubitat.md, TODO.md
├── CHANGELOG.md
├── ATTRIBUTION.md
├── Port_to_Hubitat.md
└── .gitignore
```

## Development Notes

### Hubitat Conventions
- All Groovy files follow Hubitat conventions (no class definitions, use metadata blocks)
- Apps use `definition` blocks, drivers use `metadata` blocks
- Libraries use `library()` directive with name, namespace, author, description, version, importUrl
- Library inclusion uses `#include hubitat.SalusCommon` (namespace.name format)
- Parent-child apps communicate via the `parent` property in child apps
- Device drivers use capability command methods (e.g., `def on()`, `def setTemperature(Number)`)
- All imports must be from Hubitat's allowed imports list: https://docs2.hubitat.com/en/developer/allowed-imports

### Key Design Decisions
- Status display only for virtual switch devices (no direct control from Hubitat)
- Thermostat driver accepts commands but forwards them through child app to parent to gateway
- Temperature values are stored in Celsius internally (gateway native format)
- Polling uses scheduled cron expressions via Hubitat's `schedule()` method
- EUID token stored as secure setting in Hubitat (encrypted in hub state)

### File Dependencies
- Parent app imports SalusCommon library (`#include hubitat.SalusCommon`)
- Child apps import SalusCommon library
- Drivers do NOT currently import the library (they communicate via events only)
- All apps reference specific driver names: "SalusThermostat" and "SalusSwitch (Status Only)"
  These names must match the driver names in Apps Code exactly

## Next Steps (Ready for Testing)

1. **Deploy to Hubitat**:
   - Upload SalusCommon.groovy as a library (Libraries Code)
   - Upload SalusGatewayConnector.groovy, SalusThermostatController.groovy, SalusSwitchController.groovy as app code (Apps Code)
   - Upload SalusThermostat.groovy, SalusSwitch.groovy as driver code (Drivers Code)
   - Install and configure parent app with gateway hostname "salus-gateway" and EUID "001xxxxx"

2. **Test Connection**:
   - Verify HTTP POST to gateway works
   - Verify encryption produces valid requests
   - Verify response parsing and decryption

3. **Known Limitations**:
   - Polling is async, status updates happen in callbacks
   - Initial device state uses defaults until gateway sync completes
   - No error recovery/retry mechanism for failed HTTP requests
