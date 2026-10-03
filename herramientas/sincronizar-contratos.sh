#!/usr/bin/env bash
# Vuelve a copiar los contratos a los proyectos que los consumen, con el
# commit del que salen. Sin esto, la copia se queda atrás en silencio —que es
# lo que pasó con CONFIG_SCHEMA entre ed0999e y 28a831d.
set -euo pipefail
APK=/home/proyectos/jellyfin-livetv-v1
copiar() {
  local orig="$1" copia="$2" integracion="$3"
  local hash; hash=$(git -C "$APK" log --format=%h -1 -- "$orig")
  {
    echo "# CONTRATO.md"
    echo
    echo "> **Copia.** El original vive en \`$APK/$orig\`, commit \`$hash\`."
    echo "> Lo escribe quien desarrolla la APK, porque es quien decide qué sabe leer."
    echo ">"
    echo "> **Si esta copia y el original discrepan, manda el original.** Un cambio de"
    echo "> contrato se hace allí y se vuelve a copiar aquí; nunca al revés."
    echo ">"
    echo "> Comprobar si se quedó atrás:"
    echo ">"
    echo "> \`\`\`bash"
    echo "> git -C $APK log --oneline -1 -- $orig"
    echo "> \`\`\`"
    echo ">"
    echo "> Si devuelve un commit distinto de \`$hash\`, el contrato cambió: leer"
    echo "> \`$integracion\` y volver a copiar."
    echo
    echo "---"
    echo
    tail -n +2 "$APK/$orig"
  } > "$copia"
  echo "  $orig → $copia  ($hash)"
}
copiar CONFIG_SCHEMA.md   /home/proyectos/servidor-canales/docs/CONTRATO.md  jellyfin-livetv-v1/INTEGRACION_CANALES.md
copiar REGISTRY_SCHEMA.md /home/proyectos/autorizador/docs/CONTRATO.md       jellyfin-livetv-v1/INTEGRACION_REGISTRO.md
