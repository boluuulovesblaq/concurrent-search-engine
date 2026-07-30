import java.util.List;
import java.util.Map;

// Everything one AnalysisTask produces: the ranked counts AND which
// source URLs actually contributed to those counts.
public record AnalysisResult(Map<String, Integer> counts, List<String> sourceUrls) {}