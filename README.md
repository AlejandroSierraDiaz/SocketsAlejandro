# Clon de Discord: Proyecto WebSockets

Este proyecto es una implementacion completa de una arquitectura cliente-servidor para la comunicacion en tiempo real, disenada como practica de redes y sockets. 

Se ha desarrollado simulando la interfaz y funcionalidades de Discord, sustituyendo la implementacion clasica de TCP Sockets por WebSockets, junto con una pila tecnologica moderna.

## Tecnologias Utilizadas

- Frontend: React.js, Vite, Lucide React, Emoji-picker-react
- Backend: Node.js, Express, Socket.io
- Base de Datos: SQLite
- Protocolo: WebSockets, WebRTC (para voz)

## Requisitos Previos

Para ejecutar el proyecto, unicamente es necesario tener instalado Node.js en el sistema.

## Ejecucion del Proyecto

Para iniciar tanto el servidor backend como el cliente frontend simultaneamente, ejecute el script principal incluido en la raiz del proyecto:

En Windows:
```
.\run.bat
```

Este script se encargara de instalar todas las dependencias necesarias e iniciar ambos entornos de desarrollo. El servidor de WebSockets estara activo en el puerto 3001 y la interfaz grafica se abrira en su navegador predeterminado.

## Funcionalidades Implementadas

- **Autenticacion:** Identificacion mediante nombre de usuario, con capacidad de modificarlo desde el panel de ajustes.
- **Canales Publicos:** Multiples salas de chat de texto independientes.
- **Mensajes Directos (DMs):** Sistema de chat privado entre dos usuarios.
- **Transmision Multimedia:** Soporte para enviar imagenes mediante codificacion Base64.
- **Emojis:** Integracion completa de selector de emojis.
- **Indicador de Escritura:** Notificacion en tiempo real de cuando un usuario esta escribiendo un mensaje.
- **Llamadas de Voz (WebRTC):** Canales de voz funcionales con transmision de audio en tiempo real mediante conexiones Peer-to-Peer.
- **Persistencia de Datos:** Todos los mensajes y usuarios se almacenan en una base de datos SQLite embebida, permitiendo recuperar el historial.
- **Modales e Interfaz Avanzada:** Tarjetas de perfil de usuario, panel de ajustes, visualizacion de conectados en linea y arquitectura de diseno identica a Discord.
