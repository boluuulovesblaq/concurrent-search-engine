import javax.swing.SwingUtilities;

public class Main {
    public static void main(String[] args) {
        String tavilyKey = System.getenv("TAVILY_API_KEY");

        if (tavilyKey == null) {
            System.out.println("Missing TAVILY_API_KEY!");
            return;
        }

        SwingUtilities.invokeLater(() -> new SearchEngineGUI(tavilyKey).createAndShow());
    }
}