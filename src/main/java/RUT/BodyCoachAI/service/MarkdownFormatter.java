package RUT.BodyCoachAI.service;

import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.stereotype.Service;

@Service
public class MarkdownFormatter {
    
    private final Parser parser;
    private final HtmlRenderer renderer;
    
    public MarkdownFormatter() {
        this.parser = Parser.builder().build();
        this.renderer = HtmlRenderer.builder().build();
    }

    public String markdownToHtml(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return markdown;
        }
        String cleaned = markdown
                .replaceAll("[ \t]+", " ")
                .replaceAll("\n{3,}", "\n\n")
                .replaceAll(" *\n *", "\n")
                .trim();
        
        Node document = parser.parse(cleaned);
        String html = renderer.render(document);
        
        // Убираем лишние пробелы из HTML
        html = html.replaceAll(">\\s+<", "><")
                .replaceAll("\\s+", " ")
                .replaceAll(" </p>", "</p>")
                .replaceAll("<p>\\s+", "<p>")
                .replaceAll("\\s+</p>", "</p>")
                .trim();
        
        return html;
    }
}