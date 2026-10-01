#!/bin/bash
# Genera los letreros de «sin señal» para el `backup` de Flussonic.
#
# La regla: **el letrero debe llevar el mismo códec de vídeo que el canal.**
# Flussonic no recodifica el archivo de respaldo, así que su códec sale tal
# cual en el mismo flujo HLS. Un cambio de códec a mitad de emisión obliga a la
# caja a destruir y reconstruir el decodificador de hardware, y el de esta caja
# ya ha demostrado ser frágil.
#
# La resolución y los cuadros por segundo importan menos —un decodificador
# aguanta un cambio de resolución— pero igualarlos sale gratis y quita riesgo.
#
# El texto es un marcador: sustitúyelo por tu letrero de marca manteniendo los
# parámetros de codificación.

TEXTO=${1:-SIN SENAL}
SALIDA=${2:-.}

comun_audio="-f lavfi -i anullsrc=channel_layout=stereo:sample_rate=48000"
# Silencio a propósito: un tono a las tres de la mañana sería peor que el fallo.

# --- H.264 1080p (hbohd, discoveryhd) ---
ffmpeg -hide_banner -loglevel error \
  -f lavfi -i "color=c=0x101418:s=1920x1080:r=30000/1001:d=10" $comun_audio \
  -vf "drawtext=text='$TEXTO':fontcolor=0xDDDDDD:fontsize=72:x=(w-tw)/2:y=(h-th)/2" \
  -c:v libx264 -profile:v main -level 4.0 -pix_fmt yuv420p \
  -g 15 -keyint_min 15 -sc_threshold 0 -b:v 2000k \
  -c:a aac -b:a 128k -ar 48000 -ac 2 -shortest -t 10 \
  -y "$SALIDA/sin-senal-h264-1080.mp4"

# --- H.264 1440x1080 anamórfico (estrellashd) ---
ffmpeg -hide_banner -loglevel error \
  -f lavfi -i "color=c=0x101418:s=1440x1080:r=30000/1001:d=10" $comun_audio \
  -vf "drawtext=text='$TEXTO':fontcolor=0xDDDDDD:fontsize=64:x=(w-tw)/2:y=(h-th)/2,setsar=4/3" \
  -c:v libx264 -profile:v high -level 4.0 -pix_fmt yuv420p \
  -g 15 -keyint_min 15 -sc_threshold 0 -b:v 1800k \
  -c:a aac -b:a 128k -ar 48000 -ac 2 -shortest -t 10 \
  -y "$SALIDA/sin-senal-h264-1440.mp4"

# --- H.264 720p (beisbol) ---
ffmpeg -hide_banner -loglevel error \
  -f lavfi -i "color=c=0x101418:s=1280x720:r=30000/1001:d=10" $comun_audio \
  -vf "drawtext=text='$TEXTO':fontcolor=0xDDDDDD:fontsize=48:x=(w-tw)/2:y=(h-th)/2" \
  -c:v libx264 -profile:v main -level 3.1 -pix_fmt yuv420p \
  -g 15 -keyint_min 15 -sc_threshold 0 -b:v 1500k \
  -c:a aac -b:a 128k -ar 48000 -ac 2 -shortest -t 10 \
  -y "$SALIDA/sin-senal-h264-720.mp4"

# --- H.264 576p (mileniosd) ---
ffmpeg -hide_banner -loglevel error \
  -f lavfi -i "color=c=0x101418:s=1024x576:r=30/1:d=10" $comun_audio \
  -vf "drawtext=text='$TEXTO':fontcolor=0xDDDDDD:fontsize=40:x=(w-tw)/2:y=(h-th)/2" \
  -c:v libx264 -profile:v main -level 3.1 -pix_fmt yuv420p \
  -g 15 -keyint_min 15 -sc_threshold 0 -b:v 1200k \
  -c:a aac -b:a 128k -ar 48000 -ac 2 -shortest -t 10 \
  -y "$SALIDA/sin-senal-h264-576.mp4"

# --- MPEG-2 SD anamórfico (estrellassd) ---
# En MPEG-TS y no en MPEG-PS: el contenedor PS no admite AAC.
ffmpeg -hide_banner -loglevel error \
  -f lavfi -i "color=c=0x101418:s=544x480:r=30000/1001:d=10" $comun_audio \
  -vf "drawtext=text='$TEXTO':fontcolor=0xDDDDDD:fontsize=36:x=(w-tw)/2:y=(h-th)/2,setsar=20/17" \
  -c:v mpeg2video -pix_fmt yuv420p -g 15 -b:v 2500k \
  -c:a aac -b:a 128k -ar 48000 -ac 2 -shortest -t 10 \
  -f mpegts -y "$SALIDA/sin-senal-mpeg2-sd.ts"

echo "Listos en $SALIDA:"
ls -1 "$SALIDA"/sin-senal-* 2>/dev/null
