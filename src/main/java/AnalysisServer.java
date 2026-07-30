import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

/**
 * Serves the analysis UI over HTTP instead of a Swing window.
 * GET  /              -> static frontend (src/main/resources/web)
 * POST /api/analyze   -> runs the same AnalysisRunner used by the old GUI, returns JSON
 */
public class AnalysisServer {

    private final String tavilyKey;

    public AnalysisServer(String tavilyKey) {
        this.tavilyKey = tavilyKey;
    }

    public void start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/analyze", this::handleAnalyze);
        server.createContext("/", this::handleStatic);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.println("Server running at http://localhost:" + port);
    }

    private void handleAnalyze(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Allow", "POST");
            exchange.sendResponseHeaders(405, -1);
            return;
        }

        PrintStream originalOut = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setOut(new PrintStream(new TeeOutputStream(originalOut, captured), true, StandardCharsets.UTF_8));

        try {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            JSONObject req = new JSONObject(body.isBlank() ? "{}" : body);

            String crimeQuery = req.optString("crimeQuery", "crime reporting systems papers");
            String headingsQuery = req.optString("headingsQuery", "deep learning model journal papers");
            int maxResults = req.optInt("maxResults", 12);

            AnalysisTask crimeTask = new AnalysisTask(
                    crimeQuery, crimeQuery, maxResults, AnalysisTask.Mode.CRIME_FEATURES, tavilyKey);
            AnalysisTask headingsTask = new AnalysisTask(
                    headingsQuery, headingsQuery, maxResults, AnalysisTask.Mode.DL_HEADINGS, tavilyKey);

            AnalysisRunner runner = new AnalysisRunner();
            Map<String, AnalysisResult> results = runner.runAll(List.of(crimeTask, headingsTask));

            JSONObject response = new JSONObject();
            for (Map.Entry<String, AnalysisResult> entry : results.entrySet()) {
                JSONObject r = new JSONObject();
                r.put("counts", entry.getValue().counts());
                r.put("sourceUrls", entry.getValue().sourceUrls());
                response.put(entry.getKey(), r);
            }
            response.put("log", captured.toString(StandardCharsets.UTF_8));
            sendJson(exchange, 200, response.toString());
        } catch (Exception e) {
            JSONObject err = new JSONObject();
            err.put("error", e.getMessage() == null ? e.toString() : e.getMessage());
            err.put("log", captured.toString(StandardCharsets.UTF_8));
            sendJson(exchange, 500, err.toString());
        } finally {
            System.setOut(originalOut);
        }
    }

    /** Writes to two output streams at once — lets us keep printing to the real console
     *  while also capturing the same text to send back to the browser. */
    private static class TeeOutputStream extends java.io.OutputStream {
        private final java.io.OutputStream a, b;
        TeeOutputStream(java.io.OutputStream a, java.io.OutputStream b) { this.a = a; this.b = b; }
        @Override public void write(int c) throws IOException { a.write(c); b.write(c); }
        @Override public void write(byte[] buf, int off, int len) throws IOException { a.write(buf, off, len); b.write(buf, off, len); }
        @Override public void flush() throws IOException { a.flush(); b.flush(); }
    }

    private void handleStatic(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (path.equals("/") || path.isEmpty()) {
            path = "/index.html";
        }
        // guard against path traversal outside the web/ resource folder
        String resourcePath = "web" + path.replace("..", "");

        InputStream in = getClass().getClassLoader().getResourceAsStream(resourcePath);
        if (in == null) {
            exchange.sendResponseHeaders(404, -1);
            return;
        }
        byte[] bytes = in.readAllBytes();
        exchange.getResponseHeaders().set("Content-Type", contentTypeFor(path));
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String contentTypeFor(String path) {
        if (path.endsWith(".html")) return "text/html; charset=utf-8";
        if (path.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (path.endsWith(".css")) return "text/css; charset=utf-8";
        return "application/octet-stream";
    }

    private void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
