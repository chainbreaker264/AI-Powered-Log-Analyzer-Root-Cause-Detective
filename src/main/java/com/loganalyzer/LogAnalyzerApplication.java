package com.loganalyzer;

import com.loganalyzer.controller.LogAnalyzerHttpServer;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * Application entry point for REST API / server mode.
 * Loads application.properties, configures logging, and starts the
 * built-in HTTP server. For single-file CLI analysis use
 * com.loganalyzer.cli.LogAnalyzerCLI instead.
 * 
 * Run:
 * java -cp output lib/* com.loganalyzer.LogAnalyzerApplication
 */
public class LogAnalyzerApplication {

    private static final Logger LOG = Logger.getLogger(LogAnalyzerApplication.class.getName());
    private static final String PROPERTIES_FILE = "application.properties";

    public static void main(String[] args) throws Exception {
        System.out.println("Loading Properties...");

        Properties props = loadProperties();
        configureLogging(props);

        String appName = props.getProperty("app.name", "log-analyzer");
        int port = Integer.parseInt(props.getProperty("server.port", "8080"));
        String apiKey = props.getProperty("anthropic.api.key", "");

        LOG.info("Starting " + appName + " on port " + port);

        if (apiKey.isBlank() || apiKey.startsWith("YOUR_")) {
            LOG.warning("anthropic.api.key is not set - AI analysis will use heuristic fallback.");
        }

        LogAnalyzerHttpServer server = new LogAnalyzerHttpServer(port, apiKey);
        server.start();

        LOG.info("Server is running at http://localhost:" + port + "/");
        LOG.info("GET   http://localhost:" + port + "/health       -> health check");
        LOG.info("GET   http://localhost:" + port + "/api/analyze/demo -> demo analyzer");
        LOG.info("POST  http://localhost:" + port + "/api/analyze      -> analyze log (body = raw log lines)");
        LOG.info("Press CTRL+C to stop.");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("Shutting down...");
            server.stop();
        }));

        Thread.currentThread().join();
    }

    private static Properties loadProperties() {
        Properties props = new Properties();
        // Try current directory first, then classpath
        try (InputStream input = new FileInputStream(PROPERTIES_FILE)) {
            props.load(input);
            System.out.println("[INFO] Loaded configuration from " + PROPERTIES_FILE);
        } catch (IOException e) {
            try (InputStream input = LogAnalyzerApplication.class.getClassLoader()
                    .getResourceAsStream(PROPERTIES_FILE)) {
                if (input != null) {
                    props.load(input);
                } else {
                    System.out.println("[WARN] application.properties not found - using built-in defaults");
                }
            } catch (IOException ignored) {}
        }
        return props;
    }

    private static void configureLogging(Properties props) {
        System.setProperty("java.util.logging.SimpleFormatter.format",
                "[%1$tF %1$tT] [%4$-7s] %5$s%6$s%n");
        Logger root = Logger.getLogger("");
        Level level = Level.parse(props.getProperty("log.level", "INFO"));
        root.setLevel(level);
        for (var h : root.getHandlers()) { root.removeHandler(h); }
        ConsoleHandler handler = new ConsoleHandler();
        handler.setFormatter(new SimpleFormatter());
        handler.setLevel(level);
        root.addHandler(handler);
    }
}
