@echo off
chcp 65001 > nul
title HaySheep Farm

java -cp "bin;lib/gson-2.12.1.jar;lib/sqlite-jdbc-3.49.1.0.jar" Main

pause
