# SocketChat

Proyecto de desarrollo de una aplicacion de mensajeria instantanea basada en una arquitectura cliente-servidor para el modulo de Programacion de Servicios y Procesos (2º DAM).

## Descripcion

SocketChat es una aplicacion de chat desarrollada en Java utilizando Sockets TCP. El objetivo del proyecto es aplicar los conocimientos sobre programacion en red y concurrencia adquiridos durante el curso, implementando un sistema capaz de gestionar multiples conexiones simultaneas de clientes a un servidor central.

La aplicacion incluye salas de chat persistentes, soporte para avatares en formato base64, mensajes privados e indicadores de escritura, emulando en cierta medida el comportamiento de plataformas reales como Discord o Telegram.

## Estructura del proyecto

El codigo fuente esta dividido en tres paquetes principales dentro de la carpeta `src`:

- `common`: Contiene la clase Protocol, encargada de la definicion del formato de los mensajes enviados a traves de la red y el parseo de los mismos.
- `server`: Implementa el servidor (ChatServer), la gestion de clientes individuales mediante hilos (ClientHandler) y la logica de las salas de chat (ChatRoom), incluyendo la concurrencia y persistencia de datos.
- `client`: Gestiona la conexion TCP por parte del cliente (NetworkClient) y todo el paquete visual `gui` desarrollado con Swing (MainFrame, ChatPanel, LoginPanel, UIComponents).

## Requisitos previos

- Java JDK 11 o superior.
- Windows (por el script de inicio proporcionado).

## Compilacion y ejecucion

Para facilitar el despliegue del proyecto en entornos locales, se ha incluido un script por lotes. 

1. Ejecutar el archivo `start.bat`. Este script se encarga de compilar automaticamente todo el codigo fuente y ubicar los binarios en la carpeta `out`.
2. Una vez compilado, el script inicia el servidor en segundo plano escuchando en el puerto 5000 y lanza una instancia del cliente.
3. Para abrir instancias adicionales de clientes, se puede volver a ejecutar el `start.bat`, el cual detectara si el servidor ya esta corriendo para no duplicarlo, o ejecutar directamente la clase `client.gui.MainFrame` desde la terminal.

## Decisiones tecnicas destacadas

- **Multihilo**: Se ha utilizado un `ExecutorService` (CachedThreadPool) en el servidor para gestionar las peticiones de los clientes de forma concurrente, evitando bloquear el hilo principal que escucha en el puerto.
- **Colecciones Concurrentes**: Uso intensivo de estructuras thread-safe como `ConcurrentHashMap` y `CopyOnWriteArrayList` para prevenir condiciones de carrera cuando multiples hilos intentan acceder o modificar listas de usuarios y salas simultaneamente.
- **Persistencia en ficheros**: El historial de las salas de chat y la lista de salas disponibles se escriben de manera persistente en archivos de texto dentro de una carpeta local `data`. Al iniciar el servidor, este carga la informacion previamente guardada en memoria.
- **UI Responsiva**: Se emplea `SwingUtilities.invokeLater` en la capa visual (Swing) para asegurar que todas las actualizaciones originadas por eventos de red se procesen dentro del Event Dispatch Thread (EDT), manteniendo la fluidez de la interfaz grafica.
