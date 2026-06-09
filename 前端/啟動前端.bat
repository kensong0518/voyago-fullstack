@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo ==========================================
echo   Voyago Frontend  =====  http://localhost:5173
echo ==========================================
if not exist node_modules (
  if exist package-lock.json (
    echo Installing packages with npm ci ^(lockfile present^)...
    call npm ci
  ) else (
    echo Installing packages with npm install...
    call npm install
  )
)
call npm run dev
pause
