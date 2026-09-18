# TODO.md - Plan for Porting Salus HomeAssistant Integration to Hubitat

## Overview
This plan outlines the steps to port the Salus iT600 HomeAssistant integration to Hubitat Elevation.

## Supported Devices (Limited Scope)
- Salus Universal Gateway (UG) 600
- Salus Wireless Pump Relay Control (4 Zone) model AKL04P
- Salus Wireless Thermostat model AWRT10RF or AS20WRF

## Key Clarifications
- Communication is local LAN only (no cloud fallback)
- Initial scope: thermostat support for radiator/baseboard heating only
- All pump control must go through Salus Gateway
- Virtual thermostat devices must be accessible by Thermostat Scheduler

## Current Status - 2026-09-18

### Completed ✓
- [x] Project structure established: apps/, drivers/, libs/ directories created
- [x] Port_to_Hubitat.md resolved merge conflict
- [x] Framework created with simulated data in SalusGatewayConnector.groovy
- [x] Child applications: SalusThermostatController.groovy, SalusSwitchController.groovy
- [x] Device drivers: SalusThermostat.groovy, SalusSwitch.groovy
- [x] Common library: SalusCommon.groovy
- [x] README.md updated with proper attribution
- [x] AES encryption support implemented (key derivation, fixed IV, PKCS5 padding)
- [x] HTTP POST method structure defined for gateway communication

### In Progress
- [ ] Integrate actual HTTP requests with Hubitat's async HTTP capabilities
- [ ] Test encryption/decryption with real gateway
- [ ] Complete command forwarding between apps and drivers

## Phase 1: Core Implementation

### 1. HTTP Communication Layer (SalusGatewayConnector.groovy)
Implemented AES-256-CBC encryption methods:
- `generateEncryptionKey(euid)` - derives 32-byte key from MD5
- `getFixedIV()` - returns the fixed 16-byte IV from gateway spec
- `encryptPayload(jsonPayload, euid)` - encrypts JSON for gateway
- `fetchGatewayData()` - structured for HTTP POST to `/deviceid/read`

**Pending:**
- Replace simulated data with actual `httpPost` call
- Add `httpPost` response handler method
- Implement async callback processing

### 2. Command Execution Layer
Implemented command mapping in `executeGatewayCommand()`:
- `setTemperature` - sends heating setpoint to thermostat
- `setPresetMode` - sends hold type (standby, permanent_hold, away)
- `setThermostatLock` - locks/unlocks thermostat keypad
- `turnOnSwitch` / `turnOffSwitch` - controls relay zones

**NOT IMPLEMENTED** (per scope):
- `set_cover_position`, `open_cover`, `close_cover` - cover devices not in scope

### 3. Device Discovery & State Sync
- Device discovery loop in `processGatewayData()`
- Child app creation/deletion for thermostats and switches
- State update methods for both device types

## Phase 2: Hubitat Integration

### Files Structure (Hubitat Package Manager compatible)
```
/hubitat_salus/
├── apps/
│   ├── SalusGatewayConnector.groovy    # Parent application
│   ├── SalusThermostatController.groovy  # Child app
│   └── SalusSwitchController.groovy       # Child app
├── drivers/
│   ├── SalusThermostat.groovy            # Driver
│   └── SalusSwitch.groovy                  # Driver
├── libs/
│   └── SalusCommon.groovy                  # Shared library
├── README.md
├── Port_to_Hubitat.md
└── TODO.md
```

## Technical Reference - Salus Gateway API

### Encryption Scheme
```
Key derivation: MD5("Salus-{euid.lower()}") + 16 zero bytes
IV: 16 bytes (fixed)
Mode: AES-256-CBC
Padding: PKCS5/PKCS7
```

### API Endpoints
```
Base URL: http://{host}:80/deviceid/{command}
Methods: POST only
Headers: Content-Type: application/json
Body: Encrypted JSON bytes (not base64, raw bytes)
```

### Commands
| Command | Method | Description |
|---------|--------|-------------|
| read | `{"requestAttr": "readall"}` | Get all device status |
| read | `{"requestAttr": "deviceid", "id": [...]}` | Get specific devices |
| write | `{"requestAttr": "write", "id": [{"data": {...}}, ...]}` | Send commands |

## Next Steps

1. **Hubitat HTTP Integration**: Replace simulated data with actual HTTP POST
   - Use `asynchttpPost()` in Groovy
   - Implement response handler method
   - Parse decrypted JSON response

2. **Testing**
   - Validate encryption matches gateway expectations
   - Test device discovery with real hardware
   - Verify thermostat control via Hubitat UI

3. **Remaining TODOs** (see below)

## Scope Exclusions (per Port_to_Hubitat.md)
These device types are kept as stubs/comments:
- Cover/Garage Door devices
- Contact/binary sensors (other than thermostat-related)
- Temperature/Humidity sensors
- Lock devices

---

## TODO - Remaining Tasks

### High Priority
1. Implement actual HTTP POST in fetchGatewayData()
2. Add response handler method for async HTTP
3. Test with real Salus gateway

### Medium Priority
4. Implement child app -> parent command forwarding
5. Add device capability attributes
6. Add proper error handling for network timeouts

### Low Priority (Documentation/Polish)
7. Add inline code documentation
8. Prepare for Hubitat Package Manager submission

## Rollback Plan
If HTTP integration fails, keep simulated data with clear "NOT PRODUCTION" warnings and document the expected gateway API structure for future manual implementation.

## Files Modified This Session
- TODO.md - Updated with status and next steps
- README.md - Added attribution section
- apps/SalusGatewayConnector.groovy - Added AES encryption methods and HTTP structure
- Port_to_Hubitat.md - Resolved merge conflict
