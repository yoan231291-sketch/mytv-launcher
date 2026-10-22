# MyTV Launcher

Launcher para Fire TV / Android TV con **cámaras en vivo de 16 ciudades** de fondo, clima y hora reales, y acceso directo a Stremio, Netflix, Disney+, YouTube, Apple TV y Prime Video.

```
docs/            → la interfaz (GitHub Pages)  https://yoan231291-sketch.github.io/mytv-launcher/
docs/cities.js   → ciudades, cámaras y apps (edítalo aquí y la TV se actualiza sola)
android/         → app nativa: WebView a pantalla completa + abre apps reales
.github/workflows→ compila el APK y lo publica en Releases (tag "latest")
scripts/         → instalación en la TV por ADB (Wi-Fi)
```

## Instalar en la TV
1. TV: **Configuración → Mi Fire TV → Opciones de desarrollador → Depuración ADB: Sí**.
   (Si no ves "Opciones de desarrollador": Mi Fire TV → Acerca de → presiona OK 7 veces sobre el nombre.)
2. IP de la TV: **Mi Fire TV → Acerca de → Red**.
3. En la Mac: `./scripts/firetv-setup.sh <IP> --clean` y acepta el aviso en la TV.

Deshacer todo: `./scripts/firetv-setup.sh <IP> --restore`

## Control remoto
| Botón | Acción |
|---|---|
| ▲ ▼ | Elegir app · OK la abre |
| ◄ ► | Cambiar de ciudad |
| ⏩ / ⏪ | Cambiar de cámara |
| ⏯ | Modo cine (solo la cámara) |
| ☰ | Todas las apps |
| Atrás | Modo cine / cerrar |

## Cómo funciona como inicio en Fire TV
Fire OS no permite cambiar el launcher. MyTV incluye un servicio de accesibilidad que detecta cuando aparece el inicio de Amazon (Home o encendido) y abre MyTV encima. No lee el contenido de la pantalla.

## Cambiar o agregar cámaras
Edita `docs/cities.js` en GitHub → agrega el ID del video en vivo de YouTube en `cams`. Si una cámara se cae, el launcher salta sola a la siguiente.
