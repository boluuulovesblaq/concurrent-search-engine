import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Computes TF-IDF scores for words across a SET of documents (all papers
 * fetched for one AnalysisTask), and returns the top-N highest scoring
 * terms per document as candidate "distinctive features."
 *
 * TF  = how often a term appears in THIS document, relative to its length
 * IDF = how rare that term is ACROSS all documents in the set
 * TF-IDF = TF * IDF -> high score = common in this paper, rare elsewhere
 */
public class TfIdfExtractor {

    private static final Pattern WORD_SPLIT = Pattern.compile("[^a-zA-Z]+");

    // Generic academic/web noise words that would otherwise dominate scores
    // since they appear in almost every paper - filtered before scoring.
    private static final Set<String> STOPWORDS = Set.of(
            "the", "a", "an", "and", "or", "of", "to", "in", "on", "for", "with",
            "is", "are", "was", "were", "be", "been", "this", "that", "these", "those",
            "paper", "study", "system", "systems", "using", "used", "use", "based",
            "we", "our", "it", "its", "as", "by", "from", "at", "not", "can", "will",
            "which", "such", "also", "may", "has", "have", "had", "but", "if", "then",
            "article", "research", "et", "al", "figure", "table", "abstract", "http",
            "https", "www", "com", "org"
    );

    private static final int MIN_WORD_LENGTH = 4;
    private static final int TOP_N_PER_DOC = 15;

    /**
     * Returns top candidate terms PER document, in the same order as input.
     * Each inner list is that document's top-N distinctive terms.
     */
    public List<List<String>> extractTopTermsPerDocument(List<String> documents) {
        List<Map<String, Integer>> termCountsPerDoc = new ArrayList<>();
        for (String doc : documents) {
            termCountsPerDoc.add(countTerms(doc));
        }

        // Document frequency: how many documents contain each term at least once
        Map<String, Integer> documentFrequency = new HashMap<>();
        for (Map<String, Integer> counts : termCountsPerDoc) {
            for (String term : counts.keySet()) {
                documentFrequency.merge(term, 1, Integer::sum);
            }
        }

        int totalDocs = documents.size();
        List<List<String>> results = new ArrayList<>();

        for (Map<String, Integer> counts : termCountsPerDoc) {
            int totalTermsInDoc = counts.values().stream().mapToInt(Integer::intValue).sum();
            if (totalTermsInDoc == 0) {
                results.add(List.of());
                continue;
            }

            Map<String, Double> scores = new HashMap<>();
            for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                String term = entry.getKey();
                int termCountInDoc = entry.getValue();

                double tf = (double) termCountInDoc / totalTermsInDoc;
                double idf = Math.log((double) totalDocs / (1 + documentFrequency.get(term)));
                scores.put(term, tf * idf);
            }

            List<String> topTerms = scores.entrySet().stream()
                    .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                    .limit(TOP_N_PER_DOC)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());

            results.add(topTerms);
        }

        return results;
    }

    private Map<String, Integer> countTerms(String text) {
        Map<String, Integer> counts = new HashMap<>();
        if (text == null || text.isBlank()) return counts;

        String[] words = WORD_SPLIT.split(text.toLowerCase());
        for (String word : words) {
            if (word.length() < MIN_WORD_LENGTH) continue;
            if (STOPWORDS.contains(word)) continue;
            counts.merge(word, 1, Integer::sum);
        }
        return counts;
    }
}