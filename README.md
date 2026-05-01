# Clon de Discord: Proyecto WebSockets

Este proyecto es una implementacion completa de una arquitectura cliente-servidor para la comunicacion en tiempo real, disenada como practica de redes y sockets. 

Se ha desarrollado simulando la interfaz y funcionalidades de Discord, sustituyendo la implementacion clasica de TCP Sockets por WebSockets, junto con una pila tecnologica moderna.

## Tecnologias Utilizadas

- Frontend: React.js, Vite, Lucide React
- Backend: Node.js, Express, Socket.io
- Base de Datos: SQLite
- Protocolo: WebSockets

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

- Autenticacion simulada con nombre de usuario.
- Interfaz grafica avanzada con navegacion por servidores y canales.
- Comunicacion bidireccional y transmision en tiempo real de mensajes.
- Persistencia de datos utilizando SQLite.
- Panel de usuarios activos en linea.
- Simulacion de interfaz de llamadas de voz y video.
- Modales de configuracion y perfiles de usuario.
