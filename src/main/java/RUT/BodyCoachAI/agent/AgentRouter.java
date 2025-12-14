package RUT.BodyCoachAI.agent;

import RUT.BodyCoachAI.service.ChatHistoryService;
import RUT.BodyCoachAI.service.GigaChatService;
import RUT.BodyCoachAI.service.InBodyStateService;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AgentRouter {
    
    private static final Logger log = LoggerFactory.getLogger(AgentRouter.class);
    
    private final TrainingPlanAgent trainingPlanAgent;
    private final NutritionPlanAgent nutritionPlanAgent;
    private final QAAgent QAAgent;
    private final DataAgent dataAgent;
    private final ChatLanguageModel chatModel;
    private final ChatHistoryService chatHistoryService;
    private final InBodyStateService inBodyStateService;
    
    public AgentRouter(
            TrainingPlanAgent trainingPlanAgent,
            NutritionPlanAgent nutritionPlanAgent,
            QAAgent QAAgent,
            DataAgent dataAgent,
            GigaChatService gigaChatService,
            ChatHistoryService chatHistoryService,
            InBodyStateService inBodyStateService) {
        this.trainingPlanAgent = trainingPlanAgent;
        this.nutritionPlanAgent = nutritionPlanAgent;
        this.QAAgent = QAAgent;
        this.dataAgent = dataAgent;
        this.chatModel = gigaChatService.getChatLanguageModel();
        this.chatHistoryService = chatHistoryService;
        this.inBodyStateService = inBodyStateService;
    }

    public AgentResponse route(String userRequest, String image, String userId) {
        String systemPrompt = "Ты выступаешь в роли интеллектуального роутера запросов пользователя. " +
                "Твоя задача — определить ТОЛЬКО тип запроса и вернуть ОДНО ключевое слово без пояснений.\n\n" +
                "Возможные типы запросов:\n" +
                "1. 'training' — ТОЛЬКО если пользователь ЯВНО просит СОСТАВИТЬ или СГЕНЕРИРОВАТЬ именно план тренировок, " +
                "расписание тренировок, программу упражнений или тренировочный план.\n" +
                "2. 'nutrition' — ТОЛЬКО если пользователь ЯВНО просит СОСТАВИТЬ именно план питания, рацион, меню или диету.\n" +
                "3. 'data' — если запрос связан с обработкой, анализом, загрузкой, проверкой или отображением данных InBody, " +
                "также если пользователь загрузил изображения с отчетами InBody и если пользователь просить excel таблицу или просит рассчитать КБЖУ. \n" +
                "4. 'qa' — ЛЮБЫЕ вопросы, объяснения или консультации. " +
                "Если пользователь задаёт вопросы о тренировках, питании или здоровье, " +
                "но НЕ просит составить план, такой запрос ВСЕГДА относится к 'qa'.\n\n" +
                "ВАЖНО:\n" +
                "- Если запрос выглядит как вопрос (почему, как, что, можно ли, стоит ли) — это 'qa'.\n" +
                "- Если пользователь просто интересуется тренировками или питанием без запроса на план — это 'qa'.\n" +
                "- Тип 'training' и 'nutrition' выбирай ТОЛЬКО при прямой просьбе составить план.\n\n" +
                "Запрос пользователя: \"" + userRequest + "\"\n\n" +
                "Верни строго одно слово: training, nutrition, data или qa.";

        String routingUserRequest = userRequest;
        if (image != null && !image.isBlank()) {
            routingUserRequest = (userRequest == null ? "" : userRequest);
            routingUserRequest = routingUserRequest + "\n\nПользователь загрузил изображение отчёта InBody.";
        }
        if (inBodyStateService.hasLastInBodyData(userId)) {
            routingUserRequest = (routingUserRequest == null ? "" : routingUserRequest);
            routingUserRequest = routingUserRequest + "\n\nВАЖНО: у пользователя уже есть сохранённые данные отчёта InBody (из предыдущего изображения).";
        }

        List<ChatMessage> messages = chatHistoryService.buildMessagesWithHistory(userId, systemPrompt, routingUserRequest);
        String agentType = chatModel.generate(messages).content().text();
        String normalizedType = agentType != null ? agentType.toLowerCase().trim() : "qa";
        
        AgentResponse response;
        if (normalizedType.contains("training")) {
            log.info("Выбран агент: TrainingPlanAgent для запроса: \"{}\"", userRequest);
            response = new AgentResponse(
                trainingPlanAgent.generateTrainingPlan(userRequest, userId),
                "TrainingPlanAgent"
            );
        } else if (normalizedType.contains("nutrition")) {
            log.info("Выбран агент: NutritionPlanAgent для запроса: \"{}\"", userRequest);
            response = new AgentResponse(
                nutritionPlanAgent.generateNutritionPlan(userRequest, userId),
                "NutritionPlanAgent"
            );
        } else if (normalizedType.contains("data")) {
            log.info("Выбран агент: DataAgent для запроса: \"{}\"", userRequest);
            response = new AgentResponse(
                dataAgent.handleDataTools(userRequest, userId, image),
                "DataAgent"
            );
        } else {
            log.info("Выбран агент: RagQaAgent для запроса: \"{}\"", userRequest);
            response = new AgentResponse(
                QAAgent.answerQuestion(userRequest, userId),
                "RagQaAgent"
            );
        }
        return response;
    }
    public static class AgentResponse {
        private final String response;
        private final String agent;
        
        public AgentResponse(String response, String agent) {
            this.response = response;
            this.agent = agent;
        }
        
        public String getResponse() {
            return response;
        }
        
        public String getAgent() {
            return agent;
        }
    }
}