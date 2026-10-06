@echo off
rem Launcher: runs the jar built by `mvn package`.
set "JAR=%~dp0..\target\sift.jar"
if not exist "%JAR%" (
    echo sift: %JAR% not found; run 'mvn package' first 1>&2
    exit /b 2
)
java -jar "%JAR%" %*
