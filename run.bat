@echo off
where mvn >nul 2>nul
if errorlevel 1 (
  echo Maven nao encontrado. Instale o Apache Maven e tente novamente.
  pause
  exit /b 1
)
mvn clean javafx:run
