# Salus Hubitat Integration

This project ports the Salus iT600 Home Assistant integration to Hubitat Elevation.

## Overview

This integration connects to the Salus Universal Gateway (UG) 600 to provide local control of Salus heating systems, including:
- Salus Wireless Thermostats (AWRT10RF, AS20WRF)
- Salus Wireless Pump Relay Control (AKL04P)
- Other Salus devices connected to the gateway

## Attribution

This integration is based on and incorporates code patterns from several open-source projects:

### Primary Sources
- [Home Assistant Salus iT600 Integration](https://github.com/custom-components/salus) - The original Python implementation that this port is based on
- [salus_it600 Python library](https://pypi.org/project/salus-it600-client/) - Used by the Home Assistant integration for gateway communication

### Hubitat Integration Patterns
- [CoCoHue - Hue Bridge Integration for Hubitat](https://github.com/HubitatCommunity/CoCoHue) - Parent-child architecture, event handling, and caching patterns
- [BlubButtons Hubitat Integration] - Child application and device creation patterns
- [Kasa Hubitat Integration] - Library usage for shared functionality and local-only communication patterns

### Documentation
- [Hubitat Developer Documentation](https://docs2.hubitat.com/)
- [Hubitat Package Manager Documentation](https://hubitatpackageManager.readthedocs.io/)

## Features

- Local LAN-only communication with Salus gateway (no cloud dependency)
- Virtual thermostat devices compatible with Hubitat Thermostat Scheduler and other thermostat-aware apps
- Status display for Salus relay zones (control via gateway only)
- Support for future expansion to other Salus device types
- Robust error handling and reconnection logic
- Configurable polling intervals
- Detailed logging and diagnostics

## Supported Devices

- Salus Universal Gateway (UG) 600
- Salus Wireless Pump Relay Control (4 Zone) model AKL04P
- Salus Wireless Thermostat model AWRT10RF or AS20WRF

## Installation

1. Copy the Groovy files to your Hubitat hub using Hubitat Package Manager or manual upload
2. Install the "Salus Gateway Connector" app
3. Configure the gateway IP address and EUID token in the app settings
4. The app will automatically discover and create devices for connected Salus equipment

## Usage

- Thermostat devices will appear as standard Hubitat thermostats and can be used with apps like Thermostat Scheduler
- Relay zone devices show the current status (on/off) of each zone pump
- All device control must be performed through the Salus gateway - direct control via Hubitat is not supported for safety reasons

## Configuration

- Gateway IP Address: The local IP address of your Salus UG600 gateway
- EUID Token: The security token for your Salus gateway (found in the Salus app or gateway documentation)
- Polling Interval: How often to query the gateway for status updates (default: 5 minutes)

## Notes

- This implementation follows Hubitat best practices for local-only integrations
- All communication with Salus devices occurs via the local gateway
- The integration is designed to be reliable and resilient to network issues
- Future versions may add support for additional Salus device types

## License

This project is licensed under the MIT OR Apache-2.0 license - see the [LICENSE](LICENSE) file for details.

## Version

0.1.0 - Initial port from Home Assistant implementation

