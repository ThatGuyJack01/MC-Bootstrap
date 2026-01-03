package com.thatguyjack.bootstrap.util;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;

public final class ApplyScript {
    private ApplyScript() {}

    /** Writes the batch script to .minecraft/godsmp_apply_update.bat */
    public static Path writeBatch(Path gameDir) throws Exception {
        Path bat = gameDir.resolve("godsmp_apply_update.bat");

        // We pass PID as %1
        // Script will:
        // - wait for PID to exit
        // - delete mods listed in staging\delete.txt
        // - copy staged mods into mods\
        // - replace installed.json
        String content = """
@echo off
setlocal enabledelayedexpansion

set PID=%1
if "%PID%"=="" (
  echo Missing PID argument.
  pause
  exit /b 1
)

REM Go to .minecraft directory (script location)
cd /d "%~dp0"

set STAGING=%~dp0godsmp_staging
set STAGING_MODS=%STAGING%\\mods
set DELETE_LIST=%STAGING%\\delete.txt
set NEW_INSTALLED=%STAGING%\\installed.json
set MODS_DIR=%~dp0mods
set CFG_DIR=%~dp0config\\godsmp_bootstrap

echo Waiting for Minecraft (PID=%PID%) to exit...
:waitloop
tasklist /FI "PID eq %PID%" 2>nul | find "%PID%" >nul
if %errorlevel%==0 (
  timeout /t 1 >nul
  goto waitloop
)

echo Applying updates...

if not exist "%MODS_DIR%" (
  echo mods folder not found: %MODS_DIR%
  pause
  exit /b 1
)

REM Delete removed mods (owned only)
if exist "%DELETE_LIST%" (
  for /f "usebackq delims=" %%F in ("%DELETE_LIST%") do (
    if not "%%F"=="" (
      if exist "%MODS_DIR%\\%%F" (
        echo Deleting %MODS_DIR%\\%%F
        del /f /q "%MODS_DIR%\\%%F" >nul 2>&1
      )
    )
  )
)

REM Copy staged mods into mods folder
if not exist "%STAGING_MODS%" (
  echo staging mods folder missing: %STAGING_MODS%
  pause
  exit /b 1
)

for %%J in ("%STAGING_MODS%\\*.jar") do (
  echo Installing %%~nxJ
  copy /y "%%~fJ" "%MODS_DIR%\\%%~nxJ" >nul
)

REM Replace installed.json
if not exist "%CFG_DIR%" mkdir "%CFG_DIR%" >nul 2>&1
if exist "%NEW_INSTALLED%" (
  copy /y "%NEW_INSTALLED%" "%CFG_DIR%\\installed.json" >nul
)

echo Done. Launch Minecraft again.
exit /b 0
pause
""";

        Files.writeString(bat, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        return bat;
    }

    /** Launch the batch script detached: cmd /c start "" bat pid */
    public static void runBatchDetached(Path batPath, long pid) throws Exception {
        new ProcessBuilder("cmd", "/c", "start", "\"\"", batPath.toAbsolutePath().toString(), Long.toString(pid))
                .inheritIO()
                .start();
    }
}
