# TODO.md - Plan for Porting Salus HomeAssistant Integration to Hubitat

## Overview
This plan outlines the steps to port the Salus iT600 HomeAssistant integration to Hubitat Elevation, following the specifications in `Port_to_Hubitat.md` and incorporating best practices observed from production Hubitat projects (CoCoHue, BlubButtons, Kasa devices).

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

## Phase 1: Project Setup and Exploration
1. [ ] Examine the existing HomeAssistant implementation to understand data structures and communication patterns
   - Review `custom_components/salus/` directory
   - Understand the `salus_it600` library usage (gateway communication, device models)
   - Identify the API endpoints and data formats used by the Salus gateway
2. [ ] Research Hubitat development best practices from the provided documentation links
3. [ ] Study the example Hubitat projects to understand patterns:
   - CoCoHue: Parent-child structure, event streaming, caching, V1/V2 API handling
   - BlubButtons: App creating child devices, passing data through deviceData
   - Kasa: Library usage for shared code, cloud/local communication fallback (note: we only need local)
4. [ ] Set up the Groovy project structure within the hubitat_salus directory
   - Create `apps/` directory for parent and child applications
   - Create `drivers/` directory for device drivers

## Phase 2: Parent Application Development
5. [ ] Create the parent application: Salus Gateway Connector
   - Implement `definition()` with appropriate metadata (name, namespace, etc.)
   - Set menu: "Integrations" (following CoCoHue pattern)
   - Add preferences for gateway configuration (IP address, EUID token, polling interval)
   - Implement `installed()` and `updated()` lifecycle methods
   - Implement gateway connection logic (adapt from `salus_it600.gateway.IT600Gateway`)
   - Implement polling mechanism to fetch device status from the gateway (local LAN only)
   - Implement device discovery: iterate through gateway devices (climate, binary_sensor, switch, cover, sensor, lock)
   - For each discovered device, create a child application instance with unique device ID
   - For each removed device, remove the corresponding child application instance
   - Implement methods for child applications to send commands to the gateway
   - Implement error handling and reconnection logic
   - Consider implementing event streaming if the Salus gateway supports it (like CoCoHue)

## Phase 3: Child Application Development
6. [ ] Create the child application template: Salus Device Controller
   - Implement `definition()` for a generic child application
   - Add preferences specific to the device type (if needed)
   - Implement lifecycle methods (`installed()`, `updated()`)
   - On installation, create one or more Hubitat devices using the appropriate device driver
   - Pass device-specific information (gateway device ID, device type) to the created device via device data
   - Store reference to parent application device network ID for communication
   - Implement methods to receive command updates from the parent application
   - Implement methods to send status updates to the parent application (if needed)
   - Handle device removal when the parent application signals device disappearance

## Phase 4: Device Driver Development
7. [ ] Create device drivers for supported device types, following patterns from examples:
   - **Salus Thermostat Driver** (for AWRT10RF/AS20WRF)
     - Implement Thermostat capability
     - Support attributes: temperature, heatingSetpoint (coolingSetpoint and thermostatFanMode not needed for radiator heating)
     - Support commands: setTemperature, setHeatingSetpoint (setCoolingSetpoint and setThermostatMode not needed)
     - Map commands to gateway API calls via parent application
     - Include detailed logging and error handling
     - Ensure compatibility with Hubitat Thermostat Scheduler app
   - **Salus Switch Driver** (for AKL04P relay zones - status only)
     - Implement Switch capability (for each of the 4 zones)
     - Support attributes: switch (read-only/status only)
     - Do NOT support commands: on, off (control only via gateway)
     - Map status updates from gateway API calls via parent application
     - Consider implementing separate child devices for each zone (like CoCoHue groups) for status display
   - **Salus Contact Sensor Driver** (for binary sensors like window/door)
     - Implement Contact Sensor capability
     - Support attribute: contact
     - Update status from gateway polling via parent application
   - **Salus Garage Door Driver** (for cover devices if applicable)
     - Implement Garage Door capability
     - Support attributes: door
     - Support commands: open, close, stop
   - **Salus Sensor Driver** (for temperature/humidity sensors)
     - Implement Relative Humidity Measurement and Temperature Measurement capabilities
     - Support attributes: humidity, temperature
   - **Salus Lock Driver** (for lock devices if applicable)
     - Implement Lock capability
     - Support attributes: lock
     - Support commands: lock, unlock

## Phase 5: Communication and Data Flow
8. [ ] Implement communication between parent and child applications
   - Parent application exposes methods for child applications to send commands (e.g., `sendCommandToDevice(deviceId, command, params)`)
   - Child applications store parent device network ID and call parent methods to execute actions
   - Parent application polls gateway for status updates (local LAN only, or uses event streaming if available)
   - Parent application pushes status updates to child applications (via device events or state changes)
   - Child applications update their created Hubitat devices with the latest status
   - Implement caching mechanisms to reduce gateway API calls (like CoCoHue's bridge cache)

9. [ ] Implement state synchronization
   - When parent application receives updated device status from gateway, notify relevant child applications
   - Child applications update their Hubitat device attributes accordingly
   - When Hubitat device receives a command (via device driver), child application forwards command to parent application
   - Parent application translates command to gateway API call and executes it
   - After command execution, parent application requests status update to reflect change

## Phase 6: Advanced Features (Following Examples)
10. [ ] Consider implementing library classes for shared functionality (like Kasa's kasaCommon and kasaCommunications)
11. [ ] Implement proper logging with different levels (debug, info, warn, error)
12. [ ] Add support for automatic debug timeout (like the examples)
13. [ ] Implement robust error handling and reconnection logic
14. [ ] Consider implementing event streaming if Salus gateway supports server-sent events
15. [ ] Add support for Hubitat Package Manager compliance

## Phase 7: Testing and Validation
16. [ ] Create test scenarios for each supported device type
    - Thermostat: temperature reading, setpoint changes (heating only)
    - Relay switch: status display for each zone (on/off)
    - Binary sensors: open/closed state detection
    - Cover devices: open/close/stop (if applicable)
    - Sensor devices: temperature/humidity readings
    - Lock devices: lock/unlock (if applicable)
17. [ ] Implement logging and diagnostics for troubleshooting
18. [ ] Validate compliance with Hubitat best practices from the documentation links
19. [ ] Ensure proper error handling and edge cases (e.g., gateway unreachable, invalid responses)

## Phase 8: Documentation and Finalization
20. [ ] Update README.md with Hubitat-specific installation and configuration instructions
21. [ ] Create inline documentation in Groovy code following self-documenting principles
22. [ ] Review and refine code for performance and reliability
23. [ ] Prepare for Hubitat Package Manager submission (if desired)

## Notes Based on Examples
- Follow the parent-child pattern seen in CoCoHue and BlubButtons where the parent app creates child devices
- Use device data to pass configuration information from apps to drivers (like BlubButtons)
- Consider implementing libraries for shared code (like Kasa driver)
- Use @Field for static variables that need to persist between executions
- Implement proper preference pages for configuration
- Handle both initialization and updates properly
- Consider implementing caching to reduce API calls to the gateway
- Follow Hubitat naming conventions and structure for applications and drivers

## Approval Required
Please review this plan and provide approval before proceeding with implementation. Once approved, I will begin implementing the parent application.

--- 

End of plan. Awaiting your approval to proceed.
