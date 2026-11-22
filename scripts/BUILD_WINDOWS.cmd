@echo off
setlocal EnableExtensions
cd /d "%~dp0.."
set "MC=%~1"
if not defined MC set "MC=26.2"
if not "%MC%"=="26.2" if not "%MC%"=="26.3" (
  echo Usage: scripts\BUILD_WINDOWS.cmd [26.2^|26.3]
  exit /b 2
)
rem Python is only needed for the bundled Java-25 version check and ZIP audit.
where py >nul 2>nul
if not errorlevel 1 (
  set "PYTHON=py -3"
) else (
  where python >nul 2>nul
  if errorlevel 1 (
    echo ERROR: Python 3 is required for verified packaging.
    exit /b 2
  )
  set "PYTHON=python"
)
echo Building Freaky Friday P07C for Minecraft %MC% using Java 25...
%PYTHON% scripts\build_doctor.py --mc %MC%
if errorlevel 1 exit /b 1
%PYTHON% scripts\package-full.py --minecraft %MC%
if errorlevel 1 exit /b 1
echo Build and full ZIP ready in build\packages
exit /b 0
