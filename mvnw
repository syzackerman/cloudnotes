#!/usr/bin/env sh
set -e

MAVEN_USER_HOME="${MAVEN_USER_HOME:-"$HOME/.m2"}"
LOCAL_MAVEN="$MAVEN_USER_HOME/wrapper/dists/apache-maven-3.9.11/a2d47e15/bin/mvn"

if command -v mvn >/dev/null 2>&1; then
  exec mvn "$@"
fi

if [ -x "$LOCAL_MAVEN" ]; then
  exec "$LOCAL_MAVEN" "$@"
fi

echo "Maven is not installed and the Maven 3.9.11 wrapper distribution is not cached." >&2
echo "Install Maven or run the standard Maven Wrapper generation command, then retry ./mvnw." >&2
exit 127
