# Permisos #

### --- PRINCIPALES --- ###
ACCESS_FINE_LOCATION                 - Rastrear ubicación exacta del niño
ACCESS_BACKGROUND_LOCATION           - Rastrear incluso cuando la app está cerrada
REQUEST_IGNORE_BATTERY_OPTIMIZATIONS - Evitar que Android cierre la app
INTERNET                             - Enviar datos al padre/servidor
RECEIVE_BOOT_COMPLETED               - Iniciar al encender el teléfono

### --- IMPORTANTES --- ###
FOREGROUND_SERVICE            - Para servicio continuo de monitoreo
FOREGROUND_SERVICE_LOCATION   - Requerido en Android 14+ para servicios con GPS
POST_NOTIFICATIONS            - Mostrar notificaciones (Android 13+)
PACKAGE_USAGE_STATS           - Ver qué apps usa el niño
BIND_DEVICE_ADMIN             - Controles administrativos (bloquear pantalla, etc.)
READ_SMS / RECEIVE_SMS        - Monitorear mensajes

