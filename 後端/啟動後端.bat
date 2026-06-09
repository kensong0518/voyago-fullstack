@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo ================================================
echo   Voyago Backend  ->  http://localhost:8080
echo ================================================

REM --- 自動從 PATH 找出 Java，推導 JAVA_HOME ---
if "%JAVA_HOME%"=="" (
  for /f "delims=" %%i in ('where java 2^>nul') do (
    set "JAVA_EXE=%%i"
    goto :gotjava
  )
)
goto :havehome

:gotjava
for %%a in ("%JAVA_EXE%") do set "JAVA_BIN=%%~dpa"
for %%a in ("%JAVA_BIN%..") do set "JAVA_HOME=%%~fa"

:havehome
echo JAVA_HOME = %JAVA_HOME%
echo.
echo 第一次啟動會下載 Maven 與 Spring Boot 套件，請耐心等候幾分鐘...
echo.
call mvnw.cmd spring-boot:run
echo.
echo (若上面出現紅色錯誤，請截圖。視窗保持開啟代表後端運作中，請勿關閉)
pause
