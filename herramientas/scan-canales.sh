#!/usr/bin/env bash
#
# scan-canales.sh v2 — Caracterización de canales HLS y MPEG-TS
#
# Genera la tabla de códecs, PIDs y flags de TS que necesita el módulo
# de TV en vivo (fork jellyfin-androidtv) antes de escribir código.
#
# Cambios respecto a v1:
#   - Arreglado el fallo con `set -u` que truncaba CHANNELS.md cuando
#     algún array quedaba vacío (arrays ahora inicializados con =()).
#   - Soporte de URLs HLS (.m3u8) además de MPEG-TS progresivo.
#   - Para HLS analiza el playlist (TARGETDURATION, nº de segmentos).
#   - Reporta SAR/DAR y entrelazado, que afectan al render.
#   - Texto de decisiones alineado con soportar MPEG-2 + MP2 nativo.
#
# Salidas en ./out/:
#   <canal>.probe.json     ffprobe crudo (streams + programs + format)
#   <canal>.report.txt     reporte legible por canal
#   <canal>.headers.txt    cabeceras HTTP
#   <canal>.playlist.m3u8  playlist capturado (solo HLS)
#   <canal>.analyze.txt    análisis tsduck si está disponible
#   samples/<canal>.ts     muestra capturada (golden sample)
#   CHANNELS.md            tabla consolidada lista para el repo
#
# Uso:
#   ./scan-canales.sh                 # captura 15s por canal
#   ./scan-canales.sh -t 60           # captura 60s (mejor para errores CC)
#   ./scan-canales.sh -t 30 -k        # conserva las muestras .ts
#   ./scan-canales.sh -o /ruta/salida
#
# ─────────────────────────────────────────────────────────────────────
# Traído al repositorio el 2026-10-05 (pendiente 20). Vivía suelto en
# /home/scan-canales.sh, que es mal sitio para la herramienta que produce
# la fuente de verdad.
#
# Al traerlo se corrigieron dos desfases respecto a lo que el proyecto
# decidió después de escribirlo:
#
#   1. Las URLs apuntaban a `mono.m3u8`. Desde el 02-oct el endpoint es
#      `index.m3u8`, el master: `mono.m3u8` multiplexa los audios en un
#      solo TS y Media3 sólo expone uno, así que medir sobre él da un
#      inventario de pistas que no es el que ve la app (CLAUDE.md §6).
#   2. La cabecera decía «soportar MPEG-2 + MP2 nativo». El audio se
#      normaliza a AAC en Flussonic desde el 22-sep y la app no lleva
#      decoders de MP2 (CLAUDE.md §2). El script sigue **midiendo** lo que
#      venga, que es lo correcto; lo que ya no vale son sus recomendaciones
#      de decoders.
#
# El `CHANNELS.md` que genera sale en `out/` y es un borrador: el del
# repositorio lleva análisis escrito a mano que esto no reproduce. Se
# copian datos de uno a otro, nunca el archivo entero.
# ─────────────────────────────────────────────────────────────────────

set -uo pipefail

# ─────────────────────────────────────────────────────────────────────
# CANALES A ANALIZAR — editar aquí. Formato: "nombre|url"
# Acepta HLS (.m3u8) y MPEG-TS progresivo indistintamente.
# ─────────────────────────────────────────────────────────────────────
CANALES=(
  "beisbol|http://192.168.11.250:8181/beisbol/index.m3u8"
  "mileniosd|http://192.168.11.250:8181/mileniosd/index.m3u8"
  "estrellassd-mp2v|http://192.168.11.250:8181/test-sat77w-estrellassd-mp2v/index.m3u8"
  "estrellashd-mp4|http://192.168.11.250:8181/test-sat77w-estrellashd-mp4/index.m3u8"
  "discoveryhd-70w|http://192.168.11.250:8181/test-sat70w-discoveryhd-mp4/index.m3u8"
  "hbohd-70w|http://192.168.11.250:8181/test-sat70w-hbohd-mp4/index.m3u8"
)

DURACION=15
OUTDIR="./out"
KEEP_SAMPLES=0

while getopts "t:o:kh" opt; do
  case $opt in
    t) DURACION="$OPTARG" ;;
    o) OUTDIR="$OPTARG" ;;
    k) KEEP_SAMPLES=1 ;;
    h) sed -n '2,32p' "$0"; exit 0 ;;
    *) echo "Opción inválida. -h para ayuda."; exit 1 ;;
  esac
done

# ─────────────────────────────────────────────────────────────────────
# Dependencias
# ─────────────────────────────────────────────────────────────────────
falta=0
for cmd in ffprobe ffmpeg curl jq bc; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "FALTA: $cmd"; falta=1
  fi
done
if [ "$falta" -eq 1 ]; then
  echo
  echo "Instala con:  sudo apt update && sudo apt install -y ffmpeg curl jq bc"
  exit 1
fi

HAS_TSDUCK=0
command -v tsanalyze >/dev/null 2>&1 && HAS_TSDUCK=1
if [ "$HAS_TSDUCK" -eq 0 ]; then
  echo "AVISO: tsduck no instalado. Se omite el análisis de errores CC por PID."
  echo "       Instalar (opcional, recomendado): https://tsduck.io/download/tsduck/"
  echo
fi

mkdir -p "$OUTDIR/samples"

# ─────────────────────────────────────────────────────────────────────
# Acumuladores — inicializados con =()
#
# Este era el bug de la v1: con `set -u`, un array declarado pero nunca
# asignado rompe ${#array[@]} y aborta la generación del CHANNELS.md a
# media escritura. Se manifestaba solo cuando el resultado era bueno
# (ningún canal con Content-Length => array nunca asignado).
# ─────────────────────────────────────────────────────────────────────
declare -a FILAS_MD=()
declare -a CODECS_AUDIO_RAROS=()
declare -a CANALES_MPEG2=()
declare -a CANALES_CONTENT_LENGTH=()
declare -a CANALES_FALLIDOS=()
NECESITA_FFMPEG_EXT=0

# ─────────────────────────────────────────────────────────────────────
# Análisis por canal
# ─────────────────────────────────────────────────────────────────────
for entrada in "${CANALES[@]}"; do
  NOMBRE="${entrada%%|*}"
  URL="${entrada#*|}"
  SAMPLE="$OUTDIR/samples/${NOMBRE}.ts"
  REPORT="$OUTDIR/${NOMBRE}.report.txt"
  PROBE="$OUTDIR/${NOMBRE}.probe.json"
  HEADERS="$OUTDIR/${NOMBRE}.headers.txt"
  PLAYLIST="$OUTDIR/${NOMBRE}.playlist.m3u8"

  # Modo de transporte según la URL
  if [[ "$URL" == *.m3u8* ]]; then MODO="HLS"; else MODO="TS"; fi

  echo "════════════════════════════════════════════════════════════"
  echo "  $NOMBRE   [$MODO]"
  echo "  $URL"
  echo "════════════════════════════════════════════════════════════"

  {
    echo "CANAL:      $NOMBRE"
    echo "URL:        $URL"
    echo "TRANSPORTE: $MODO"
    echo "FECHA:      $(date -Iseconds)"
    echo "MUESTRA:    ${DURACION}s"
    echo
  } > "$REPORT"

  # ── 1. HTTP / playlist ──────────────────────────────────────────
  echo "  [1/5] Revisando HTTP..."
  RIESGO_CL="n/a"

  if [ "$MODO" = "HLS" ]; then
    curl -s -D "$HEADERS" --max-time 8 "$URL" -o "$PLAYLIST" 2>/dev/null
    HTTP_STATUS=$(head -1 "$HEADERS" 2>/dev/null | tr -d '\r')

    if [ -s "$PLAYLIST" ]; then
      SEG_DUR=$(grep -m1 -i 'EXT-X-TARGETDURATION' "$PLAYLIST" | tr -d '\r' | cut -d: -f2)
      N_SEG=$(grep -c '^[^#].' "$PLAYLIST" 2>/dev/null | head -1)
      IS_MASTER=$(grep -ci 'EXT-X-STREAM-INF' "$PLAYLIST" 2>/dev/null | head -1)
      N_SEG=${N_SEG:-0}; IS_MASTER=${IS_MASTER:-0}
      {
        echo "── HLS ───────────────────────────────────────────────"
        echo "Status:             ${HTTP_STATUS:-sin respuesta}"
        echo "TARGETDURATION:     ${SEG_DUR:-n/d} s"
        echo "Segmentos listados: $N_SEG"
        echo "¿Master playlist?:  $([ "$IS_MASTER" -gt 0 ] && echo "SI ($IS_MASTER variantes)" || echo "no (media playlist)")"
        echo
        echo "Contenido del playlist:"
        head -20 "$PLAYLIST"
        echo
      } >> "$REPORT"
      echo "        ✓ TARGETDURATION=${SEG_DUR:-?}s, $N_SEG segmentos"
      [ "$IS_MASTER" -gt 0 ] && echo "        ⚠  es master playlist: el player intentará ABR"
    else
      echo "        ✗ playlist vacío o inaccesible"
      echo "ERROR: no se pudo descargar el playlist." >> "$REPORT"
    fi
  else
    curl -s -D "$HEADERS" -o /dev/null --max-time 4 "$URL" 2>/dev/null
    HTTP_STATUS=$(head -1 "$HEADERS" 2>/dev/null | tr -d '\r')
    CONTENT_LENGTH=$(grep -i '^content-length:' "$HEADERS" 2>/dev/null | tr -d '\r' | head -1)
    CONTENT_TYPE=$(grep -i '^content-type:' "$HEADERS" 2>/dev/null | tr -d '\r' | head -1)
    TRANSFER_ENC=$(grep -i '^transfer-encoding:' "$HEADERS" 2>/dev/null | tr -d '\r' | head -1)
    {
      echo "── HTTP ──────────────────────────────────────────────"
      echo "Status:             ${HTTP_STATUS:-sin respuesta}"
      echo "Content-Type:       ${CONTENT_TYPE:-ausente}"
      echo "Transfer-Encoding:  ${TRANSFER_ENC:-ausente}"
      echo "Content-Length:     ${CONTENT_LENGTH:-ausente (correcto para live)}"
      echo
    } >> "$REPORT"

    RIESGO_CL="no"
    if [ -n "${CONTENT_LENGTH:-}" ]; then
      RIESGO_CL="SI"
      CANALES_CONTENT_LENGTH+=("$NOMBRE")
      echo "        ⚠  Content-Length presente: riesgo de preparación infinita"
    fi
  fi

  # ── 2. Captura de la muestra ────────────────────────────────────
  echo "  [2/5] Capturando ${DURACION}s..."
  T0=$(date +%s.%N)
  if [ "$MODO" = "HLS" ]; then
    timeout $((DURACION + 25)) ffmpeg -nostdin -hide_banner -loglevel warning \
      -i "$URL" -map 0:v:0 -map 0:a -c copy -t "$DURACION" \
      -f mpegts -y "$SAMPLE" \
      2>"$OUTDIR/${NOMBRE}.capture.log" </dev/null
  else
    curl -s --max-time "$DURACION" "$URL" -o "$SAMPLE" \
      2>"$OUTDIR/${NOMBRE}.capture.log"
  fi
  T1=$(date +%s.%N)

  if [ ! -s "$SAMPLE" ]; then
    echo "        ✗ Sin datos. Motivo reportado por la herramienta:"
    sed 's/^/            /' "$OUTDIR/${NOMBRE}.capture.log" 2>/dev/null | head -8
    {
      echo "ERROR: no se recibieron datos."
      echo "--- salida de la captura ---"
      cat "$OUTDIR/${NOMBRE}.capture.log" 2>/dev/null
    } >> "$REPORT"
    CANALES_FALLIDOS+=("$NOMBRE")
    FILAS_MD+=("| $NOMBRE | $MODO | ERROR | - | - | - | - | - | - | - |")
    echo
    continue
  fi

  BYTES=$(stat -c%s "$SAMPLE")
  SEGS=$(echo "$T1 - $T0" | bc -l)
  MBPS=$(echo "scale=2; ($BYTES * 8) / $DURACION / 1000000" | bc -l)
  echo "        ✓ ${BYTES} bytes  (~${MBPS} Mbps)"

  {
    echo "── CAPTURA ───────────────────────────────────────────"
    echo "Bytes:              $BYTES"
    echo "Bitrate aprox:      ${MBPS} Mbps"
    echo "Tiempo real:        ${SEGS}s"
    echo
  } >> "$REPORT"

  # ── 3. ffprobe ──────────────────────────────────────────────────
  echo "  [3/5] Analizando con ffprobe..."
  ffprobe -hide_banner -v error \
    -analyzeduration 10M -probesize 20M \
    -of json -show_streams -show_programs -show_format \
    "$SAMPLE" > "$PROBE" 2>/dev/null

  if ! jq -e '.streams' "$PROBE" >/dev/null 2>&1; then
    echo "        ✗ ffprobe no pudo interpretar el stream."
    echo "ERROR: ffprobe falló. TS posiblemente muy corrupto." >> "$REPORT"
    CANALES_FALLIDOS+=("$NOMBRE")
    FILAS_MD+=("| $NOMBRE | $MODO | ilegible | - | - | - | - | - | - | - |")
    echo
    continue
  fi

  V_CODEC=$(jq -r '[.streams[]|select(.codec_type=="video")][0].codec_name   // "-"' "$PROBE")
  V_PROF=$(jq -r  '[.streams[]|select(.codec_type=="video")][0].profile      // "-"' "$PROBE")
  V_W=$(jq -r     '[.streams[]|select(.codec_type=="video")][0].width        // "-"' "$PROBE")
  V_H=$(jq -r     '[.streams[]|select(.codec_type=="video")][0].height       // "-"' "$PROBE")
  V_PID=$(jq -r   '[.streams[]|select(.codec_type=="video")][0].id           // "-"' "$PROBE")
  V_FPS=$(jq -r   '[.streams[]|select(.codec_type=="video")][0].r_frame_rate // "-"' "$PROBE")
  V_INTER=$(jq -r '[.streams[]|select(.codec_type=="video")][0].field_order  // "-"' "$PROBE")
  V_SAR=$(jq -r   '[.streams[]|select(.codec_type=="video")][0].sample_aspect_ratio  // "-"' "$PROBE")
  V_DAR=$(jq -r   '[.streams[]|select(.codec_type=="video")][0].display_aspect_ratio // "-"' "$PROBE")

  N_AUDIO=$(jq -r '[.streams[]|select(.codec_type=="audio")]|length' "$PROBE")
  A_CODECS=$(jq -r '[.streams[]|select(.codec_type=="audio")|.codec_name]|unique|join(",")' "$PROBE")
  A_PIDS=$(jq -r   '[.streams[]|select(.codec_type=="audio")|.id]|join(",")' "$PROBE")

  {
    echo "── VIDEO ─────────────────────────────────────────────"
    echo "Códec:              $V_CODEC"
    echo "Perfil:             $V_PROF"
    echo "Resolución:         ${V_W}x${V_H}"
    echo "FPS:                $V_FPS"
    echo "Entrelazado:        $V_INTER"
    echo "SAR (pixel):        $V_SAR"
    echo "DAR (display):      $V_DAR"
    echo "PID:                $V_PID"
    echo
    echo "── AUDIO ($N_AUDIO pista(s)) ─────────────────────────"
    jq -r '.streams[]|select(.codec_type=="audio")|
      "PID \(.id)  \(.codec_name)  \(.sample_rate)Hz  \(.channels)ch  lang=\(.tags.language // "n/d")"' "$PROBE"
    echo
    echo "── OTRAS PISTAS ──────────────────────────────────────"
    jq -r '.streams[]|select(.codec_type!="video" and .codec_type!="audio")|
      "PID \(.id)  \(.codec_type)  \(.codec_name)"' "$PROBE"
    echo
    echo "── PROGRAMAS ─────────────────────────────────────────"
    jq -r '.programs[]? | "pnr=\(.program_id)  \(.tags.service_name // "sin nombre")"' "$PROBE"
    echo
  } >> "$REPORT"

  echo "        video: $V_CODEC ${V_W}x${V_H} SAR=$V_SAR  |  audio: $A_CODECS"

  if [ "$V_INTER" != "progressive" ] && [ "$V_INTER" != "-" ]; then
    echo "        ⚠  entrelazado ($V_INTER): MediaCodec no desentrelaza"
  fi

  # Banderas de decisión
  case "$A_CODECS" in
    *mp2*|*ac3*|*eac3*|*dts*|*truehd*|*mp1*)
      NECESITA_FFMPEG_EXT=1
      CODECS_AUDIO_RAROS+=("$NOMBRE:$A_CODECS")
      echo "        ⚠  audio requiere extensión FFmpeg" ;;
  esac
  if [ "$V_CODEC" = "mpeg2video" ]; then
    CANALES_MPEG2+=("$NOMBRE")
    echo "        ⚠  MPEG-2: verificar decoder en la caja"
  fi

  # ── 4. Access Unit Delimiters (solo H.264) ──────────────────────
  echo "  [4/5] Buscando AUDs..."
  AUD_COUNT="n/a"; IDR_COUNT="n/a"
  FLAG_DETECT="n/a"; FLAG_NONIDR="n/a"

  if [ "$V_CODEC" = "h264" ]; then
    TRACE=$(ffmpeg -hide_banner -v trace -i "$SAMPLE" \
              -c:v copy -bsf:v trace_headers -t 5 -f null - 2>&1)
    AUD_COUNT=$(echo "$TRACE" | grep -ci "Access Unit Delimiter" || true)
    IDR_COUNT=$(echo "$TRACE" | grep -ci "IDR" || true)
    AUD_COUNT=${AUD_COUNT:-0}
    IDR_COUNT=${IDR_COUNT:-0}

    if [ "$AUD_COUNT" -gt 0 ]; then
      FLAG_DETECT="OFF"
      echo "        ✓ $AUD_COUNT AUDs → FLAG_DETECT_ACCESS_UNITS = OFF"
    else
      FLAG_DETECT="ON"
      echo "        ⚠  sin AUDs → FLAG_DETECT_ACCESS_UNITS = ON (obligatorio)"
    fi

    if [ "$IDR_COUNT" -gt 0 ]; then
      FLAG_NONIDR="OFF"
    else
      FLAG_NONIDR="ON"
      echo "        ⚠  sin IDR → FLAG_ALLOW_NON_IDR_KEYFRAMES = ON"
    fi

    {
      echo "── FLAGS DE TS (Media3) ──────────────────────────────"
      echo "AUDs detectados:    $AUD_COUNT"
      echo "IDR detectados:     $IDR_COUNT"
      echo "FLAG_DETECT_ACCESS_UNITS:     $FLAG_DETECT"
      echo "FLAG_ALLOW_NON_IDR_KEYFRAMES: $FLAG_NONIDR"
      echo
      echo "Nota 1: en Amlogic (ZTE B866V2) la recomendación empírica de"
      echo "        campo invierte estos valores. Ambos flags deben quedar"
      echo "        configurables en runtime y validarse en hardware real."
      echo "Nota 2: en la ruta HLS, HlsMediaSource ignora el"
      echo "        ExtractorsFactory global. Hay que pasar los flags vía"
      echo "        DefaultHlsExtractorFactory."
      echo
    } >> "$REPORT"
  else
    echo "        — no aplica (video $V_CODEC, no H.264)"
    {
      echo "── FLAGS DE TS ───────────────────────────────────────"
      echo "No aplica: los flags H.264 solo afectan a video h264."
      echo
    } >> "$REPORT"
  fi

  # ── 5. tsduck ───────────────────────────────────────────────────
  echo "  [5/5] Análisis de transporte..."
  if [ "$HAS_TSDUCK" -eq 1 ]; then
    tsanalyze "$SAMPLE" > "$OUTDIR/${NOMBRE}.analyze.txt" 2>&1
    CC_ERR=$(grep -i "TS packets.*error\|Continuity" "$OUTDIR/${NOMBRE}.analyze.txt" | head -3 || true)
    {
      echo "── TRANSPORTE (tsduck) ───────────────────────────────"
      echo "${CC_ERR:-sin errores reportados}"
      echo "Detalle completo en ${NOMBRE}.analyze.txt"
      echo
    } >> "$REPORT"
    echo "        ✓ ver ${NOMBRE}.analyze.txt"
  else
    echo "        — omitido (tsduck no instalado)"
  fi

  FILAS_MD+=("| $NOMBRE | $MODO | $V_CODEC | ${V_W}x${V_H} | $V_SAR | $V_INTER | $V_PID | $A_CODECS | $A_PIDS | $FLAG_DETECT / $FLAG_NONIDR |")

  [ "$KEEP_SAMPLES" -eq 0 ] && rm -f "$SAMPLE"
  echo
done

# ─────────────────────────────────────────────────────────────────────
# CHANNELS.md consolidado
# ─────────────────────────────────────────────────────────────────────
MD="$OUTDIR/CHANNELS.md"
{
  echo "# Caracterización de canales"
  echo
  echo "Generado: $(date -Iseconds) · muestra de ${DURACION}s por canal"
  echo
  echo "| Canal | Transp. | Video | Resolución | SAR | Scan | PID V | Audio | PID A | DETECT_AU / ALLOW_NON_IDR |"
  echo "|---|---|---|---|---|---|---|---|---|---|"
  if [ ${#FILAS_MD[@]} -gt 0 ]; then
    printf '%s\n' "${FILAS_MD[@]}"
  else
    echo "| (sin canales analizados) | | | | | | | | | |"
  fi
  echo
  echo "## Decisiones que se derivan de esta tabla"
  echo

  if [ "$NECESITA_FFMPEG_EXT" -eq 1 ]; then
    echo "### Extensión FFmpeg: NECESARIA"
    echo
    echo "Hay audio que Android no decodifica de forma nativa y fiable:"
    echo
    for c in "${CODECS_AUDIO_RAROS[@]}"; do echo "- \`$c\`"; done
    echo
    echo "Consecuencia para Google Play: hay que usar un build **LGPL-2.1**"
    echo "de FFmpeg (\`--disable-gpl --disable-nonfree\`), enlazado dinámico"
    echo "como \`.so\` separados. Quedan descartados nextlib y"
    echo "jellyfin-androidx-media por ser GPL-3.0."
    echo
    echo "Renderer: \`EXTENSION_RENDERER_MODE_ON\` para que el audio no"
    echo "soportado vaya por FFmpeg y el AAC siga por hardware."
  else
    echo "### Extensión FFmpeg: NO NECESARIA"
    echo
    echo "Todo el audio es decodificable de forma nativa por Android."
    echo "Esto elimina la dependencia de FFmpeg y con ella el problema de"
    echo "licencias GPL/LGPL de cara a Google Play."
  fi
  echo

  if [ ${#CANALES_MPEG2[@]} -gt 0 ]; then
    echo "### Video MPEG-2 presente"
    echo
    for c in "${CANALES_MPEG2[@]}"; do echo "- $c"; done
    echo
    echo "**Decisión tomada:** se soporta MPEG-2 + MP2 de forma nativa en"
    echo "el cliente, sin transcodificar en el headend."
    echo
    echo "Implicación para el código: la app debe detectar en runtime si"
    echo "existe decoder para \`video/mpeg2\` mediante"
    echo "\`MediaCodecList(ALL_CODECS)\`. Si esa caja no lo tiene, el canal"
    echo "se enruta automáticamente al backend libVLC, que además"
    echo "desentrelaza por software. La decisión es por dispositivo y por"
    echo "canal, nunca global ni cableada en el APK."
    echo
  fi

  if [ ${#CANALES_CONTENT_LENGTH[@]} -gt 0 ]; then
    echo "### Content-Length fijo detectado (riesgo alto)"
    echo
    for c in "${CANALES_CONTENT_LENGTH[@]}"; do echo "- $c"; done
    echo
    echo "ExoPlayer intentará calcular la duración con TsDurationReader y"
    echo "se quedará en preparación infinita. Corregir en el servidor"
    echo "(chunked / sin Content-Length) o pasar ese canal por Flussonic."
    echo
  fi

  if [ ${#CANALES_FALLIDOS[@]} -gt 0 ]; then
    echo "### Canales que no se pudieron analizar"
    echo
    for c in "${CANALES_FALLIDOS[@]}"; do echo "- $c"; done
    echo
  fi

  cat <<'EOF'
### Relación de aspecto

Revisar la columna SAR. Cualquier valor distinto de `1:1` es contenido
anamórfico: si el reproductor ignora el SAR, la imagen sale deformada.
El módulo debe respetar el `pixelWidthHeightRatio` del stream y ofrecer
modos de ajuste manuales (Fit / Fill / Zoom / 4:3 / 16:9).

### Entrelazado

Revisar la columna Scan. Cualquier valor distinto de `progressive`
significa señal entrelazada. MediaCodec no desentrelaza: en esos canales
se ve peine en movimiento salvo que la caja desentrelace por hardware
(obligatorio solo en Android TV certificado) o se use libVLC con
deinterlace por software.

## Pendiente: capacidades de la caja Android TV

Correr en cada modelo a soportar, con la caja conectada por adb:

```bash
adb shell dumpsys media.codec | grep -iE "mpeg2|mp2|audio/mpeg|avc"
```

Interpretación: `hw-accelerated: 1` con `software-only: 0` indica
decoder de hardware real; `vendor: 1` indica que es del fabricante.

| Caja | SoC | MPEG-2 | H.264 HW | AC-3 | Android | Notas |
|---|---|---|---|---|---|---|
| ZTE ZXV10 B866V2 | Amlogic | ? | ? | ? | ? | caja mayoritaria del primer cliente |
|  |  |  |  |  |  |  |

Esta tabla es informativa. La app NO debe depender de ella: la detección
de capacidades ocurre en runtime en cada dispositivo, y los casos
problemáticos se corrigen con overrides remotos desde el `config.json`,
sin republicar el APK.
EOF
} > "$MD"

# ─────────────────────────────────────────────────────────────────────
# Resumen en pantalla
# ─────────────────────────────────────────────────────────────────────
echo "════════════════════════════════════════════════════════════"
echo "  RESUMEN"
echo "════════════════════════════════════════════════════════════"
echo
if [ "$NECESITA_FFMPEG_EXT" -eq 1 ]; then
  echo "  Extensión FFmpeg ....... NECESARIA (build LGPL obligatorio)"
else
  echo "  Extensión FFmpeg ....... no necesaria"
fi
echo "  Canales analizados ..... ${#FILAS_MD[@]}"
echo "  Canales MPEG-2 ......... ${#CANALES_MPEG2[@]}"
echo "  Content-Length fijo .... ${#CANALES_CONTENT_LENGTH[@]}"
echo "  Canales fallidos ....... ${#CANALES_FALLIDOS[@]}"
echo
echo "  Tabla consolidada:  $MD"
echo "  Reportes por canal: $OUTDIR/*.report.txt"
[ "$KEEP_SAMPLES" -eq 1 ] && echo "  Muestras .ts:       $OUTDIR/samples/"
echo
