package RUT.BodyCoachAI.agent;

import RUT.BodyCoachAI.service.ChatHistoryService;
import RUT.BodyCoachAI.service.GigaChatService;
import RUT.BodyCoachAI.service.MarkdownFormatter;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class NutritionPlanAgent {
    
    private final ChatLanguageModel chatModel;
    private final ChatHistoryService chatHistoryService;
    private final MarkdownFormatter markdownFormatter;
    
    public NutritionPlanAgent(GigaChatService gigaChatService, ChatHistoryService chatHistoryService, MarkdownFormatter markdownFormatter) {
        this.chatModel = gigaChatService.getChatLanguageModel();
        this.chatHistoryService = chatHistoryService;
        this.markdownFormatter = markdownFormatter;
    }
    
    public String generateNutritionPlan(String userRequest, String userId) {
        String systemPrompt = "Ты профессиональный диетолог и специалист по спортивному питанию. " +
                "Твоя задача: " +
                " — составлять индивидуальные планы питания. " +
                " — если не указано, предположить цель пользователя (похудение, набор массы, поддержание формы). " +
                " — если не указано, предположить уровень активности. " +
                " — если указано, медицинские ограничения. " +
                "Создавай индивидуальные планы питания на основе целей и данных пользователя. " +
                "Планы должны быть сбалансированными и учитывать КБЖУ. " +
                "Дай краткие рекомендации. " +
                "Отвечай ТОЛЬКО текстом, НЕ генерируй таблицы и прочее.";

        List<ChatMessage> messages = chatHistoryService.buildMessagesWithHistory(userId, systemPrompt, userRequest);
        String response = chatModel.generate(messages).content().text();
        return markdownFormatter.markdownToHtml(response);
    }
}