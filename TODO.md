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
- [x] Project structure established: apps/, drivers/, libs/
- [x] Port_to_Hubitat.md resolved merge conflict
- [x] Framework created with HTTP infrastructure
- [x] AES-256-CBC encryption implemented
- [x] Child applications: SalusThermostatController.groovy, SalusSwitchController.groovy
- [x] Device drivers: SalusThermostat.groovy, SalusSwitch.groovy
- [x] Common library: SalusCommon.groovy
- [x] README.md updated with proper attribution

### HTTPS Implementation ✓
- [x] `generateEncryptionKey()` - MD5("Salus-{euid}") derivation
- [x] `getFixedIV()` - Returns fixed 16-byte IV
- [x] `encryptPayload()` - AES-256-CBC with PKCS5 padding
- [x] `fetchGatewayData()` - Prepares HTTP POST to `/deviceid/read`
- [x] `executeGatewayCommand()` - Maps commands to gateway API
- [x] `gatewayResponseHandler()` - Async HTTP response handler
- [x] Command mappings for: setTemperature, setPresetMode, setThermostatLock, turnOnSwitch, turnOffSwitch

### Test Gateway Available
Hostname: `salus-gateway`
EUID: `001E5E09021F3160`

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

## Next Steps (Ready for Testing)

1. **Deploy to Hubitat**:
   - Upload SalusGatewayConnector.groovy as an app
   - Install and configure with gateway hostname "salus-gateway" and EUID "001E5E09021F3160"

2. **Test Connection**:
   - Verify HTTP POST to gateway works
   - Verify encryption produces valid requests
   - Verify response parsing

3. **Known Limitations**:
   - Simulated data return in fetchGatewayData() - needs actual httpPost call
   - Need to verify decryption of responses works
   - Cover devices intentionally not implemented (out of scope)

## Files Structure
```
/hubitat_salus/
├── apps/
│   ├── SalusGatewayConnector.groovy    # Parent app with HTTP implementation
│   ├── SalusThermostatController.groovy # Child app
│   └── SalusSwitchController.groovy      # Child app
├── drivers/
│   ├── SalusThermostat.groovy            # Driver
│   └── SalusSwitch.groovy                  # Driver
├── libs/
│   └── SalusCommon.groovy                  # Shared utilities
├── README.md, Port_to_Hubitat.md, TODO.md
```

---
## Approval Ready

The implementation is ready for testing with the provided gateway credentials.
