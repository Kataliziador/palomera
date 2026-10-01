# Letreros de «sin señal» para el `backup` de Flussonic

## La regla

**El letrero debe llevar el mismo códec de vídeo que el canal.**

Flussonic **no recodifica** el archivo de respaldo: su códec sale tal cual en
el mismo flujo HLS. Un cambio de códec a mitad de emisión obliga a la caja a
destruir el decodificador de hardware y construir otro, y el decodificador AVC
de la caja de pruebas ya ha demostrado ser frágil con mucho menos que eso.

La resolución y los cuadros por segundo importan menos —un decodificador
aguanta un cambio de resolución sin reconstruirse— pero igualarlos no cuesta
nada y quita riesgo.

El audio es **AAC 48 kHz estéreo** en los cinco, porque es lo que ya emiten los
seis canales tras la normalización del headend.

## Qué letrero va con qué canal

| Canal | Vídeo del canal | Letrero |
|---|---|---|
| `test-sat70w-hbohd-mp4` | h264 1920x1080 | `sin-senal-h264-1080.mp4` |
| `test-sat70w-discoveryhd-mp4` | h264 1920x1080 | `sin-senal-h264-1080.mp4` |
| `test-sat77w-estrellashd-mp4` | h264 1440x1080 SAR 4:3 | `sin-senal-h264-1440.mp4` |
| `beisbol` | h264 1280x720 | `sin-senal-h264-720.mp4` |
| `mileniosd` | h264 1024x576 | `sin-senal-h264-576.mp4` |
| `test-sat77w-estrellassd-mp2v` | **mpeg2video** 544x480 SAR 20:17 | **`sin-senal-mpeg2-sd.mp4`** |

Si solo quieres mantener dos archivos, el mínimo que **no** rompe nada es uno
por códec: el de 1080 para los cinco de H.264 y el de MPEG-2 para
`estrellassd`. Se verá un cambio de resolución al entrar el letrero, que es
inofensivo.

## Cómo se usan — **el paso que falta en la documentación de Flussonic**

`vod/` **no es una carpeta: es el nombre de una ubicación VOD** que hay que
declarar aparte. Sin ese bloque, `backup vod/...` no apunta a nada y la
configuración no entra. Es el motivo más probable de que el `backup` «no se
deje configurar».

**1. Copia los letreros al servidor**, por ejemplo a `/storage`:

```bash
scp headend/letreros/sin-senal-*.mp4 root@<tu-flussonic>:/storage/
```

**2. Declara la ubicación VOD** en `/etc/flussonic/flussonic.conf`, **una sola
vez** para todo el servidor:

```
vod vod {
  storage /storage;
}
```

Con eso, `vod/sin-senal-h264-1080.mp4` resuelve a
`/storage/sin-senal-h264-1080.mp4`.

**3. Añade el `backup` a cada canal**, apuntando al letrero de su códec:

```
stream test-sat77w-estrellassd-mp2v {
  input <tu fuente>;
  segment_duration 2;
  segment_count 20;
  source_timeout 10;
  backup vod/sin-senal-mpeg2-sd.mp4 video_timeout=5 audio_timeout=10 timeout=20;
}

stream test-sat70w-hbohd-mp4 {
  input <tu fuente>;
  segment_duration 2;
  segment_count 20;
  source_timeout 10;
  backup vod/sin-senal-h264-1080.mp4 video_timeout=5 audio_timeout=10 timeout=20;
}
```

**4. Recarga** y comprueba que el canal sigue en línea:

```bash
service flussonic reload
```

### Si el MP4 con MPEG-2 no entra

MPEG-2 dentro de MP4 es legal y `ffprobe` lo lee bien, pero es una combinación
poco común. Si Flussonic la rechaza, está también
`sin-senal-mpeg2-sd.ts`: la documentación de failover dice que acepta MP4
**y** MPEG-TS. Prueba el `.mp4` primero, que es lo que tu instalación parece
preferir.

## Detalles de la codificación, y por qué

- **GOP de 15 cuadros** (medio segundo): el cliente empieza a decodificar el
  letrero casi al instante en vez de esperar al siguiente fotograma clave.
- **Diez segundos de duración**: Flussonic lo repite en bucle. Más largo solo
  ocupa sitio.
- **Silencio, no tono**: un pitido a las tres de la mañana sería peor que el
  fallo que intenta avisar.
- **MP4 también para el de MPEG-2.** El contenedor MPEG-PS queda descartado
  porque no admite AAC; MP4 sí admite vídeo MPEG-2, aunque sea poco habitual.
  Se deja además la versión `.ts` por si acaso.
- **El SAR se conserva** en los dos canales anamórficos, para que el letrero no
  salga deformado donde la imagen no lo estaba.

## Cómo rehacerlos con tu marca

`generar.sh` los produce todos. El texto es un marcador: sustituye el filtro
`drawtext` por tu imagen de marca —con `-loop 1 -i letrero.png`— y **conserva
los parámetros de codificación**, que son la parte que importa.

```bash
./generar.sh "SIN SEÑAL" /ruta/de/salida
```
