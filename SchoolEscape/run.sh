#!/bin/sh
mkdir -p out && javac -d out *.java && java -cp out Main
