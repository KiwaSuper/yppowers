@echo off
setlocal
set "GRADLE_VERSION=9.2.1"
set "GRADLE_SHA256=72f44c9f8ebcb1af43838f45ee5c4aa9c5444898b3468ab3f4af7b6076c5bc3f"
if "%GRADLE_USER_HOME%"=="" set "GRADLE_USER_HOME=%USERPROFILE%\.gradle"
set "INSTALL_ROOT=%GRADLE_USER_HOME%\yppowers-bootstrap"
set "GRADLE_HOME=%INSTALL_ROOT%\gradle-%GRADLE_VERSION%"
if exist "%GRADLE_HOME%\bin\gradle.bat" goto run_gradle
where powershell.exe >nul 2>nul || (echo Error: PowerShell is required to download and verify Gradle.& exit /b 1)
if not exist "%INSTALL_ROOT%" mkdir "%INSTALL_ROOT%"
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $u='https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip'; $z='%INSTALL_ROOT%\gradle-%GRADLE_VERSION%-bin.zip'; Invoke-WebRequest -Uri $u -OutFile $z; $h=(Get-FileHash -Algorithm SHA256 $z).Hash.ToLowerInvariant(); if ($h -ne '%GRADLE_SHA256%') { Remove-Item $z -Force; throw 'Gradle distribution SHA-256 did not match pinned checksum' }; Expand-Archive -LiteralPath $z -DestinationPath '%INSTALL_ROOT%' -Force; Remove-Item $z -Force"
if errorlevel 1 exit /b 1
:run_gradle
call "%GRADLE_HOME%\bin\gradle.bat" %*
exit /b %ERRORLEVEL%
