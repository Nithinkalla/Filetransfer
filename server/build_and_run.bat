@echo off
REM Compile Java source file
javac LaptopServer.java
IF ERRORLEVEL 1 (
    echo Compilation failed. Exiting.
    pause
    exit /b 1
)

REM Create manifest file
echo Main-Class: LaptopServer > manifest.txt

REM Create JAR file
jar cfm LaptopServer.jar manifest.txt LaptopServer.class
IF ERRORLEVEL 1 (
    echo JAR creation failed. Exiting.
    pause
    exit /b 1
)

REM Run the JAR
java -jar LaptopServer.jar

pause
