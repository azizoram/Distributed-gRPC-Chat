#!/bin/bash

case "$(uname -s)" in
    Linux*)     OS=linux;;
    Darwin*)    OS=mac;;
    CYGWIN*)    OS=windows;;
    MINGW*)     OS=windows;;
    *)          OS="unknown"
esac


GRADLE_WRAPPER="gradlew"
if [ "$OS" == "windows" ]; then
    GRADLE_WRAPPER="gradlew.bat"
fi

./$GRADLE_WRAPPER clean build

while true; do
    for jar_file in build/libs/*.jar; do
        if [ -f "$jar_file" ]; then
            break 2  # Break both loops
        fi
    done
    sleep 1
done

cp ./build/libs/*.jar ./gRPC_chat_sem.jar