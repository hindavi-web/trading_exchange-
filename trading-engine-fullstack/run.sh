#!/usr/bin/env bash
set -e

echo "========================================================="
echo " Starting Apex Stock Trading Engine & Mobile Backend"
echo "========================================================="

mkdir -p bin
echo "Compiling Java source files..."
javac -d bin -cp "bin:lib/*" $(find src/main/java -name "*.java")

echo "Launching server on http://localhost:8080 ..."
java -cp "bin:lib/*" com.engine.Main
