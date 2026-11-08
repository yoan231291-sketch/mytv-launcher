#!/bin/zsh
# =====================================================================
#  MyTV en cualquier TV con Fire OS o Android TV / Google TV.
#  Instala el launcher y activa todo: inicio propio, control del teléfono (casa e internet),
#  teclas/texto dentro de YouTube y otras apps, foto de la pantalla, título de lo que suena,
#  Bluetooth, dibujo encima de cualquier app.
#
#  Uso:  ./setup-tv.sh 192.168.1.50              instalar / actualizar
#        ./setup-tv.sh 192.168.1.50 --apk X.apk  usar un APK propio (p. ej. compilado en la Mac)
#        ./setup-tv.sh 192.168.1.50 --check      solo revisar que todo esté funcionando
#        ./setup-tv.sh 192.168.1.50 --restore    dejar la TV como venía (teclado, inicio)
#
#  Antes: en la TV, activar "Depuración ADB" (ver SKILL / README) y tener la Mac en el mismo Wi-Fi.
# =====================================================================
IP="$1"; MODE="$2"; APK_ARG="$3"; PKG="com.yoan.mytv"
[ -z "$IP" ] && { echo "Falta la IP de la TV. Ej: ./setup-tv.sh 192.168.1.50"; exit 1; }
command -v adb >/dev/null || { echo "Instalando adb…"; brew install --cask android-platform-tools; }
D="$IP:5555"; A(){ adb -s "$D" "$@"; }; S(){ adb -s "$D" shell "$@"; }
ok(){ echo "  ✅ $1"; }; bad(){ echo "  ❌ $1"; FAIL=1; }; FAIL=0

echo "▶ Conectando a $IP (si la TV pregunta '¿Permitir depuración?', marca 'Permitir siempre')…"
adb connect "$D" >/dev/null; sleep 2
for i in 1 2 3 4 5 6 7 8 9 10; do A get-state >/dev/null 2>&1 && break; sleep 3; done
A get-state >/dev/null 2>&1 || { echo "No conecta. Revisa que la Depuración ADB esté activa y la IP sea correcta."; exit 1; }
MODEL=$(S getprop ro.product.model | tr -d '\r'); BRAND=$(S getprop ro.product.manufacturer | tr -d '\r')
FIREOS=$(S getprop ro.build.version.name | tr -d '\r'); SDK=$(S getprop ro.build.version.sdk | tr -d '\r')
echo "  TV: $BRAND $MODEL · Android SDK $SDK · $FIREOS"
SAVE="$HOME/Claude/tv-respaldos/$IP.txt"; mkdir -p "$(dirname "$SAVE")"

if [ "$MODE" = "--restore" ]; then
  ORIG=$(grep -m1 '^IME=' "$SAVE" 2>/dev/null | cut -d= -f2)
  [ -n "$ORIG" ] && S ime set "$ORIG" && echo "  teclado original: $ORIG"
  S settings delete secure enabled_accessibility_services; S settings put secure accessibility_enabled 0
  echo "✔ Listo: la TV vuelve a su inicio y teclado originales. Para borrar MyTV: adb -s $D uninstall $PKG"; exit 0
fi

if [ "$MODE" != "--check" ]; then
  # Respaldo de lo que vamos a cambiar (solo la primera vez)
  [ -f "$SAVE" ] || { echo "IME=$(S settings get secure default_input_method | tr -d '\r')" > "$SAVE"; echo "A11Y=$(S settings get secure enabled_accessibility_services | tr -d '\r')" >> "$SAVE"; }

  if [ "$MODE" = "--apk" ] && [ -n "$APK_ARG" ]; then APK="$APK_ARG"
  else echo "▶ Descargando la última versión de MyTV…"; APK=$(mktemp -d)/mytv.apk
       curl -sL -o "$APK" https://github.com/yoan231291-sketch/mytv-launcher/releases/download/latest/mytv-launcher.apk; fi
  echo "▶ Instalando…"
  if ! A install -r "$APK" | tail -1 | grep -q Success; then
    echo "  (firma distinta a la instalada: reinstalando desde cero)"; A uninstall $PKG >/dev/null 2>&1; A install "$APK" | tail -1
  fi

  echo "▶ Activando permisos…"
  # Inicio propio: se apaga y vuelve a prender el servicio (tras reinstalar, Android no lo reconecta si el valor no cambia)
  S settings delete secure enabled_accessibility_services; sleep 1
  S settings put secure enabled_accessibility_services "$PKG/$PKG.HomeWatcherService"
  S settings put secure accessibility_enabled 1
  S appops set $PKG GET_USAGE_STATS allow                                   # saber qué app está al frente
  S ime enable "$PKG/.RemoteIme" >/dev/null; S ime set "$PKG/.RemoteIme" >/dev/null   # teclas y texto dentro de otras apps
  S 'cmd notification allow_listener com.yoan.mytv/com.yoan.mytv.NowPlaying\$Listener' >/dev/null 2>&1   # título de lo que suena
  # Fire TV Edition (TV con Fire OS integrado): la TV debe seguir escuchando la red apagada para poder prenderla desde el teléfono
  [ -n "$(S settings get secure str.auto_wake_up_enabled | tr -d '\r' | grep -v null)" ] && S settings put secure str.auto_wake_up_enabled 1
  S input keyevent 224; sleep 1                                              # despertar la pantalla
  S am start -n "$PKG/.MainActivity" >/dev/null; sleep 12

  echo "▶ Permiso de foto de la pantalla (se acepta solo, con 'No volver a mostrar')…"
  LAN=$(S dumpsys activity service $PKG/.HomeWatcherService | grep MYTV_CONTROL_CASA | cut -d= -f2 | tr -d '\r')
  curl -s -m 8 "${LAN}api/shot" >/dev/null; sleep 2
  S uiautomator dump /sdcard/mytv-ui.xml >/dev/null 2>&1
  XML=$(S cat /sdcard/mytv-ui.xml)
  tap(){ local b; b=$(echo "$XML" | grep -oE "text=\"$1\"[^>]*bounds=\"\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]\"" | grep -oE '\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]' | head -1)
         [ -z "$b" ] && return 1; local n=(${(s:,:)${b//[\[\]]/,}}); S input tap $(( (n[2]+n[4])/2 )) $(( (n[3]+n[5])/2 )); }
  if echo "$XML" | grep -qiE "Start now|Iniciar ahora|Empezar ahora"; then
    tap "Don't show again" || tap "No volver a mostrar"; sleep 0.5
    tap "Start now" || tap "Iniciar ahora" || tap "Empezar ahora"; sleep 2
  fi
  S am start -n "$PKG/.MainActivity" >/dev/null; sleep 3
fi

echo "▶ Revisión"
DUMP=$(S dumpsys activity service $PKG/.HomeWatcherService | tr -d '\r')
LAN=$(echo "$DUMP" | grep MYTV_CONTROL_CASA | cut -d= -f2); NET=$(echo "$DUMP" | grep MYTV_CONTROL_INTERNET | cut -d= -f2); DRAW=$(echo "$DUMP" | grep MYTV_DIBUJO | cut -d= -f2)
[ -n "$LAN" ] && ok "Servicio de inicio activo" || bad "Servicio de inicio NO activo (repite la instalación)"
[ "$(S settings get secure default_input_method | tr -d '\r')" = "$PKG/.RemoteIme" ] && ok "Teclado de control activo" || bad "Teclado de control no activo"
echo "$DUMP" | grep -q "MYTV_FOTO=lista" && ok "Foto de la pantalla lista" || bad "Foto de la pantalla sin permiso (aparece en la TV: acepta 'Iniciar ahora')"
STATE=$(curl -s -m 8 "${LAN}api/state"); echo "$STATE" | grep -q '"on"' && ok "Control de la casa responde" || bad "Control de la casa no responde"
NOW=$(curl -s -m 8 "${LAN}api/now"); echo "$NOW" | grep -q '"app"' && ok "Sabe qué app está al frente: $(echo "$NOW" | grep -oE '"app":"[^"]*"')" || bad "No sabe qué app está al frente (appops)"
echo "$NOW" | grep -q "sin permiso de sesiones" && bad "Sin permiso para leer títulos (notification listener)" || ok "Puede leer títulos de lo que suena"
BT=$(curl -s -m 8 "${LAN}api/bt"); ok "Bluetooth: $(echo "$BT" | grep -oE '"name":"[^"]*"' | tr '\n' ' ')"

echo
echo "════════ ENLACES DE ESTA TV ════════"
echo "Control (casa y fuera, recomendado): $NET"
echo "Control solo con el Wi-Fi de casa:   $LAN"
echo "Dibujar en la TV:                    $DRAW"
echo "Guárdalos en el teléfono: Safari → Compartir → Agregar a pantalla de inicio."
{ echo "# $(date) $BRAND $MODEL"; echo "INTERNET=$NET"; echo "CASA=$LAN"; echo "DIBUJO=$DRAW"; } >> "$SAVE"
[ $FAIL = 0 ] && echo "✔ Todo funcionando." || echo "⚠ Hay puntos en ❌: revisa arriba."
