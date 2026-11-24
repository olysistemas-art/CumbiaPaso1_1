# Cumbia Paso 1 — v2.1 (Android)

Pestañas:
- **Ritmos**: güira, bajo, conga y timbal con rejilla de 16 pasos editable
  (apagado → golpe → acento), 3 estilos (Colombiana, Sonidera, Mexicana),
  volumen y silencio por instrumento, BPM con + / −.
- **Música**: carga una canción del teléfono; detecta BPM y golpes graves,
  vibra en cada golpe, pone la guía de cumbia encima y cambia la velocidad
  (70 %–115 %) sin cambiar el tono. Ajuste fino de BPM, desfase y tiempo 1.
  **Sonido de la canción**: Original, Solo bajo, Bajo y tambores, Sin güira,
  Sin bajo (filtro por frecuencias de 24 dB/oct; no es separación por IA).
  Genera un archivo temporal la primera vez (unos segundos) y lo reutiliza.
- **Práctica**: escucha el bajo con el micrófono y vibra en el tiempo 1.
- **Aprende**: guía corta de tiempo 1, paso básico y uso de la app.

## Tus propios sonidos
Crea la carpeta `app/src/main/res/raw/` y pon dentro archivos con estos
nombres (minúsculas, sin espacios): `guira`, `bajo`, `conga`, `timbal`
(.wav, .mp3 u .ogg, cortos: 1 a 3 segundos). Los que falten se generan
sintéticos. Al subir cambios a GitHub se recompila el APK.

## Compilar
GitHub: pestaña Actions → Build APK → artefacto `CumbiaPaso1-APK`.
Local (JDK 17 + Android SDK): `./gradlew assembleDebug`
