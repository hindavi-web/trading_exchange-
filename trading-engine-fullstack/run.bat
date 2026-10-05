@echo off
echo =========================================================
echo  Starting Apex Stock Trading Engine & Mobile Backend
echo =========================================================

REM Create bin directory if not exists
if not exist "bin" mkdir bin

REM Compile all java classes
echo Compiling Java source files...
javac -d bin -cp "bin;lib/*" src\main\java\com\engine\model\*.java src\main\java\com\engine\core\*.java src\main\java\com\engine\db\*.java src\main\java\com\engine\api\*.java src\main\java\com\engine\Main.java

if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Compilation failed!
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo Launching server on http://localhost:8080 ...
java -cp "bin;lib/*" com.engine.Main
