@echo off
REM build.bat - Compile all Java sources into output/

setlocal enabledelayedexpansion

cd /d "%~dp0"

set ROOT=%cd%
set SRC=%ROOT%\src\main\java
set TEST_SRC=%ROOT%\src\test\java
set OUT=%ROOT%\output

if not exist "%OUT%" mkdir "%OUT%"

echo == AI-Powered Log Analyzer - Build Run ==
echo.

javac --add-exports=jdk.httpserver/com.sun.net.httpserver.internal=ALL-UNNAMED -d "%OUT%" ^
    "%SRC%\com\loganalyzer\model\LogEntry.java" ^
    "%SRC%\com\loganalyzer\model\AnalysisResult.java" ^
    "%SRC%\com\loganalyzer\model\AlertPayload.java" ^
    "%SRC%\com\loganalyzer\util\JsonUtil.java" ^
    "%SRC%\com\loganalyzer\util\ApiClient.java" ^
    "%SRC%\com\loganalyzer\service\LogIngestionService.java" ^
    "%SRC%\com\loganalyzer\service\EmbeddingService.java" ^
    "%SRC%\com\loganalyzer\service\RootCauseAnalysisService.java" ^
    "%SRC%\com\loganalyzer\LogAnalysisOrchestrator.java" ^
    "%SRC%\com\loganalyzer\LogAnalyzerApplication.java" ^
    "%SRC%\com\loganalyzer\cli\LogAnalyzerCLI.java" ^
    "%SRC%\com\loganalyzer\controller\LogAnalyzerHttpServer.java" ^
    "%TEST_SRC%\com\loganalyzer\LogAnalyzerTest.java" ^
    "%TEST_SRC%\com\loganalyzer\LogAnalyzerApplicationTests.java"

if %errorlevel% equ 0 (
    echo.
    echo All sources compiled - 0 errors.
    echo.
    echo == Build Complete ==
    echo Tests:   java --add-exports=jdk.httpserver/com.sun.net.httpserver.internal=ALL-UNNAMED -cp output com.loganalyzer.LogAnalyzerTest
    echo CLI:     java --add-exports=jdk.httpserver/com.sun.net.httpserver.internal=ALL-UNNAMED -cp output com.loganalyzer.cli.LogAnalyzerCLI example-logs.txt --no-ai
    echo Server:  java --add-exports=jdk.httpserver/com.sun.net.httpserver.internal=ALL-UNNAMED -cp output com.loganalyzer.LogAnalyzerApplication
) else (
    echo Build failed with errors!
    exit /b 1
)
