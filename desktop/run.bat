@echo off
REM Launch the knotes desktop app on Windows. Double-click this file, or run it from a shell.
REM Needs PyGObject + GTK 3 (see the "Windows" section of README.md).
setlocal

REM Resolve this script's own directory so the app finds style.css / its modules.
set "HERE=%~dp0"

REM pythonw.exe runs the GUI without a console window; python.exe is the fallback (and shows
REM tracebacks, which is what you want while setting things up).
where pythonw.exe >nul 2>&1
if %ERRORLEVEL%==0 (
    start "" pythonw.exe "%HERE%knotes.py" %*
    exit /b 0
)

where python.exe >nul 2>&1
if not %ERRORLEVEL%==0 (
    echo Python was not found on PATH.
    echo Install Python 3 plus PyGObject/GTK 3 - see the "Windows" section of README.md.
    pause
    exit /b 1
)

python.exe -c "import gi; gi.require_version('Gtk','3.0'); from gi.repository import Gtk" 2>nul
if not %ERRORLEVEL%==0 (
    echo PyGObject + GTK 3 are missing.
    echo Easiest fix:  pip install pygobject   ^(needs the GTK 3 runtime; see README.md^)
    pause
    exit /b 1
)

python.exe "%HERE%knotes.py" %*
