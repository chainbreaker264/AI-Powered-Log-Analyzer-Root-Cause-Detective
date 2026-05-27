#!/usr/bin/env bash
# build.sh - Compile all Java sources into output/

set -e

ROOT="$( cd "$(dirname "$0")" && pwd )"
SRC="$ROOT/src/main/java"
TEST_SRC="$ROOT/src/test/java"
OUT="$ROOT/output"

if [ -x "/C/Program Files/Eclipse Adoptium/jdk-21.0.2.13-hotspot/bin/javac.exe" ]; then
    JAVAC="/C/Program Files/Eclipse Adoptium/jdk-21.0.2.13-hotspot/bin/javac.exe"
    JAVA="/C/Program Files/Eclipse Adoptium/jdk-21.0.2.13-hotspot/bin/java.exe"
elif [ -x "/c/Program Files/Eclipse Adoptium/jdk-21.0.2.13-hotspot/bin/javac" ]; then
    JAVAC="/c/Program Files/Eclipse Adoptium/jdk-21.0.2.13-hotspot/bin/javac"
    JAVA="/c/Program Files/Eclipse Adoptium/jdk-21.0.2.13-hotspot/bin/java"
else
    JAVAC="javac"
    JAVA="java"
fi

EXPORTS="--add-exports=jdk.httpserver/com.sun.net.httpserver.internal=ALL-UNNAMED"

echo "== AI-Powered Log Analyzer - Build Run =="
echo ""

mkdir -p "$OUT"

"$JAVAC" $EXPORTS -d "$OUT" \
    "$SRC"/com/loganalyzer/model/LogEntry.java \
    "$SRC"/com/loganalyzer/model/AnalysisResult.java \
    "$SRC"/com/loganalyzer/model/AlertPayload.java \
    "$SRC"/com/loganalyzer/util/JsonUtil.java \
    "$SRC"/com/loganalyzer/util/ApiClient.java \
    "$SRC"/com/loganalyzer/service/LogIngestionService.java \
    "$SRC"/com/loganalyzer/service/EmbeddingService.java \
    "$SRC"/com/loganalyzer/service/RootCauseAnalysisService.java \
    "$SRC"/com/loganalyzer/LogAnalysisOrchestrator.java \
    "$SRC"/com/loganalyzer/LogAnalyzerApplication.java \
    "$SRC"/com/loganalyzer/cli/LogAnalyzerCLI.java \
    "$SRC"/com/loganalyzer/controller/LogAnalyzerHttpServer.java \
    "$TEST_SRC"/com/loganalyzer/LogAnalyzerTest.java \
    "$TEST_SRC"/com/loganalyzer/LogAnalyzerApplicationTests.java

echo "All sources compiled - 0 errors."
echo ""
echo "== Build Complete =="
echo "Tests:   $JAVA $EXPORTS -cp output com.loganalyzer.LogAnalyzerTest"
echo "CLI:     $JAVA $EXPORTS -cp output com.loganalyzer.cli.LogAnalyzerCLI example-logs.txt --no-ai"
echo "Server:  $JAVA $EXPORTS -cp output com.loganalyzer.LogAnalyzerApplication"
