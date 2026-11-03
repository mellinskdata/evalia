@echo off
setlocal
cd /d "%~dp0"
title Evalia - Inicializador

echo ==========================================
echo          EVALIA - INICIALIZADOR
echo ==========================================
echo.

where java >nul 2>nul
if errorlevel 1 (
  echo [ERRO] Java 17 nao foi encontrado.
  echo Instale o Temurin JDK 17 e tente novamente.
  pause
  exit /b 1
)

java -version 2>&1 | findstr /R /C:"version \"17\." >nul
if errorlevel 1 (
  echo [AVISO] O Evalia foi preparado para Java 17.
  java -version
  echo.
)

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\bootstrap-evalia.ps1"
set "RC=%ERRORLEVEL%"
if not "%RC%"=="0" (
  echo.
  echo [ERRO] O Evalia nao conseguiu iniciar. Codigo: %RC%
  echo Tire uma foto desta janela e envie no ChatGPT.
  pause
)
exit /b %RC%
