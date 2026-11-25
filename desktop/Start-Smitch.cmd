@echo off
setlocal
cd /d "%~dp0"
py -3 -c "import sys; sys.exit(sys.version_info < (3, 10))" >nul 2>&1
if not errorlevel 1 (
    py -3 "%~dp0smitch.py" %*
) else (
    python "%~dp0smitch.py" %*
)
if errorlevel 1 (
    echo.
    echo If Python is missing, install Python 3.10 or newer and run this launcher again.
    exit /b 1
)
