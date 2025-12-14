package RUT.BodyCoachAI.agent;

import RUT.BodyCoachAI.service.ChatHistoryService;
import RUT.BodyCoachAI.service.GigaChatService;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TrainingPlanAgent {
    
    private final ChatLanguageModel chatModel;
    private final ChatHistoryService chatHistoryService;
    
    public TrainingPlanAgent(GigaChatService gigaChatService, ChatHistoryService chatHistoryService) {
        this.chatModel = gigaChatService.getChatLanguageModel();
        this.chatHistoryService = chatHistoryService;
    }
    
    public String generateTrainingPlan(String userRequest, String userId) {
        String systemPrompt = "Ты профессиональный тренер по фитнесу. " +
                "Твоя задача: " +
                " — составлять безопасные и эффективные планы тренировок. " +
                " — если не указано, предположить цель пользователя (сила, масса, выносливость, похудение). " +
                " — если не указано, предположить уровень подготовки (новичок / средний / продвинутый). " +
                "Создавай индивидуальные планы тренировок на основе данных пользователя. " +
                "Планы должны быть структурированными, безопасными и эффективными." +
                "Дай рекомендации по восстановлению и технике безопасности." +
                "Для форматирования используй ТОЛЬКО HTML теги. Не используй Markdown символы.";

        List<ChatMessage> messages = chatHistoryService.buildMessagesWithHistory(userId, systemPrompt, userRequest);
        return chatModel.generate(messages).content().text();
    }
}