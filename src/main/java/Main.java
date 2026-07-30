import java.nio.file.Files;
import java.nio.file.Path;

public class Main {
    public static void main(String[] args) throws Exception {
        String tavilyKey = System.getenv("TAVILY_API_KEY");

        if (tavilyKey == null) {
            tavilyKey = loadKeyFromDotEnv();
        }

        if (tavilyKey == null || tavilyKey.isBlank()) {
            System.out.println("Missing TAVILY_API_KEY! Set it as an environment variable, " +
                    "or create a .env file in the project root containing:\nTAVILY_API_KEY=your_key_here");
            return;
        }

        String portEnv = System.getenv("PORT");
        int port = (portEnv != null) ? Integer.parseInt(portEnv) : 8080;        new AnalysisServer(tavilyKey).start(port);
        System.out.println("Open http://localhost:" + port + " in your browser.");
    }

    /**
     * Reads TAVILY_API_KEY=... from a .env file in the project root, if present.
     * Lets you keep the key locally without exporting it in your shell every time.
     */
    private static String loadKeyFromDotEnv() {
        Path envFile = Path.of(".env");
        if (!Files.exists(envFile)) {
            return null;
        }
        try {
            for (String line : Files.readAllLines(envFile)) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) {
                    continue;
                }
                String[] parts = line.split("=", 2);
                if (parts[0].trim().equals("TAVILY_API_KEY")) {
                    return parts[1].trim();
                }
            }
        } catch (Exception e) {
            System.out.println("Could not read .env file: " + e.getMessage());
        }
        return null;
    }
}
