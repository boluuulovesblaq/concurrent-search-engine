public record PageResult(String url, String text, boolean success, long fetchTimeMs) {

    // Convenience check: did we get enough real content to be worth using?
    public boolean hasUsefulContent() {
        return success && text != null && text.length() >= 100;
    }
}