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

## Remaining Issues / TODO

### High Priority
1. **Verify encryption output**: The encryption in `SalusCommon.groovy` needs to be tested with the actual gateway to ensure the byte array output matches what the gateway expects (the httpPost body: encrypted should work properly)
2. **Test gateway connection**: Verify HTTP POST to gateway works with test credentials
3. **Test response decryption**: Verify the gateway's response can be properly decrypted and parsed

### Medium Priority
4. **Test full device lifecycle**: 
   - Parent app discovers devices on gateway
   - Creates child apps for thermostat and switch devices
   - Child apps create virtual devices with proper drivers
   - State updates flow from gateway → parent → child -> driver
5. **Verify command flow**: Commands from driver → child app → parent app → gateway
6. **Test with Hubitat Thermostat Scheduler app**: Ensure virtual thermostat devices are compatible
7. **Verify polling schedule**: Test that scheduled status polling works correctly with cron expression

### Low Priority
8. **Cover devices**: Intentionally not implemented (out of scope for initial version)
9. **Binary sensors**: Not implemented (out of scope for initial version)
10. **Locks**: Not implemented (out of scope for initial version)

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
