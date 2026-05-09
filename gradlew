#!/bin/sh

APP_HOME=$(cd "${0%/*}" || exit 1; pwd -P)
APP_BASE_NAME=${0##*/}
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

if [ -n "$JAVA_HOME" ]; then
    JAVA_EXE=$JAVA_HOME/bin/java
else
    JAVA_EXE=java
fi

if ! "$JAVA_EXE" -version >/dev/null 2>&1; then
    echo "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH." >&2
    exit 1
fi

eval set -- $DEFAULT_JVM_OPTS '"-Dorg.gradle.appname=$APP_BASE_NAME"' -classpath '"$CLASSPATH"' org.gradle.wrapper.GradleWrapperMain "$@"
exec "$JAVA_EXE" "$@"
