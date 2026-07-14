# Habitergy Link (Android)

App nativa Android para la **adopción de controladores Shelly** en Habitergy. Comparte el design system M3 con `@habitergy/manager` (ver `packages/design-tokens/habitergy-m3.json`).

**Versión actual:** ver `version.properties` (publicada en https://github.com/Maurix09/habitergy-link-android).

## Requisitos

- Android Studio Ladybug (2024.2+) o más reciente
- JDK 17
- Android SDK 35
- Emulador API 34+ (recomendado: Pixel 6)
- Backend en `https://api.habitergy.com` (lookup de `device_code` vía API)

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

## Códigos de controlador

Los `device_code` comparten el algoritmo **nanoId** con los `siteCode` de alojamientos:

| Tipo | Formato | Ejemplo |
|------|---------|---------|
| Alojamiento (`siteCode`) | `XXXXC` | `KX67W` |
| Controlador (`deviceCode`) | `SH-XXXXC` | `SH-KX67W` |

En el paso 1, la UI muestra el prefijo `SH-` fijo y el usuario ingresa los **5 caracteres del sufijo** (cuerpo + checksum). El mismo sufijo `KX67W` es válido tanto como site como como parte de `SH-KX67W`.

Para probar con datos de seed:

```bash
pnpm --filter @habitergy/database seed
# Imprime: Adoptable device (available): SH-XXXXX
```

## Flujo actual

### Paso 1 — Identificá el controlador (real)

- Ingresar sufijo de 5 chars → validación **checksum nanoId** local
- Si el checksum es válido → `GET /api/adoption/devices/SH-XXXXC`
- Estados: disponible (verde), asignado, no encontrado, error de red
- **Escanear QR** → placeholder («Coming soon»)
- **¿No tenés el código?** → avanza al paso 2 sin MAC

### Paso 2 — Bluetooth (placeholder)

- Pantalla informativa; escaneo BLE real pendiente

## Estructura

```
app/src/main/java/com/habitergy/link/
├── MainActivity.kt
├── domain/
│   ├── DeviceCode.kt          # Sufijo nanoId + prefijo SH-
│   └── model/AdoptionModels.kt
├── data/
│   ├── api/                   # Ktor → apps/api
│   └── AdoptionRepository.kt
└── ui/
    ├── adoption/              # ViewModel + pantallas paso 1–2
    ├── components/
    └── theme/
```

## Próximos pasos

- [ ] BLE real (`BluetoothLeScanner` + filtros Allterco)
- [ ] QR con CameraX + ML Kit
- [x] API lookup `GET /api/adoption/devices/:deviceCode`
- [x] Checksum nanoId unificado con `siteCode`
- [ ] Paso 3: provisioning WiFi vía RPC-over-BLE
- [ ] Deep link desde Manager PWA

## Relación con Manager

Manager PWA sigue siendo el panel del partner. Link es el flujo de vinculación física del Shelly. En Android, el botón «Adoptar controlador» del Manager debería abrir Link vía App Link.

## Releases

```bash
./scripts/release.sh patch   # 0.1.x -> 0.1.(x+1), default
./scripts/release.sh minor   # 0.1.x -> 0.2.0, solo cuando se pida
```

Repo standalone: https://github.com/Maurix09/habitergy-link-android

Más contexto para agentes: `AGENTS.md`.
