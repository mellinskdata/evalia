#!/usr/bin/env sh
set -eu
if ! command -v mvn >/dev/null 2>&1; then
  echo "Maven nao encontrado. Instale o Apache Maven e tente novamente."
  exit 1
fi
mvn clean javafx:run
