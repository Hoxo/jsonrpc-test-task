#!/bin/sh

java \
  $JVM_OPTIONS \
  -Dfile.encoding=UTF-8 \
  -XX:+HeapDumpOnOutOfMemoryError \
  -jar /app/app.jar
