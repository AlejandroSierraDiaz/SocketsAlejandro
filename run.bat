@echo off
echo ========================================================
echo   Iniciando DevChat (React + Node.js + WebSockets)
echo ========================================================

echo.
echo [1/2] Iniciando Servidor WebSockets con SQLite...
start "Backend (Servidor)" cmd /c "cd backend-node && npm install && node server.js"

echo [2/2] Iniciando Frontend React (Vite)...
start "Frontend (Cliente)" cmd /k "cd frontend-react && npm install && npm run dev"

echo.
echo ¡Todo en marcha! 
echo El servidor backend esta corriendo en http://localhost:3001
echo El frontend (interfaz) se abrira en tu navegador web.
echo.
pause
