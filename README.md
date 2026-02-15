# Permisos #

### --- PRINCIPALES --- ###
  - ACCESS_FINE_LOCATION                 - Rastrear ubicación exacta del niño
  - ACCESS_BACKGROUND_LOCATION           - Rastrear incluso cuando la app está cerrada
  - REQUEST_IGNORE_BATTERY_OPTIMIZATIONS - Evitar que Android cierre la app
  - INTERNET                             - Enviar datos al padre/servidor
  - RECEIVE_BOOT_COMPLETED               - Iniciar al encender el teléfono

### --- IMPORTANTES --- ###
  - FOREGROUND_SERVICE            - Para servicio continuo de monitoreo
  - FOREGROUND_SERVICE_LOCATION   - Requerido en Android 14+ para servicios con GPS
  - POST_NOTIFICATIONS            - Mostrar notificaciones (Android 13+)
  - PACKAGE_USAGE_STATS           - Ver qué apps usa el niño
  - BIND_DEVICE_ADMIN             - Controles administrativos (bloquear pantalla, etc.)
  - READ_SMS / RECEIVE_SMS        - Monitorear mensajes

Buen proyecto 👀 — es totalmente viable con AWS, pero conviene **diseñar bien la arquitectura desde el inicio**, sobre todo por **seguridad, permisos y legalidad** (ver mensajes, bloquear celular, etc.).

Te lo explico **de forma práctica**, pensando en Android + Java.

---

## 1️⃣ Arquitectura general (visión clara)

Tienes **3 piezas**:

```
App Padre  ──▶  Servidor (AWS)  ◀── App Niño
```

El servidor hace:

* Vincular padre ↔ niño
* Autenticación
* Envío de comandos
* Persistencia de datos
* Notificaciones en tiempo real

---

## 2️⃣ AWS: stack recomendado (simple y escalable)

### 🔹 Backend (API)

**Opción recomendada:**

* **API Gateway** → expone endpoints REST
* **AWS Lambda (Java o Node.js)** → lógica del servidor
* **DynamoDB** → base de datos NoSQL

💡 Ventaja: no administras servidores, barato y escala solo.

---

### 🔹 Base de datos (DynamoDB)

Tablas sugeridas:

#### 👤 Usuarios

```json
{
  "userId": "uuid",
  "email": "padre@email.com",
  "role": "PADRE | NINO"
}
```

#### 🔗 Vinculación

```json
{
  "childId": "uuid",
  "parentId": "uuid",
  "linkCode": "123456",
  "status": "PENDING | LINKED"
}
```

#### 📡 Eventos / comandos

```json
{
  "eventId": "uuid",
  "childId": "uuid",
  "type": "BLOCK_PHONE | GET_USAGE",
  "payload": {},
  "status": "SENT | EXECUTED"
}
```

---

## 3️⃣ Código de enlace (clave padre → niño)

### Flujo correcto

1. App Padre → `/generate-link-code`
2. Servidor genera:

   * Código corto (6 dígitos)
   * Expira en X minutos
3. App Niño ingresa el código → `/link-child`
4. Servidor valida y vincula

⚠️ **Nunca hagas el enlace solo en el cliente**.

---

## 4️⃣ Comunicación en tiempo real (clave del sistema)

### 🔔 Firebase Cloud Messaging (FCM)

Es **la mejor opción** para Android.

#### Flujo:

1. App Niño se registra en FCM y envía su `token` al servidor
2. App Padre envía comando
3. Servidor:

   * Guarda el evento
   * Envía push notification por FCM
4. App Niño recibe → ejecuta acción

👉 AWS + Firebase funcionan perfectamente juntos.

---

## 5️⃣ Permisos Android (muy importante)

Para la app del niño necesitarás:

### 🔐 Permisos críticos

* **Device Admin / Device Owner**
* **Accessibility Service**
* **UsageStatsManager**
* **Notification Listener**
* **SMS (si aplica)**

⚠️ Muchos de estos:

* No funcionan bien desde Play Store
* Requieren **explicar claramente al usuario**
* Algunos solo funcionan si la app se instala como **Device Owner**

---

## 6️⃣ Seguridad (NO te saltes esto)

### 🔒 Autenticación

* **AWS Cognito**
* Tokens JWT
* Roles (PADRE / NIÑO)

### 🔒 Buenas prácticas

* HTTPS obligatorio
* Nunca confiar en el cliente
* Validar que un padre solo controle **sus** hijos
* Logs en CloudWatch

---

## 7️⃣ Legalidad ⚠️ (en serio)

Funciones como:

* Leer mensajes
* Ver notificaciones
* Bloquear dispositivo

👉 En muchos países **requieren consentimiento explícito** y políticas claras.
👉 Play Store es muy estricta con apps de control parental.

**Recomendación:** revisa las *Google Play Developer Policies* antes de publicar.

---

## 8️⃣ Alternativa si quieres algo aún más simple

Si no quieres AWS al inicio:

* **Firebase Auth**
* **Firestore**
* **Cloud Functions**
* **FCM**

Para Android, Firebase es brutalmente rápido de implementar.

---

## 9️⃣ Mi recomendación final

Si es tu **primer backend**:
👉 **Firebase primero**
👉 Luego migras a AWS si crece

Si ya sabes backend:
👉 **AWS (API Gateway + Lambda + DynamoDB + Cognito)**

---

Si quieres, en el próximo mensaje puedo:

* Dibujarte el **diagrama completo**
* Proponerte los **endpoints REST**
* Ayudarte con el **FCM + Android**
* O revisar **qué permisos son viables en Play Store**

¿En qué parte quieres profundizar primero? 🚀
