import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.CategoryLabelPositions;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.category.DefaultCategoryDataset;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ChartRenderer {

    public static JFreeChart buildChart(String title, Map<String, Integer> counts, int maxBars) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();

        List<Map.Entry<String, Integer>> ranked = counts.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(maxBars)
                .collect(Collectors.toList());

        for (Map.Entry<String, Integer> entry : ranked) {
            dataset.addValue(entry.getValue(), "Frequency", entry.getKey());
        }

        JFreeChart chart = ChartFactory.createBarChart(
                title, "Item", "Paper Count", dataset,
                PlotOrientation.HORIZONTAL, false, true, false
        );

        CategoryPlot plot = chart.getCategoryPlot();
        CategoryAxis axis = plot.getDomainAxis();
        axis.setCategoryLabelPositions(CategoryLabelPositions.STANDARD);

        return chart;
    }
}