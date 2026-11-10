# Petición: liberar la tele en Jellyfin al salir de una cuenta

**De:** Palomera para Roku (`/home/proyectos/Palomera-Roku`), 2026-11-09.
**Estado:** pendiente en Android. En Roku, hecho y probado (rama
`liberar-tele`, commit `74e319ba`).
**Pide Edgar** que se arregle aquí igual.

## El fallo, medido en producción

El 09-nov, en el site de Palomera Probadores, una caja Roku activada para la
cuenta `testroku` perdió su sesión local. Al activarla otra vez, el panel de
abonados contestó tres veces «No se pudo activar: la clave no tiene permiso».
**Borrar a mano el dispositivo de esa cuenta en el panel de control de
Jellyfin la dejó entrar a la primera.**

## La causa, leída en Jellyfin 10.11 (`release-10.11.z`)

- **El panel pone un límite de teles por cuenta.** Lo escribe en la política
  del usuario como `MaxActiveSessions`, desde el plan; por defecto 2.
- **Jellyfin cuenta antes de limpiar.** Quick Connect autoriza por
  `SessionManager.AuthenticateNewSessionInternal`, que cuenta
  `Sessions.Count(i => i.UserId == user.Id)` y, si llega al máximo, lanza
  `SecurityException` → **403**. Eso ocurre **antes** de
  `GetAuthorizationToken`, que es lo que cerraría la sesión vieja **del mismo
  aparato**. Así que **la misma caja que se reactiva choca contra su propio
  fantasma**.
- **Una sesión no se va sola.** Sigue en memoria hasta un `Logout`, el borrado
  del dispositivo o un reinicio del servidor.
- **En producción Jellyfin no da el motivo.** El cuerpo del 403 es «Error
  processing request.», y el panel lo traducía como «la clave».
- **`POST /Sessions/Logout`** con el token de la caja llama a
  `SessionManager.Logout(token)`. Eso **borra el dispositivo y cierra las
  sesiones** con ese `DeviceId`: es exactamente lo que se hizo a mano.

## El hueco en esta app

Al salir de una cuenta, Android borra el token **sólo en la caja** y nunca
avisa a Jellyfin. La sesión se queda en el servidor ocupando el cupo:

| Dónde | Qué hace hoy |
|---|---|
| `auth/repository/AuthenticationRepository.kt` → `logout(user)`, llamado desde `ui/startup/fragment/ServerFragment.kt` y `ui/startup/preference/EditUserScreen.kt` | pone `accessToken = null` en el almacén local |
| `auth/repository/ServerRepository.kt` → `deleteServer(server)`, llamado desde `ui/startup/preference/EditServerScreen.kt` y `ui/startup/fragment/SelectServerFragment.kt` | `authenticationStore.removeServer`, sin tocar el servidor |

Con el límite en 1, una caja que cierra sesión no puede volver a activarse
hasta que alguien borre su dispositivo en Jellyfin. Con 2, bastan dos salidas.

## Lo que se pide

1. **Antes de olvidar el token, avisar a Jellyfin:** `POST /Sessions/Logout`
   con ese token (en el SDK, `sessionApi.reportSessionEnded()` con el cliente
   de esa sesión). Hay que hacerlo en `logout(user)` y en `deleteServer(server)`,
   en este último para cada usuario de ese servidor que tenga token guardado.
2. **Con espera corta, y sin retener a nadie.** En Roku: 5 s
   (`Sintonia.LIBERAR_TELE_ESPERA_MS`). Si no contesta, se registra y se sale
   igual; el cupo lo libera el proveedor desde el panel.
3. **Cero errores silenciosos:** registrar el resultado (liberada, o no y por
   qué) con el servidor y el código.
4. **No liberar al cambiar de usuario** si la caja conserva el perfil para
   volver sin código: esa caja sigue siendo una de las teles de la cuenta.
5. **Ojo con el 403 del suspendido.** Con el horario de acceso vacío, el
   `Logout` de un abonado suspendido probablemente también conteste 403 y no
   libere nada. Es inferido, no medido. Que no bloquee la salida.

## Cómo se comprobó en Roku (para repetirlo aquí)

1. Caja activada → «Cerrar sesión». Consola: `cuenta.teleLiberada
   opcion="sign_out"`.
2. En Jellyfin, el dispositivo de la cuenta **desapareció solo** de
   Dispositivos.
3. Activar otra vez desde el panel con el código nuevo: **entró sin borrar
   nada a mano**. Lo comprobó Edgar en la tele.

## La otra mitad, que no es de las apps

Las cajas que salen **sin avisar** (reseteo, reinstalación, aparato roto o
reemplazado) sólo las puede resolver el panel de abonados. Se está haciendo
allí:
- contar las teles de la cuenta antes de autorizar;
- si está llena, ofrecer «¿cuál reemplazas?»;
- un botón «Quitar esta tele» en cada dispositivo;
- mensajes con la causa real.
