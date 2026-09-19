# DroneWatch

**Flitsmeister voor drones** – een Android-app die passief Remote ID-signalen (ASTM F3411 / OpenDroneID) van drones ontvangt via Bluetooth Low Energy en deze op een kaart, radar en live-lijst toont.

## Belangrijke kenmerken

- **Continu scannen** – geen Start/Stop-knop; scannen start automatisch bij openen van de app
- **Passieve ontvangst alleen** – de app bestuurt, jammer of onderschept geen drones
- **OpenDroneID / ASTM F3411** message parsing (Basic ID, Location, Self-ID, System, Operator ID)
- **Kaart** (OpenStreetMap via osmdroid)
- **Radar-weergave** met afstandsringen (250 m – 2 km)
- **Configureerbare waarschuwingen** met geofence + cooldown
- **Lokale geschiedenis** (Room)
- **Demo mode** met virtuele drones
- **Privacy-first** – Remote ID-data blijft standaard op het apparaat
- **MVVM + Clean Architecture**, Kotlin Coroutines + Flow, Jetpack Compose, Material 3

## Ondersteunde Android-versies

| | |
|---|---|
| Minimum SDK | 29 (Android 10) |
| Target SDK | 35 |
| Compile SDK | 35 |

## Vereisten

- Android Studio Ladybug (2024.2.1) of nieuwer aanbevolen
- JDK 17
- Een fysiek Android-apparaat met Bluetooth LE (emulator heeft meestal geen echte BLE Remote ID)

## Installatie & build

1. Clone of pak dit project uit.
2. Open de map `DroneWatch` in Android Studio (File → Open).
3. Laat Gradle de dependencies synchroniseren.
4. Verbind een Android-apparaat (of start een emulator).
5. Kies de `app` run-configuration en druk op **Run**.

### Command-line build

```bash
cd DroneWatch
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

> **Let op:** de Gradle Wrapper JAR is niet meegeleverd in deze export.  
> Android Studio genereert deze automatisch bij eerste openen, of voer uit:
> ```bash
> gradle wrapper --gradle-version 8.9
> ```

## Permissies

De app vraagt runtime:

| Permissie | Reden |
|-----------|--------|
| `BLUETOOTH_SCAN` / `BLUETOOTH_CONNECT` | Ontvangen van Remote ID BLE-advertisements |
| `ACCESS_FINE_LOCATION` | Afstand & richting berekenen; op oudere Android ook vereist voor BLE-scan |
| `POST_NOTIFICATIONS` | Waarschuwingen bij nabije drones |
| `FOREGROUND_SERVICE_CONNECTED_DEVICE` | Betrouwbaar scannen op de achtergrond (binnen platformlimieten) |

Uitleg wordt in de UI getoond voordat permissies worden gevraagd.

## Remote ID – hoe het werkt

1. Drones die voldoen aan de Europese / Amerikaanse Remote ID-eisen zenden periodiek BLE (en soms Wi-Fi) broadcasts uit met o.a. positie, hoogte, snelheid en identificatie.
2. `RemoteIdScanner` scant continu op BLE advertisements (service UUID `0xFFFA` / OpenDroneID).
3. `OpenDroneIdDecoder` parseert de payload volgens ASTM F3411 message types.
4. Berichten worden samengevoegd tot één `DroneDetection` per unieke drone-ID.
5. Lifecycle: **ACTIVE** → **STALE** (geen update ~10 s) → **LOST** (geen update ~45 s) → verwijderd uit live-lijst.
6. Afstand en bearing worden berekend met de Haversine-formule t.o.v. de telefoon-GPS.

### Beperkingen van detectie

Detectie hangt af van:

- of de drone überhaupt Remote ID uitzendt (firmware, regio, type);
- telefoonhardware en Bluetooth-stack;
- afstand, obstakels, interferentie;
- Android-achtergrondbeperkingen voor BLE-scanning.

**Er wordt geen vaste maximale detectieafstand gegarandeerd.**  
De app claimt nooit dat een drone een politie-, verkeers- of overheidsdrone is zonder betrouwbare externe bron.

## Demo mode

In **Instellingen** → schakel **Demo mode** in.

- Er worden 5 virtuele drones rondom je positie gegenereerd.
- Ze bewegen langzaam.
- Bovenaan staat duidelijk **DEMO MODE**.
- Echte BLE-scanning wordt tijdelijk gestopt.

## Schermen

| Scherm | Functie |
|--------|---------|
| Home | Live status, dichtstbijzijnde drone, automatische scanstatus |
| Live | Sorteerbare lijst + detailvenster |
| Kaart | OpenStreetMap met gebruiker + drone-markers |
| Radar | Relatieve weergave met 250 m / 500 m / 1 km / 2 km ringen |
| Geschiedenis | Lokale Room-database van eerdere detecties |
| Instellingen | Waarschuwingsafstand, timeouts, demo, privacy, debug-export |

## Architectuur

```
app/
  data/
    bluetooth/     RemoteIdScanner
    remoteid/      OpenDroneIdDecoder, RemoteIdDecoder
    database/      Room (geschiedenis)
    location/      Fused Location
    repository/    DroneRepositoryImpl, SettingsRepositoryImpl
  domain/
    model/         DroneDetection, ScannerState, enums
    repository/    interfaces
  ui/
    home, drones, map, radar, history, settings
    navigation, theme, components
  service/         Foreground service voor continu scannen
```

## Tests

```bash
./gradlew test
```

Unit tests dekken o.a.:

- Haversine-afstand & bearing
- OpenDroneID Basic ID & Location parsing
- Ongeldige packets (geen crash)

## iOS later toevoegen

De architectuur is platform-agnostisch op domain-niveau:

- `DroneDetection`, `RemoteIdDecoder`, repositories zijn pure Kotlin / interfaces.
- Voor iOS: CoreBluetooth + een Swift/Kotlin Multiplatform decoder-laag, of een gedeelde KMP-module voor domain + decoder.
- UI blijft native (SwiftUI) of gedeeld via Compose Multiplatform.

## Licenties van gebruikte libraries

| Library | Licentie |
|---------|----------|
| AndroidX / Jetpack | Apache 2.0 |
| Kotlin | Apache 2.0 |
| osmdroid | Apache 2.0 |
| Play Services Location | Google Play Services ToS |
| Accompanist | Apache 2.0 |
| Room | Apache 2.0 |

OpenDroneID-specificatie: open standaard (ASTM F3411).  
Deze app bevat een eigen, beperkte parser; voor productie wordt aangeraden de officiële [OpenDroneID-core](https://github.com/opendroneid/opendroneid-core-c) te integreren wanneer een stabiele Android-binding beschikbaar is.

## Troubleshooting

| Probleem | Oplossing |
|----------|-----------|
| Geen drones zichtbaar | Zet Demo mode aan om UI te testen. Voor echte detectie: drone met Remote ID + BLE in de buurt. |
| “Bluetooth uit” | Zet Bluetooth aan; de app herstart de scanner automatisch. |
| Permissie geweigerd | Ga naar App-info → Permissies en verleen Bluetooth + Locatie. |
| Kaart leeg | Controleer internet (alleen voor tegels). Scanning zelf werkt offline. |
| Achtergrond stopt | Android beperkt BLE-scanning op de achtergrond; foreground service helpt binnen de limieten. |

## Disclaimer

DroneWatch is uitsluitend bedoeld voor legitieme, passieve ontvangst van publiek uitgezonden Remote ID-signalen.  
Misbruik (storen, jammen, besturen, hacken) is verboden en wordt door deze software niet ondersteund.

---

**DroneWatch** – gebouwd met Kotlin, Jetpack Compose & OpenDroneID-principes.
