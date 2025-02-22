#!/bin/bash
rm -rf target/classes
javac  -d target/classes --source-path src/main/java src/main/java/com/quaxt/mcc/Mcc.java
