# El headend de pruebas

Lo que hay aquí no se compila ni viaja en el APK: es la configuración del
headend con el que se miden los canales (`CLAUDE.md` §4.7, «utilería fuera de
la app»).

## `HEADEND` no es un nombre de máquina

Las URLs de `config.json` empiezan por `http://HEADEND:8181`, y ese `HEADEND`
es **un marcador, no un host**. Hay que sustituirlo por la dirección real antes
de servir el archivo:

```bash
set -a; . herramientas/entorno.local; set +a
sed "s|http://HEADEND:8181|$PALOMERA_HEADEND|g" headend/config.json > /tmp/config.json
```

**Por qué no está escrita.** El repositorio es público, y por decisión de Edgar
del 04-nov en escritos públicos no se revela ninguna dirección, y menos de un
servidor. La dirección vive en `herramientas/entorno.local`, que el
`.gitignore` deja fuera; la plantilla es `herramientas/entorno.ejemplo`.

Que el marcador sea una palabra y no una dirección falsa es deliberado: una IP
inventada se copia y se sirve sin que nadie la mire, y entonces el fallo es «el
canal no carga» en vez de «esto hay que rellenarlo».

## `letreros/`

Las cartas de ajuste y los dos vídeos generados que sirve el site de
demostración. Son nuestros: ningún material de terceros, que es lo que permite
decírselo por escrito al revisor de Google (`FICHA.md` §4).
