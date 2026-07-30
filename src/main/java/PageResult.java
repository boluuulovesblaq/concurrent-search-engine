import java.util.List;

public record PageResult(String url, String text, List<String> headings,
                         boolean success, long fetchTimeMs) {

    public boolean hasUsefulContent() {
        return success && text != null && text.length() >= 100;
    }
}