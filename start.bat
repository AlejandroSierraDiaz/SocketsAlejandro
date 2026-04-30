@echo off
title SocketChat
echo ========================================
echo   SocketChat - Iniciando...
echo ========================================
echo.

REM Compilar si hace falta
if not exist "out\common\Protocol.class" (
    echo [*] Compilando...
    if not exist "out" mkdir out
    javac -encoding UTF-8 -d out src\common\Protocol.java src\server\ChatRoom.java src\server\ClientHandler.java src\server\ChatServer.java src\client\NetworkClient.java src\client\gui\UIComponents.java src\client\gui\LoginPanel.java src\client\gui\ChatPanel.java src\client\gui\MainFrame.java
    if %ERRORLEVEL% NEQ 0 (
        echo [ERROR] Fallo de compilacion.
        pause
        exit /b 1
    )
    echo [OK] Compilacion exitosa.
    echo.
)

REM Iniciar servidor en segundo plano
echo [*] Iniciando servidor en segundo plano...
start /B java -cp out server.ChatServer 5000 > nul 2>&1

REM Esperar un momento para que el servidor arranque
timeout /t 2 /nobreak > nul

REM Iniciar cliente
echo [*] Abriendo SocketChat...
echo.
java -cp out client.gui.MainFrame

REM Cuando el cliente se cierre, matar el servidor
taskkill /F /FI "WINDOWTITLE eq SocketChat Server" > nul 2>&1
