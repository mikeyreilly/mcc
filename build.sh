#!/bin/bash
rm -rf target/classes
javac --source 25 -g  -d target/classes --source-path src/main/java src/main/java/com/quaxt/mcc/Mcc.java
