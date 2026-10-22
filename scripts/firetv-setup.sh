#!/bin/zsh
# =====================================================================
#  MyTV en Fire TV — instala el launcher, lo deja como inicio y limpia apps.
#  Uso:  ./firetv-setup.sh 192.168.1.50            (instalar / actualizar)
#        ./firetv-setup.sh 192.168.1.50 --clean    (además, apaga apps de Amazon que no usas)
#        ./firetv-setup.sh 192.168.1.50 --restore  (deshace la limpieza y quita MyTV como inicio)
# =====================================================================
set -e
IP="$1"; MODE="$2"; PKG="com.yoan.mytv"
[ -z "$IP" ] && { echo "Falta la IP de la TV. Ej: ./firetv-setup.sh 192.168.1.50"; exit 1; }
command -v adb >/dev/null || brew install --cask android-platform-tools

LOG="$(dirname "$0")/firetv-$(date +%Y%m%d-%H%M%S).log"
exec > >(tee -a "$LOG") 2>&1

echo "▶ Conectando a $IP (acepta el aviso en la TV la primera vez)…"
adb connect "$IP:5555"; sleep 2
adb -s "$IP:5555" get-state >/dev/null || { echo "No conecta. Revisa 'Depuración ADB' en la TV."; exit 1; }
A=(adb -s "$IP:5555")
echo "Modelo: $(${A[@]} shell getprop ro.product.model) · Fire OS: $(${A[@]} shell getprop ro.build.version.name)"

# Apps de Amazon que se pueden apagar sin romper la TV (todas reversibles con --restore)
BLOAT=(
  com.amazon.hedwig                 # notificaciones/promos
  com.amazon.dp.logger              # telemetría
  com.amazon.device.metrics         # telemetría
  com.amazon.tv.csapp               # anuncios de contenido
  com.amazon.aria                   # sugerencias
  com.amazon.ags.app                # GameCircle
  com.amazon.bueller.photos         # Amazon Photos
  com.amazon.imdb.tv.android.app    # Freevee
  com.amazon.kindle.devicecontrols
)

if [ "$MODE" = "--restore" ]; then
  for p in $BLOAT; do ${A[@]} shell pm enable "$p" 2>/dev/null && echo "  ↺ $p"; done
  ${A[@]} shell settings delete secure enabled_accessibility_services
  ${A[@]} shell settings put secure accessibility_enabled 0
  echo "✔ Restaurado. MyTV sigue instalada pero ya no reemplaza el inicio."; exit 0
fi

echo "▶ Descargando la última versión de MyTV…"
TMP=$(mktemp -d); curl -sL -o "$TMP/mytv.apk" https://github.com/yoan231291-sketch/mytv-launcher/releases/download/latest/mytv-launcher.apk
if ! ${A[@]} install -r "$TMP/mytv.apk"; then
  echo "  (firma distinta: reinstalando)"; ${A[@]} uninstall com.yoan.mytv >/dev/null 2>&1 || true
  ${A[@]} install "$TMP/mytv.apk"
fi

echo "▶ Activando MyTV como pantalla de inicio…"
# Apagar y volver a encender: tras reinstalar, la TV no reconecta el servicio si el valor no cambia
${A[@]} shell settings delete secure enabled_accessibility_services; sleep 1
${A[@]} shell settings put secure enabled_accessibility_services "$PKG/$PKG.HomeWatcherService"
${A[@]} shell settings put secure accessibility_enabled 1

if [ "$MODE" = "--clean" ]; then
  echo "▶ Apagando apps de Amazon que no usas…"
  ${A[@]} shell settings put global window_animation_scale 0.5
  ${A[@]} shell settings put global transition_animation_scale 0.5
  ${A[@]} shell settings put global animator_duration_scale 0.5
  for p in $BLOAT; do
    if ${A[@]} shell pm disable-user --user 0 "$p" >/dev/null 2>&1; then echo "  ✓ $p"; else echo "  · $p (no existe o protegida)"; fi
  done
fi

echo "▶ Abriendo MyTV…"
${A[@]} shell am start -n "$PKG/.MainActivity" >/dev/null
sleep 8; ${A[@]} exec-out screencap -p > "$(dirname "$0")/tv-captura.png"
echo "✔ Listo. Captura de la TV: scripts/tv-captura.png · Log: $LOG"
