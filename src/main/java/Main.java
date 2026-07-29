import java.util.List;
import java.util.Map;

public class Main {
    public static void main(String[] args) {
        String tavilyKey = System.getenv("TAVILY_API_KEY");
        String anthropicKey = System.getenv("ANTHROPIC_API_KEY");

        if (tavilyKey == null || anthropicKey == null) {
            System.out.println("Missing TAVILY_API_KEY or ANTHROPIC_API_KEY!");
            return;
        }

        AnalysisTask crimeTask = new AnalysisTask(
                "Crime Reporting Features",
                "crime reporting systems papers",
                5,
                SemanticExtractor.Mode.CRIME_FEATURES,
                tavilyKey,
                anthropicKey
        );

        AnalysisTask headingsTask = new AnalysisTask(
                "DL Paper Sub-Headings",
                "deep learning model journal papers",
                5,
                SemanticExtractor.Mode.DL_HEADINGS,
                tavilyKey,
                anthropicKey
        );

        AnalysisRunner runner = new AnalysisRunner();

        long startTime = System.currentTimeMillis();
        Map<String, Map<String, Integer>> results = runner.runAll(List.of(crimeTask, headingsTask));
        long endTime = System.currentTimeMillis();

        System.out.println();
        System.out.println("Both analyses done in " + (endTime - startTime) + " ms");

        ChartRenderer.renderAll(results, "charts");
    }
}