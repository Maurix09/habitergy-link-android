# Habitergy Link (Android)

App nativa Android para la **adopción de controladores Shelly** en Habitergy. Comparte el design system M3 con `@habitergy/manager` (ver `packages/design-tokens/habitergy-m3.json`).

## Requisitos

- Android Studio Ladybug (2024.2+) o más reciente
- JDK 17
- Android SDK 35
- Emulador API 34+ (recomendado: Pixel 6)

## Abrir el proyecto

1. Android Studio → **Open** → seleccionar `apps/link-android/`
2. Esperar Gradle Sync
3. Crear un AVD (Virtual Device) si no tenés uno
4. Run ▶ en emulador o dispositivo físico

Desde terminal (con `ANDROID_HOME` configurado):

```bash
cd apps/link-android
./gradlew :app:assembleDebug
./gradlew :app:installDebug   # requiere emulador/dispositivo conectado
```

## Flujo actual (mock)

### Paso 1 — Identificá el controlador

- Ingresar código (`CX123`, `SH-AB12`, `SH-T3ST`) → lookup mock de MAC en “base de datos”
- **Escanear QR** → simula lectura de `CX123` (sin cámara en esta iteración)
- **No tengo el código** → modo adopción sin MAC conocida
- **Siguiente** → paso 2

### Paso 2 — Bluetooth

- Escaneo BLE simulado (~2 s)
- **Con MAC (paso 1 con código):** banner “Controlador encontrado” si coincide
- **Sin MAC:** lista para elegir manualmente entre Shellys mock
- **Continuar** → diálogo placeholder del paso 3 (WiFi)

## Códigos mock

| Código   | MAC                 | Modelo           |
|----------|---------------------|------------------|
| CX123    | 3C:E8:1A:12:34:56   | Shelly 1PM Gen3  |
| SH-AB12  | 3C:E8:1A:12:34:56   | Shelly 1PM Gen3  |
| SH-T3ST  | 8A:13:BF:AB:CD:EF   | Shelly 1PM Gen4  |

## Estructura

```
app/src/main/java/com/habitergy/link/
├── MainActivity.kt
├── data/mock/MockAdoptionData.kt
├── domain/model/AdoptionModels.kt
└── ui/
    ├── adoption/          # ViewModel + pantallas paso 1–2
    ├── components/        # Scaffold, botones, tarjetas Shelly
    └── theme/             # Tokens M3 Habitergy
```

## Próximos pasos

- [ ] BLE real (`BluetoothLeScanner` + filtros Allterco)
- [ ] QR con CameraX + ML Kit
- [ ] API real `GET /api/devices/code/{deviceCode}`
- [ ] Paso 3: provisioning WiFi vía RPC-over-BLE
- [ ] Deep link desde Manager PWA

## Relación con Manager

Manager PWA sigue siendo el panel del partner. Link es el flujo de vinculación física del Shelly. En Android, el botón “Adoptar controlador” del Manager debería abrir Link vía App Link.

## Releases

Versión actual definida en `version.properties`. Para publicar en GitHub:

```bash
./scripts/release.sh patch   # 0.1.x -> 0.1.(x+1), default
./scripts/release.sh minor   # 0.1.x -> 0.2.0, solo cuando se pida
```

Repo standalone: https://github.com/Maurix09/habitergy-link-android
