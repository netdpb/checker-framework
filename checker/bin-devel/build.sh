#!/bin/bash

echo Entering checker/bin-devel/build.sh in "$(pwd)"

# Fail the whole script if any command fails
set -e

echo "initial CHECKERFRAMEWORK=$CHECKERFRAMEWORK"
export CHECKERFRAMEWORK="${CHECKERFRAMEWORK:-$(pwd -P)}"
echo "CHECKERFRAMEWORK=$CHECKERFRAMEWORK"

export SHELLOPTS
echo "SHELLOPTS=${SHELLOPTS}"

if [ "$(uname)" == "Darwin" ] ; then
  export JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home)}
else
  # shellcheck disable=SC2230
  export JAVA_HOME=${JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$(which javac)")")")}
fi
echo "JAVA_HOME=${JAVA_HOME}"

# Using `(cd "$CHECKERFRAMEWORK" && ./gradlew getPlumeScripts -q)` leads to infinite regress.
PLUME_SCRIPTS="$CHECKERFRAMEWORK/checker/bin-devel/.plume-scripts"
if [ -d "$PLUME_SCRIPTS" ] ; then
  (cd "$PLUME_SCRIPTS" && git pull -q)
else
  (cd "$CHECKERFRAMEWORK/checker/bin-devel" && \
      (git clone --depth 1 -q https://github.com/plume-lib/plume-scripts.git .plume-scripts || \
       git clone --depth 1 -q https://github.com/plume-lib/plume-scripts.git .plume-scripts))
fi

# Clone the annotated JDK into ../jdk .
"$PLUME_SCRIPTS/git-clone-related" typetools jdk

## Build stubparser
"$PLUME_SCRIPTS/git-clone-related" typetools stubparser -q

echo "Checking out the stubparser commit at which jspecify last merged from upstream."
git -C ../stubparser checkout -q dd2c1d4a8b3c428d554d6fab6aa1b840d4031985

echo "Running:  (cd ../stubparser/ && ./.build-without-test.sh)"
(cd ../stubparser/ && ./.build-without-test.sh)
echo "... done: (cd ../stubparser/ && ./.build-without-test.sh)"


## Build JSpecify, only for the purpose of using its tests.
"$PLUME_SCRIPTS/git-clone-related" jspecify jspecify -q
if type -p java; then
  _java=java
elif [[ -n "$JAVA_HOME" ]] && [[ -x "$JAVA_HOME/bin/java" ]];  then
  _java="$JAVA_HOME/bin/java"
else
  echo "Can't find java"
  exit 1
fi

if false; then # DO NOT BUILD DURING THE BUILD
  version=$("$_java" -version 2>&1 | head -1 | cut -d'"' -f2 | sed '/^1\./s///' | cut -d'.' -f1)
  if [[ "$version" -ge 9 ]]; then
    echo "Running:  (cd ../jspecify/ && ./gradlew build)"
    # If failure, retry in case the failure was due to network lossage.
    (cd ../jspecify/ && export JDK_JAVA_OPTIONS='--add-opens jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED --add-opens jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED --add-opens jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED --add-opens jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED --add-opens jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED --add-opens jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED --add-opens jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED --add-opens jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED --add-opens jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED' && (./gradlew build || (sleep 60 && ./gradlew build)))
    echo "... done: (cd ../jspecify/ && ./gradlew build)"
  fi
fi


## Compile

if false; then # DO NOT BUILD DURING THE BUILD
  # Downloading the gradle wrapper sometimes fails.
  # If so, the next command gets another chance to try the download.
  (./gradlew help || sleep 10) > /dev/null 2>&1

  echo "running \"./gradlew assemble\" for checker-framework"
  ./gradlew assemble --console=plain --warning-mode=all -s -Dorg.gradle.internal.http.socketTimeout=60000 -Dorg.gradle.internal.http.connectionTimeout=60000
fi

echo Exiting checker/bin-devel/build.sh in "$(pwd)"
