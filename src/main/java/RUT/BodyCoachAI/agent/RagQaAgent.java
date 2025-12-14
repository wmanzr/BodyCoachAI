package RUT.BodyCoachAI.agent;

import RUT.BodyCoachAI.service.ChatHistoryService;
import RUT.BodyCoachAI.service.GigaChatService;
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

    public RagQaAgent(GigaChatService gigaChatService, RagService ragService, ChatHistoryService chatHistoryService) {
        this.chatModel = gigaChatService.getChatLanguageModel();
        this.contentRetriever = ragService.getContentRetriever();
        this.chatHistoryService = chatHistoryService;
    }

    public String answerQuestion(String question, String userId) {
        Query query = Query.from(question);
        List<Content> relevantContents = contentRetriever.retrieve(query);

        String context = relevantContents.stream()
                .map(Content::textSegment)
                .map(segment -> segment.text())
                .collect(Collectors.joining("\n\n"));

        String systemPrompt = "Ты эксперт по спорту, питанию и здоровому образу жизни. " +
                "Ты отвечаешь ТОЛЬКО на основе предоставленных документов. " +
                "Правила: " +
                " — используй ТОЛЬКО текст из контекста. " +
                " — НЕЛЬЗЯ добавлять знания из головы. " +
                "Не пиши о том, чего пользователь не просил, отвечай только на то, что он написал. " +
                "Если информации недостаточно, скажи об этом честно и не отвечай вне контекста документов. " +
                "Для форматирования используй ТОЛЬКО HTML теги. Не используй Markdown символы.";

        String userMessage = "Контекст из документов:\n" + context + "\n\nВопрос пользователя: " + question;

        List<ChatMessage> messages = chatHistoryService.buildMessagesWithHistory(userId, systemPrompt, userMessage);
        return chatModel.generate(messages).content().text();
    }
}