#!/bin/bash
rm -rf target/classes
javac --enable-preview --source 23 -g  -d target/classes --source-path src/main/java src/main/java/com/quaxt/mcc/Mcc.java
