@echo off
chcp 65001 > nul

echo === AIJudge Build Script (Windows) ===

if not exist out mkdir out

echo Dang tim file nguon...
dir /s /b src\main\java\*.java > sources.txt

echo Dang bien dich...
javac -encoding UTF-8 -d out @sources.txt
if errorlevel 1 (
    echo Bien dich that bai!
    del sources.txt
    pause
    exit /b 1
)

del sources.txt
echo Bien dich thanh cong!

echo Khoi dong AI Judge...
java -cp out aijudge.Main
pause