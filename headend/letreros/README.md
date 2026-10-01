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
| `test-sat77w-estrellassd-mp2v` | **mpeg2video** 544x480 SAR 20:17 | **`sin-senal-mpeg2-sd.ts`** |

Si solo quieres mantener dos archivos, el mínimo que **no** rompe nada es uno
por códec: el de 1080 para los cinco de H.264 y el de MPEG-2 para
`estrellassd`. Se verá un cambio de resolución al entrar el letrero, que es
inofensivo.

## Cómo se usan

```
stream test-sat77w-estrellassd-mp2v {
  input <tu fuente>;
  segment_duration 2;
  segment_count 20;
  source_timeout 10;
  backup vod/sin-senal-mpeg2-sd.ts video_timeout=5 audio_timeout=10 timeout=20;
}
```

## Detalles de la codificación, y por qué

- **GOP de 15 cuadros** (medio segundo): el cliente empieza a decodificar el
  letrero casi al instante en vez de esperar al siguiente fotograma clave.
- **Diez segundos de duración**: Flussonic lo repite en bucle. Más largo solo
  ocupa sitio.
- **Silencio, no tono**: un pitido a las tres de la mañana sería peor que el
  fallo que intenta avisar.
- **MPEG-TS y no MPEG-PS** para el de MPEG-2: el contenedor PS no admite AAC.
- **El SAR se conserva** en los dos canales anamórficos, para que el letrero no
  salga deformado donde la imagen no lo estaba.

## Cómo rehacerlos con tu marca

`generar.sh` los produce todos. El texto es un marcador: sustituye el filtro
`drawtext` por tu imagen de marca —con `-loop 1 -i letrero.png`— y **conserva
los parámetros de codificación**, que son la parte que importa.

```bash
./generar.sh "SIN SEÑAL" /ruta/de/salida
```
