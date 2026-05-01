@echo off
setlocal

java -Xss10M -Djava.util.logging.config.file=logging.properties -cp "%~dp0target\classes" com.quaxt.mcc.Mcc %*
exit /b %ERRORLEVEL%
