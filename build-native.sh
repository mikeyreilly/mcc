#!/bin/bash
native-image -O3 --class-path "target/classes" com.quaxt.mcc.Mcc -o mcc-native
