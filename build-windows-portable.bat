@echo off
setlocal EnableExtensions
cd /d "%~dp0"

where mvn >nul 2>nul || (
  echo [ERRO] Maven nao encontrado no PATH.
  exit /b 1
)
where jpackage >nul 2>nul || (
  echo [ERRO] jpackage nao encontrado. Instale um JDK 21+ completo.
  exit /b 1
)

echo [1/4] Compilando e executando testes...
call mvn clean test package
if errorlevel 1 exit /b %errorlevel%

echo [2/4] Preparando dependencias de runtime...
if exist target\package-input rmdir /s /q target\package-input
mkdir target\package-input
call mvn dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=target\package-input
if errorlevel 1 exit /b %errorlevel%
copy /y target\evalia-2.1.0.jar target\package-input\ >nul
if exist engine (
  xcopy /e /i /y engine target\package-input\engine >nul
)

echo [3/4] Gerando app Windows autocontido...
if exist dist\Evalia rmdir /s /q dist\Evalia
if not exist dist mkdir dist
jpackage ^
  --type app-image ^
  --name Evalia ^
  --app-version 2.1.0 ^
  --vendor "Evalia" ^
  --description "See beyond the moves" ^
  --input target\package-input ^
  --main-jar evalia-2.1.0.jar ^
  --main-class br.edu.evalia.EvaliaApp ^
  --dest dist
if errorlevel 1 exit /b %errorlevel%

echo [4/4] Pronto.
echo Execute: dist\Evalia\Evalia.exe
echo Distribua a pasta dist\Evalia inteira.
endlocal
