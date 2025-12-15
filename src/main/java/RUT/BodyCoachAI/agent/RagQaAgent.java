package RUT.BodyCoachAI.agent;

import RUT.BodyCoachAI.service.ChatHistoryService;
import RUT.BodyCoachAI.service.GigaChatService;
import RUT.BodyCoachAI.service.MarkdownFormatter;
import RUT.BodyCoachAI.service.RagService;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class RagQaAgent {
    
    private final ChatLanguageModel chatModel;
    private final ContentRetriever contentRetriever;
    private final ChatHistoryService chatHistoryService;
    private final MarkdownFormatter markdownFormatter;

    public RagQaAgent(GigaChatService gigaChatService, RagService ragService, ChatHistoryService chatHistoryService, MarkdownFormatter markdownFormatter) {
        this.chatModel = gigaChatService.getChatLanguageModel();
        this.contentRetriever = ragService.getContentRetriever();
        this.chatHistoryService = chatHistoryService;
        this.markdownFormatter = markdownFormatter;
    }

    public String answerQuestion(String question, String userId) {
        Query query = Query.from(question);
        List<Content> relevantContents = contentRetriever.retrieve(query);

        String context = relevantContents.stream()
                .map(Content::textSegment)
                .map(segment -> segment.text())
                .collect(Collectors.joining("\n\n---\n\n"));

        String systemPrompt = "Ты эксперт по спорту, питанию и здоровому образу жизни. " +
                "КРИТИЧЕСКИ ВАЖНО: Ты отвечаешь ТОЛЬКО на основе предоставленного контекста из документов. " +
                "СТРОГИЕ ПРАВИЛА: " +
                "1. Используй ТОЛЬКО информацию из предоставленного контекста. " +
                "2. НЕЛЬЗЯ использовать знания из своей головы или добавлять информацию которой нет в контексте. " +
                "3. Если в контексте нет информации для ответа на вопрос, ОБЯЗАТЕЛЬНО скажи: \"В предоставленных документах нет информации по этому вопросу.\" " +
                "4. НЕ придумывай ответы, если информации нет в контексте. " +
                "5. Не пиши о том, чего пользователь не просил, отвечай только на конкретный вопрос. " +
                "6. Отвечай ТОЛЬКО текстом, НЕ генерируй таблицы и прочее. " +
                "7. Если контекст пуст или содержит только разделители, скажи что информации нет в документах.";

        String userMessage;
        if (context.isEmpty()) {
            userMessage = "В предоставленных документах нет релевантной информации.\n\nВопрос пользователя: " + question;
        } else {
            userMessage = "КОНТЕКСТ ИЗ ДОКУМЕНТОВ (используй ТОЛЬКО эту информацию):\n\n" + context + 
                    "\n\n---\n\nВОПРОС ПОЛЬЗОВАТЕЛЯ: " + question;
        }

        List<ChatMessage> messages = chatHistoryService.buildMessagesWithHistory(userId, systemPrompt, userMessage);
        String response = chatModel.generate(messages).content().text();
        return markdownFormatter.markdownToHtml(response);
    }
}