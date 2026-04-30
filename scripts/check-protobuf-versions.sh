#!/usr/bin/env bash
set -euo pipefail

for v in 3 4; do
  file=$(< "buildtools-v${v}/protobuf-version.txt")
  file=${file#v}
  pom=$(mvn help:evaluate -Dexpression="protobuf-v${v}.version" -q -DforceStdout)
  [[ "$pom" == *"$file" ]] || { echo "MISMATCH: protobuf-v${v}.version=$pom vs buildtools-v${v}/protobuf-version.txt=$file"; exit 1; }
done
