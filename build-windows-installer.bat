@echo off
setlocal EnableExtensions
cd /d "%~dp0"
call build-windows-portable.bat
if errorlevel 1 exit /b %errorlevel%

echo Gerando instalador EXE com jpackage...
if not exist dist\installer mkdir dist\installer
jpackage ^
  --type exe ^
  --name Evalia ^
  --app-version 2.1.0 ^
  --vendor "Evalia" ^
  --description "See beyond the moves" ^
  --input target\package-input ^
  --main-jar evalia-2.1.0.jar ^
  --main-class br.edu.evalia.EvaliaApp ^
  --dest dist\installer ^
  --win-menu ^
  --win-shortcut ^
  --win-dir-chooser
if errorlevel 1 (
  echo [ERRO] O instalador nao foi gerado. Confira se o WiX Toolset exigido pelo seu JDK esta instalado.
  exit /b 1
)
echo Instalador criado em dist\installer.
endlocal
