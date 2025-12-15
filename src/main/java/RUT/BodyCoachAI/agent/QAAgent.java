package RUT.BodyCoachAI.agent;

import RUT.BodyCoachAI.service.ChatHistoryService;
import RUT.BodyCoachAI.service.GigaChatService;
import RUT.BodyCoachAI.service.MarkdownFormatter;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class QAAgent {

    private final ChatLanguageModel chatModel;
    private final ChatHistoryService chatHistoryService;
    private final MarkdownFormatter markdownFormatter;

    public QAAgent(GigaChatService gigaChatService, ChatHistoryService chatHistoryService, MarkdownFormatter markdownFormatter) {
        this.chatModel = gigaChatService.getChatLanguageModel();
        this.chatHistoryService = chatHistoryService;
        this.markdownFormatter = markdownFormatter;
    }

    public String answerQuestion(String question, String userId) {
        String systemPrompt = "Ты эксперт по спорту, питанию и здоровому образу жизни. " +
                "Ты можешь отвечать на вопросы используя свои знания и опыт. " +
                "Отвечай подробно и полезно на вопросы пользователя. " +
                "Отвечай ТОЛЬКО текстом, НЕ генерируй таблицы и прочее.";

        List<ChatMessage> messages = chatHistoryService.buildMessagesWithHistory(userId, systemPrompt, question);
        String response = chatModel.generate(messages).content().text();
        return markdownFormatter.markdownToHtml(response);
    }
}