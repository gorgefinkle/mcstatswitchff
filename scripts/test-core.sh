#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
mkdir -p build/core-test
javac -d build/core-test src/main/java/dev/freakyfriday/core/*.java src/test/java/dev/freakyfriday/core/*.java src/main/java/dev/freakyfriday/mind/MindLedger.java src/test/java/dev/freakyfriday/mind/MindLedgerSelfTest.java src/main/java/dev/freakyfriday/mind/DynamicObjectGraph.java src/test/java/dev/freakyfriday/mind/DynamicObjectGraphSelfTest.java
java -ea -cp build/core-test dev.freakyfriday.core.CategoryExchangeSelfTest
java -ea -cp build/core-test dev.freakyfriday.core.CategoryDefinitionsSelfTest
java -ea -cp build/core-test dev.freakyfriday.core.CategorySchemaSelfTest
java -ea -cp build/core-test dev.freakyfriday.core.CategoryOffersSelfTest
java -ea -cp build/core-test dev.freakyfriday.mind.MindLedgerSelfTest
java -ea -cp build/core-test dev.freakyfriday.mind.DynamicObjectGraphSelfTest
