import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.CategoryLabelPositions;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.category.DefaultCategoryDataset;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Renders a ranked bar chart (descending by count) for one analysis's
 * results and writes it to a PNG file. Generic over any item->count map,
 * so the same renderer handles both required analyses (crime features,
 * DL headings) -- just called twice with different data/titles.
 */
public class ChartRenderer {

    // Cap how many bars we draw -- 100 distinct headings on one chart
    // is unreadable. Assignment asks for >=10 features, so this default
    // comfortably shows everything relevant while staying legible.
    private static final int DEFAULT_MAX_BARS = 20;

    /**
     * Renders counts as a horizontal bar chart, ranked highest-to-lowest,
     * and saves it as a PNG at outputPath.
     */
    public static void renderBarChart(String title, Map<String, Integer> counts, String outputPath) {
        renderBarChart(title, counts, outputPath, DEFAULT_MAX_BARS);
    }

    public static void renderBarChart(String title, Map<String, Integer> counts,
                                      String outputPath, int maxBars) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();

        List<Map.Entry<String, Integer>> ranked = counts.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue())) // descending
                .limit(maxBars)
                .collect(Collectors.toList());

        for (Map.Entry<String, Integer> entry : ranked) {
            dataset.addValue(entry.getValue(), "Frequency", entry.getKey());
        }

        JFreeChart chart = ChartFactory.createBarChart(
                title,
                "Item",
                "Paper Count",
                dataset,
                PlotOrientation.HORIZONTAL, // horizontal reads better with long labels
                false,  // no legend needed, single series
                true,
                false
        );

        // Rotate/space category labels so longer feature/heading names don't overlap.
        CategoryPlot plot = chart.getCategoryPlot();
        CategoryAxis axis = plot.getDomainAxis();
        axis.setCategoryLabelPositions(CategoryLabelPositions.STANDARD);

        try {
            File outputFile = new File(outputPath);
            File parentDir = outputFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            ChartUtils.saveChartAsPNG(outputFile, chart, 900, 600);
            System.out.println("Chart saved: " + outputPath + " (" + ranked.size() + " items)");
        } catch (IOException e) {
            System.err.println("Failed to save chart \"" + title + "\": " + e.getMessage());
        }
    }

    /**
     * Convenience for AnalysisRunner's output shape: renders one chart per
     * analysis name, writing each to outputDir/<sanitized-name>.png.
     */
    public static void renderAll(Map<String, Map<String, Integer>> resultsByAnalysis, String outputDir) {
        for (Map.Entry<String, Map<String, Integer>> entry : resultsByAnalysis.entrySet()) {
            String safeName = entry.getKey().toLowerCase().replaceAll("[^a-z0-9]+", "_");
            String path = outputDir + "/" + safeName + ".png";
            renderBarChart(entry.getKey(), entry.getValue(), path);
        }
    }
}