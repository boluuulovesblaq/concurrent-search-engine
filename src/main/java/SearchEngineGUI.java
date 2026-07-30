import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Map;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;

public class SearchEngineGUI {

    private final String tavilyKey;

    private JTextField crimeQueryField;
    private JTextField headingsQueryField;
    private JSpinner maxResultsSpinner;
    private JTextArea logArea;
    private JPanel chartsPanel;
    private JButton runButton;

    public SearchEngineGUI(String tavilyKey) {
        this.tavilyKey = tavilyKey;
    }

    public void createAndShow() {
        JFrame frame = new JFrame("Concurrent Search Engine - SERP Analysis");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1200, 800);
        frame.setLayout(new BorderLayout(10, 10));

        frame.add(buildInputPanel(), BorderLayout.NORTH);

        chartsPanel = new JPanel(new GridLayout(1, 2, 10, 10));
        chartsPanel.add(placeholderLabel("Crime Reporting Features result will appear here"));
        chartsPanel.add(placeholderLabel("DL Sub-Headings result will appear here"));
        frame.add(chartsPanel, BorderLayout.CENTER);

        logArea = new JTextArea(8, 80);
        logArea.setEditable(false);
        frame.add(new JScrollPane(logArea), BorderLayout.SOUTH);

        frame.setVisible(true);
    }

    private JPanel buildInputPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 5, 5, 5);
        c.fill = GridBagConstraints.HORIZONTAL;

        c.gridx = 0; c.gridy = 0; c.weightx = 0;
        panel.add(new JLabel("Task 1 query (features):"), c);
        c.gridx = 1; c.weightx = 1;
        crimeQueryField = new JTextField("crime reporting systems papers");
        panel.add(crimeQueryField, c);

        c.gridx = 0; c.gridy = 1; c.weightx = 0;
        panel.add(new JLabel("Task 2 query (sub-headings):"), c);
        c.gridx = 1; c.weightx = 1;
        headingsQueryField = new JTextField("deep learning model journal papers");
        panel.add(headingsQueryField, c);

        c.gridx = 0; c.gridy = 2; c.weightx = 0;
        panel.add(new JLabel("Results per query:"), c);
        c.gridx = 1; c.weightx = 0;
        maxResultsSpinner = new JSpinner(new SpinnerNumberModel(12, 3, 30, 1));
        panel.add(maxResultsSpinner, c);

        c.gridx = 1; c.gridy = 3;
        runButton = new JButton("Run Both Analyses");
        runButton.addActionListener(e -> runAnalyses());
        panel.add(runButton, c);

        return panel;
    }

    private JLabel placeholderLabel(String text) {
        JLabel label = new JLabel(text, SwingConstants.CENTER);
        label.setForeground(Color.GRAY);
        return label;
    }

    private void runAnalyses() {
        String crimeQuery = crimeQueryField.getText().trim();
        String headingsQuery = headingsQueryField.getText().trim();
        int maxResults = (Integer) maxResultsSpinner.getValue();

        if (crimeQuery.isEmpty() || headingsQuery.isEmpty()) {
            logArea.setText("Both queries must be filled in.\n");
            return;
        }

        runButton.setEnabled(false);
        logArea.setText("Running both analyses concurrently...\n");
        chartsPanel.removeAll();
        chartsPanel.add(placeholderLabel("Working..."));
        chartsPanel.add(placeholderLabel("Working..."));
        chartsPanel.revalidate();
        chartsPanel.repaint();

        new SwingWorker<Map<String, AnalysisResult>, String>() {

            @Override
            protected Map<String, AnalysisResult> doInBackground() {
                AnalysisTask crimeTask = new AnalysisTask(
                        "Crime Reporting Features", crimeQuery,
                        maxResults, AnalysisTask.Mode.CRIME_FEATURES, tavilyKey);

                AnalysisTask headingsTask = new AnalysisTask(
                        "DL Paper Sub-Headings", headingsQuery,
                        maxResults, AnalysisTask.Mode.DL_HEADINGS, tavilyKey);

                publish("Searching and fetching sources for both tasks concurrently...");
                AnalysisRunner runner = new AnalysisRunner();
                return runner.runAll(List.of(crimeTask, headingsTask));
            }

            @Override
            protected void process(List<String> chunks) {
                for (String line : chunks) {
                    logArea.append(line + "\n");
                }
            }

            @Override
            protected void done() {
                try {
                    Map<String, AnalysisResult> results = get();

                    chartsPanel.removeAll();
                    for (Map.Entry<String, AnalysisResult> entry : results.entrySet()) {
                        if (entry.getValue().counts().isEmpty()) {
                            chartsPanel.add(placeholderLabel(
                                    entry.getKey() + ": no usable results found"));
                        } else {
                            chartsPanel.add(buildResultPanel(entry.getKey(), entry.getValue()));
                        }
                    }
                    chartsPanel.revalidate();
                    chartsPanel.repaint();

                    logArea.append("Done. Displaying results.\n");

                } catch (Exception ex) {
                    logArea.append("Error: " + ex.getMessage() + "\n");
                } finally {
                    runButton.setEnabled(true);
                }
            }
        }.execute();
    }

    private JPanel buildResultPanel(String title, AnalysisResult result) {
        Map<String, Integer> counts = result.counts();
        JPanel panel = new JPanel(new BorderLayout(5, 5));

        JFreeChart chart = ChartRenderer.buildChart(title, counts, 15);
        panel.add(new ChartPanel(chart), BorderLayout.CENTER);

        List<Map.Entry<String, Integer>> ranked = counts.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(10)
                .toList();

        StringBuilder summary = new StringBuilder();
        summary.append(title).append(" - ").append(counts.size())
                .append(" distinct items found. Top results:\n");
        int rank = 1;
        for (Map.Entry<String, Integer> entry : ranked) {
            summary.append(rank++).append(". ").append(entry.getKey())
                    .append(" (").append(entry.getValue()).append(" paper")
                    .append(entry.getValue() == 1 ? "" : "s").append(")\n");
        }

        summary.append("\nSources (").append(result.sourceUrls().size()).append("):\n");
        for (String url : result.sourceUrls()) {
            summary.append("- ").append(url).append("\n");
        }

        JTextArea summaryArea = new JTextArea(summary.toString());
        summaryArea.setEditable(false);
        summaryArea.setLineWrap(true);
        summaryArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane summaryScroll = new JScrollPane(summaryArea);
        summaryScroll.setPreferredSize(new Dimension(0, 220));
        panel.add(summaryScroll, BorderLayout.SOUTH);

        return panel;
    }
}