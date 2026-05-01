# Documentacion Tecnica: Clon de Discord

Este documento contiene la justificacion tecnica, la arquitectura y las decisiones adoptadas para el desarrollo del proyecto de WebSockets. 

## 1. Justificacion de la Solucion Informatica

### Lenguajes y Tecnologias Elegidas
Para simular el funcionamiento de aplicaciones modernas como WhatsApp, Telegram o Discord, se ha optado por abandonar los sockets crudos de TCP y evolucionar hacia una arquitectura web utilizando el stack React, Node.js y SQLite, junto con estandares P2P.

Frontend (Cliente): React.js y Vite
Justificacion: Aplicaciones como Discord usan React para su interfaz de usuario. React nos permite crear una Single Page Application (SPA) dinamica y fluida. Se ha implementado un diseno moderno idéntico a Discord, gestionando multiples canales de texto, canales de voz reales, panel de configuracion, envio de imagenes y mensajes privados.

Backend (Servidor): Node.js y Express
Justificacion: Node.js, por su naturaleza asincrona orientada a eventos, es idoneo para manejar multiples conexiones concurrentes de WebSockets y senalizacion WebRTC.

Comunicacion en Tiempo Real: WebSockets y WebRTC
Justificacion: WebSockets opera sobre HTTP, lo que evita bloqueos de firewalls, permite comunicacion bidireccional continua con baja latencia para mensajeria, estados de escritura ("Typing...") y eventos de sistema. Para la voz, se implemento WebRTC, estableciendo canales de audio Peer-to-Peer (P2P) de muy baja latencia. Socket.io actua unicamente como servidor de senalizacion (intercambio de ofertas y candidatos ICE) para WebRTC.

Base de Datos: SQLite
Justificacion: Para mantener el proyecto facil de ejecutar, se utiliza SQLite. Se ha ampliado para almacenar de forma estructurada mensajes en formato JSON, permitiendo clasificar el texto y la data multimedia (imagenes en Base64), ademas de categorizar a que canal o DM (Mensaje Directo) pertenece cada mensaje.

## 2. Arquitectura Cliente-Servidor y P2P

La arquitectura del sistema es hibrida: sigue el modelo Cliente-Servidor para los datos y autenticacion, y una topologia Peer-to-Peer (P2P) para la voz.

Multiples Clientes abren una conexion persistente hacia el Servidor Node.js. El servidor actua como intermediario. Almacena los mensajes en SQLite, distribuye los eventos de escritura, emojis e imagenes a traves de WebSockets. Para las llamadas, los clientes transmiten sus flujos de audio directamente entre navegadores (WebRTC).

### Diagrama de Arquitectura de Red

```mermaid
graph TD
    subgraph Capa de Presentacion Frontend
        C1[Cliente 1 - React]
        C2[Cliente 2 - React]
    end

    subgraph Capa de Servidor y Datos
        S[Node.js + Socket.io]
        DB[(Base de Datos SQLite)]
    end

    C1 <-->|WebSocket: Texto, Imagenes, Senalizacion| S
    C2 <-->|WebSocket: Texto, Imagenes, Senalizacion| S
    
    S <-->|Consultas SQL| DB
    
    C1 <..>|WebRTC UDP/TCP: Audio en Vivo| C2
```

## 3. Funcionalidades Cumplidas segun la Rubrica

1. Conexion multicliente: Soporta multiples clientes simultaneos gestionando salas (rooms) independientes en Socket.io.
2. Envio y recepcion de mensajes: Envio de texto, emojis e imagenes (codificadas en Base64).
3. Canales y Mensajes Privados: Separacion logica del chat. Los mensajes privados utilizan identificadores de sala dinamicos.
4. Voz en Tiempo Real: Llamadas de voz totalmente funcionales usando microfonos mediante la API navigator.mediaDevices y WebRTC.
5. Identificacion de usuario: Registro y modificacion de perfil. Interfaz de tarjeta de perfil interactiva.
6. Notificaciones en vivo: Indicadores de escritura ("Usuario esta escribiendo...").
7. Ejecucion automatizada: Todo el sistema se lanza ejecutando el script run.bat.

## 4. Explicacion del Protocolo Implementado

Se han definido multiples flujos de eventos sobre WebSockets:

Eventos de Chat:
- register_user / update_username: Identificacion y modificacion de datos en la BD.
- join_channel: Peticion de historial de un canal en concreto (publico o DM).
- send_message: Recibe un payload JSON con { content, channel, type }. Retransmite a todos los usuarios del 'channel'.
- typing: Notifica a los oyentes de una sala especifica el inicio de actividad.

Eventos de Voz (Senalizacion WebRTC):
- join_voice / leave_voice: Suscripcion a los eventos de una sala de voz.
- webrtc_offer / webrtc_answer: Intercambio de Session Description Protocol (SDP) entre peers.
- webrtc_ice_candidate: Descubrimiento de red y negociacion de puertos para atravesar NATs (via servidor STUN).
