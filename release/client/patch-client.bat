@echo off
setlocal
rem Patches an original Infdev client jar so that it can join servers of TheWello's Infdev Server Project.
rem Usage: patch-client.bat <original Infdev jar>      (or drag the jar onto this file)
rem The build is detected from the jar. The result is written to the folder "patched" next to this script,
rem named after the build, for example patched\inf-20100316-mp.jar. Java 8 is required.
cd /d "%~dp0"
if "%~1"=="" (
  echo Usage: patch-client.bat ^<original Infdev jar^>
  echo Supported builds: inf-20100630-1340, inf-20100227-1433, inf-20100313, inf-20100316, inf-20100320, inf-20100321, inf-20100325-1640, inf-20100327, inf-20100330-1611, inf-20100413-1953
  exit /b 1
)
set "JAVA=java"
for /d %%D in ("%ProgramFiles%\Eclipse Adoptium\jdk-8*" "%ProgramFiles%\Eclipse Adoptium\jre-8*" "%ProgramFiles%\Java\jre1.8*" "%ProgramFiles(x86)%\Java\jre1.8*") do if exist "%%~D\bin\java.exe" set "JAVA=%%~D\bin\java.exe"
if not exist patched mkdir patched
"%JAVA%" -jar TheWellosInfdevClientPatcher.jar "%~1" patched
if errorlevel 1 (
  echo Patching failed.
  pause
  exit /b 1
)
set "BC=%APPDATA%\.betacraft"
if exist "%BC%\versions\jsons" (
  choice /m "BetaCraft was found. Install the patched client into BetaCraft"
  if not errorlevel 2 (
    for %%F in (patched\*-mp.jar) do (
      copy /y "%%F" "%BC%\versions\" >nul
      copy /y "betacraft\%%~nF.json" "%BC%\versions\jsons\" >nul
      copy /y "betacraft\%%~nF.info" "%BC%\versions\jsons\" >nul
      echo Installed %%~nF into BetaCraft. Restart BetaCraft and pick the version "TheWello's Infdev Multiplayer".
    )
  )
)
pause
