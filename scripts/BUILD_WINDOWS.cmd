@echo off
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0.."
echo Freaky Friday R0.11 / Minecraft 26.2 / NeoForge build
if not defined JAVA_HOME (
  echo NOTE: Set JAVA_HOME to a Java 25 JDK directory before building.
)
java -version
if errorlevel 1 exit /b 2
if defined GRADLE_HOME (
  if exist "%GRADLE_HOME%\bin\gradle.bat" (
    call "%GRADLE_HOME%\bin\gradle.bat" --no-daemon clean build -PtargetMinecraft=26.2
    exit /b !ERRORLEVEL!
  )
)
call "%~dp0..\gradlew.bat" --no-daemon clean build -PtargetMinecraft=26.2
exit /b !ERRORLEVEL!
