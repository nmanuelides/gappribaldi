# Gappribaldi

App Android (Kotlin + Compose) de diagnóstico OBD para un Mercedes-Benz C350 W204. Ver README.md.

## Cada versión nueva (obligatorio)

1. **Versión**: subir `apk=` en `version.properties` en 1. La versión sale de ahí: apk 1 → v0.01.0,
   apk 105 → v1.05.0 (ver `app/build.gradle.kts`). El `versionCode` es la cantidad de commits.
2. **Changelog**: agregar al final de `news.json` un mensaje
   `{"id": "v0.02.0", "title": "Novedades de la v0.02.0", "body": "• ...", "title_en": "New in v0.02.0", "body_en": "• ...", "link": null}`.
   La app muestra cada id una sola vez, y un changelog solo cuando esa versión ya está instalada.
   Mensajes que no son de una versión (avisos) usan un id con fecha, p. ej. `2026-11-20-aviso`.
3. **Compilar, testear, commitear y pushear** a `main`: el push dispara `.github/workflows/release.yml`,
   que publica el release `build-<versionCode>` con el APK firmado. Las apps instaladas lo ven al abrirse
   y muestran "Versión nueva disponible". Cambios solo en `news.json` o `*.md` no generan release
   (las novedades se leen directo del repo, sirven para avisos sin versión nueva).

## Firma y GitHub

- La clave de release está en `~/.android-keys/gappribaldi-release.jks` + `gappribaldi-release.properties`
  (nunca se commitea). CI usa los secrets `RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`,
  `RELEASE_KEY_PASSWORD` y `RELEASE_KEY_ALIAS` del repo. Si se pierde la clave, las apps instaladas no pueden
  actualizarse (habría que desinstalar e instalar de nuevo).
- APK de release local, firmado igual que CI:
  ```bash
  P=~/.android-keys/gappribaldi-release.properties; g(){ grep "^$1=" $P | cut -d= -f2-; }
  export RELEASE_KEYSTORE="$(g storeFile)" RELEASE_KEYSTORE_PASSWORD="$(g password)" RELEASE_KEY_PASSWORD="$(g password)" RELEASE_KEY_ALIAS="$(g alias)"
  ./gradlew :app:assembleRelease   # queda en dist/gappribaldi-v<versión>.apk
  ```
- Usar la cuenta personal `nmanuelides` **solo en este repo**; nunca cambiar la cuenta global de `gh`
  (la activa es la del trabajo). El repo ya tiene un credential helper local que usa
  `gh auth token --user nmanuelides`, así que `git push` funciona directo.

## Instalar en el teléfono

adb inalámbrico; la instalación directa se corta con APKs grandes, así que copiar primero:
```bash
adb push dist/gappribaldi-v*.apk /data/local/tmp/g.apk && adb shell "pm install -r /data/local/tmp/g.apk; rm /data/local/tmp/g.apk"
```
