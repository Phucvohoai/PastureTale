@echo off
title HaySheep Farm
chcp 65001 > nul
del /Q bin\*.class 2>nul
del /Q bin\model\*.class 2>nul
del /Q bin\db\*.class 2>nul
del /Q bin\game\*.class 2>nul
javac -cp ".;lib/sqlite-jdbc-3.49.1.0.jar;lib/gson-2.12.1.jar" src/*.java src/model/*.java src/db/*.java src/game/*.java -d bin

java -cp "bin;lib/sqlite-jdbc-3.49.1.0.jar;lib/gson-2.12.1.jar" Main

pause