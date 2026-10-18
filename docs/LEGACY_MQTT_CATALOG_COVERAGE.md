# Legacy MQTT catalog coverage

`LegacyMqttDiscoveryCatalog` is a pure-Java factory for the offline-rendered upstream Model 3 discovery definitions, preferring payloads captured from loaded legacy runtime configs and filling absent entries from the offline render. Runtime discovery topics, unique IDs, device identifiers, state/command topics, and payloads are preserved with validated VIN template substitution. Registry-disabled-by-integration entities remain represented and carry `enabled_by_default: false`; the HA entity registry remains authoritative for actual disabled state.

Every config advertises global `root/availability` and per-entity `root/availability/<component>/<object>` with `availability_mode: all`, preventing retained legacy states alone from appearing fresh. Key deployment/regeneration actions remain recognizable and require confirmation. The software-update action lacking a delay argument and mismatched Model X identity are marked unsupported.

Run with JDK 17: `javac -d /tmp/legacy-mqtt-catalog-classes app/src/main/java/org/opencharge/teslable/LegacyMqttDiscoveryCatalog.java tests/java/LegacyMqttDiscoveryCatalogHarness.java && java -cp /tmp/legacy-mqtt-catalog-classes org.opencharge.teslable.LegacyMqttDiscoveryCatalogHarness`
