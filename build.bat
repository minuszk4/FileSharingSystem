@echo off
title DFSS - Build Manager
color 0A

:MENU
cls
echo ===============================================
echo   DFSS - Build System Manager
echo ===============================================
echo.
echo   [1] Build Backend (Maven)
echo   [2] Build Frontend (React)
echo   [3] Build Docker images
echo   [0] Exit
echo.
set /p choice= Select an option (0-3):

if "%choice%"=="1" goto BUILD_BACKEND
if "%choice%"=="2" goto BUILD_FRONTEND
if "%choice%"=="3" goto DOCKER_MENU
if "%choice%"=="0" exit
goto MENU

:BUILD_BACKEND
cls
echo ===============================================
echo [1/3] Building Maven backend...
echo ===============================================
call mvn clean package -DskipTests
if %errorlevel% neq 0 (
    echo Maven build failed!
    pause
    goto MENU
)
echo Maven build successful!
echo.
pause
goto MENU

:BUILD_FRONTEND
cls
echo ===============================================
echo [2/3] Building Frontend React...
echo ===============================================
cd client
call npm install
if %errorlevel% neq 0 (
    echo NPM install failed!
    cd ..
    pause
    goto MENU
)
call npm run build
if %errorlevel% neq 0 (
    echo React build failed!
    cd ..
    pause
    goto MENU
)
cd ..
echo Frontend built successfully!
echo.
pause
goto MENU

:DOCKER_MENU
cls
echo ===============================================
echo   Build Docker Images Individually
echo ===============================================
echo.
echo   [1] Build frontend
echo   [2] Build gateway
echo   [3] Build tracker
echo   [4] Build node1
echo   [5] Build node2
echo   [6] Build node3
echo   [7] Build node4
echo   [8] Build node5
echo   [0] Back to main menu
echo.
set /p dockerChoice= Select an option (0-8):

if "%dockerChoice%"=="1" goto BUILD_FRONTEND_DOCKER
if "%dockerChoice%"=="2" goto BUILD_GATEWAY_DOCKER
if "%dockerChoice%"=="3" goto BUILD_TRACKER_DOCKER
if "%dockerChoice%"=="4" goto BUILD_NODE1_DOCKER
if "%dockerChoice%"=="5" goto BUILD_NODE2_DOCKER
if "%dockerChoice%"=="6" goto BUILD_NODE3_DOCKER
if "%dockerChoice%"=="7" goto BUILD_NODE4_DOCKER
if "%dockerChoice%"=="8" goto BUILD_NODE5_DOCKER
if "%dockerChoice%"=="0" goto MENU
goto DOCKER_MENU

:BUILD_FRONTEND_DOCKER
cls
echo Building frontend Docker image...
docker-compose build frontend
if %errorlevel% neq 0 (
    echo Build failed!
    pause
    goto DOCKER_MENU
)
echo Frontend Docker image built successfully!
pause
goto DOCKER_MENU

:BUILD_GATEWAY_DOCKER
cls
echo Building gateway Docker image...
docker-compose build gateway
if %errorlevel% neq 0 (
    echo Build failed!
    pause
    goto DOCKER_MENU
)
echo Gateway Docker image built successfully!
pause
goto DOCKER_MENU

:BUILD_TRACKER_DOCKER
cls
echo Building tracker Docker image...
docker-compose build tracker
if %errorlevel% neq 0 (
    echo Build failed!
    pause
    goto DOCKER_MENU
)
echo Tracker Docker image built successfully!
pause
goto DOCKER_MENU

:BUILD_NODE1_DOCKER
cls
echo Building node1 Docker image...
docker-compose build node1
if %errorlevel% neq 0 (
    echo Build failed!
    pause
    goto DOCKER_MENU
)
echo Node1 Docker image built successfully!
pause
goto DOCKER_MENU

:BUILD_NODE2_DOCKER
cls
echo Building node2 Docker image...
docker-compose build node2
if %errorlevel% neq 0 (
    echo Build failed!
    pause
    goto DOCKER_MENU
)
echo Node2 Docker image built successfully!
pause
goto DOCKER_MENU

:BUILD_NODE3_DOCKER
cls
echo Building node3 Docker image...
docker-compose build node3
if %errorlevel% neq 0 (
    echo Build failed!
    pause
    goto DOCKER_MENU
)
echo Node3 Docker image built successfully!
pause
goto DOCKER_MENU

:BUILD_NODE4_DOCKER
cls
echo Building node4 Docker image...
docker-compose build node4
if %errorlevel% neq 0 (
    echo Build failed!
    pause
    goto DOCKER_MENU
)
echo Node4 Docker image built successfully!
pause
goto DOCKER_MENU

:BUILD_NODE5_DOCKER
cls
echo Building node5 Docker image...
docker-compose build node5
if %errorlevel% neq 0 (
    echo Build failed!
    pause
    goto DOCKER_MENU
)
echo Node5 Docker image built successfully!
pause
goto DOCKER_MENU
