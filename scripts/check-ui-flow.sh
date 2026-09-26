#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
./mvnw -q -DskipTests test-compile dependency:build-classpath \
  -Dmdep.outputFile=target/ui-flow-classpath.txt
classpath="target/test-classes:target/classes:$(cat target/ui-flow-classpath.txt)"
java -cp "$classpath" top.zedo.skin.uis.ui.StudioFlowSmoke
java -cp "$classpath" top.zedo.skin.v.VEditorFlowSmoke
