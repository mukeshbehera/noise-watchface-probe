#!/bin/sh
# Gradle wrapper launcher (POSIX). Generated for CI and local use.
set -e

APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
JAVACMD="${JAVA_HOME:-}/bin/java"
if [ ! -x "$JAVACMD" ]; then
  JAVACMD=java
fi

CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
exec "$JAVACMD" \
  "-Dorg.gradle.appname=gradlew" \
  -classpath "$CLASSPATH" \
  org.gradle.wrapper.GradleWrapperMain \
  "$@"
