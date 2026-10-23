# TeslaBLE for ScreenMate

Read your Tesla's battery and charging information over Bluetooth, then share it with DiPlay on the same ScreenMate.

The app maintains the Bluetooth connection in the background and retries when it drops. You can use it as the battery-data companion for the [ScreenMate DiPlay fork](https://github.com/gilesmartin/DiPlay), or optionally publish data through MQTT.

## What do I need?

Android 8 or later, the required Bluetooth permissions, and your own vehicle key enrollment. The app ships without a VIN, enrolled key, telemetry token or account credentials. The [setup guide](docs/SCREENMATE_SETUP.md) explains how to configure your own unit.

MQTT, Home Assistant and Cloudflare are optional; they are not required to share battery data locally with DiPlay. The separate remote-wake feature is experimental and has not been verified end to end. It cannot run while ScreenMate has no power.

## Getting started

1. [Download the community preview](https://github.com/gilesmartin/Tesla-ble-monitor/releases/tag/screenmate-preview-2026.10.22).
2. Follow the [ScreenMate setup guide](docs/SCREENMATE_SETUP.md) for permissions, vehicle enrollment and local token setup.
3. Enable DiPlay's optional TeslaBLE data source if you want the apps to work together.

This is an unofficial community preview. Software and build tests do not guarantee fresh data on every vehicle, and Apple Maps arrival-charge estimates or charging-stop routing have not been established. Do not use the app as your only source of battery information or change vehicle enrollment just to troubleshoot telemetry.

## More detail

- [Local API and developer notes](docs/LOCAL_API.md)
- [Bluetooth reconnection](docs/RECONNECT.md)
- [Optional MQTT setup](docs/MQTT_SETUP.md)
- [License](LICENSE) and [third-party notices](docs/THIRD_PARTY_NOTICES.md)

The controller uses AGPL-3.0 components. It is not affiliated with Tesla, Apple or ScreenMate. Do not publish vehicle keys, tokens or private diagnostic exports.
