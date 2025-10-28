@echo off
title DFSS - File Sharing System Manager
color 0A

:MENU
cls
echo ===============================================
echo   DFSS - File Sharing System Controller
echo ===============================================
echo.
echo   [1] Build system  (Maven + Docker images)
echo   [2] Run system    (Start all containers)
echo   [3] Stop system   (Stop and remove containers)
echo   [4] Clean all     (Remove containers, images, volumes)
echo   [0] Exit
echo.
set /p choice= Select an option (0-4):

if "%choice%"=="1" goto BUILD
if "%choice%"=="2" goto RUN
if "%choice%"=="3" goto STOP
if "%choice%"=="4" goto CLEAN
if "%choice%"=="0" exit
goto MENU

:BUILD
cls
echo ===============================================
echo [1/2]  Building Maven modules...
echo ===============================================
call mvn clean package -DskipTests
if %errorlevel% neq 0 (
    echo  Maven build failed!
    pause
    goto MENU
)
echo  Maven build successful!
echo.




echo ===============================================
echo [2/2]  Building Docker images...
echo ===============================================
docker-compose build
if %errorlevel% neq 0 (
    echo  Docker build failed!
    pause
    goto MENU
)
echo  Docker images built successfully!

REM Prune builder cache cũ (giữ cache cần thiết cho latest)
docker builder prune --filter "until=1h" -f
echo Pruned old Docker builder cache.
echo.
pause
goto MENU

:RUN
cls
echo ===============================================
echo  Starting DFSS containers...
echo ===============================================
docker-compose up -d
if %errorlevel% neq 0 (
    echo  Failed to start containers!
    pause
    goto MENU
)
echo  System is running!
echo.
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
echo.
echo  Gateway: http://localhost:8080
echo  Nodes:   node1(8082), node2(8083), node3(8084), node4(8085), node5(8086)
echo.
pause
goto MENU

:STOP
cls
echo ===============================================
echo Stopping DFSS containers...
echo ===============================================
docker-compose down
echo  All containers stopped.
echo.
pause
goto MENU

:CLEAN
cls
echo ===============================================
echo Cleaning up ALL Docker resources...
echo ===============================================
docker-compose down --rmi all -v
echo All containers, images, and volumes removed.
echo.
pause
goto MENU
