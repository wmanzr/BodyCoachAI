package RUT.BodyCoachAI.agent;

import RUT.BodyCoachAI.service.GigaChatService;
import RUT.BodyCoachAI.service.InBodyStateService;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
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
    private final QAAgent qaAgent;
    private final RagQaAgent ragQaAgent;
    private final DataAgent dataAgent;
    private final ChatLanguageModel chatModel;
    private final InBodyStateService inBodyStateService;
    
    public AgentRouter(
            TrainingPlanAgent trainingPlanAgent,
            NutritionPlanAgent nutritionPlanAgent,
            QAAgent qaAgent,
            RagQaAgent ragQaAgent,
            DataAgent dataAgent,
            GigaChatService gigaChatService,
            InBodyStateService inBodyStateService) {
        this.trainingPlanAgent = trainingPlanAgent;
        this.nutritionPlanAgent = nutritionPlanAgent;
        this.qaAgent = qaAgent;
        this.ragQaAgent = ragQaAgent;
        this.dataAgent = dataAgent;
        this.chatModel = gigaChatService.getChatLanguageModel();
        this.inBodyStateService = inBodyStateService;
    }

    public AgentResponse route(String userRequest, String image, String userId, boolean ragEnabled) {
        String systemPrompt = "Ты выступаешь в роли интеллектуального роутера запросов пользователя. " +
                "Твоя задача — определить ТОЛЬКО тип запроса и вернуть ОДНО ключевое слово без пояснений.\n\n" +
                "Возможные типы запросов:\n" +
                "1. 'training' — ТОЛЬКО если пользователь ЯВНО просит СОСТАВИТЬ или СГЕНЕРИРОВАТЬ именно план тренировок, " +
                "расписание тренировок, программу упражнений или тренировочный план.\n" +
                "   НЕ 'training': вопросы о влиянии упражнений (\"как подтягивания влияют?\"), вопросы о технике, общие вопросы о тренировках.\n" +
                "2. 'nutrition' — ТОЛЬКО если пользователь ЯВНО просит СОСТАВИТЬ именно план питания, рацион, меню или диету.\n" +
                "   НЕ 'nutrition': вопросы о влиянии продуктов, вопросы о питании, общие вопросы о диетах.\n" +
                "3. 'data' — ТОЛЬКО если запрос ЯВНО связан с РАБОТОЙ С ОТЧЕТОМ InBody: " +
                "загрузка фото отчёта InBody, создание Excel-таблицы из данных InBody, расчёт КБЖУ на основе данных InBody, " +
                "просмотр или отображение конкретных показателей из отчёта InBody. " +
                "НЕ относится к 'data': общие вопросы о здоровье, питании, тренировках, даже если упоминается слово 'анализ' или 'данные'.\n" +
                "4. 'qa' — ЛЮБЫЕ вопросы, объяснения или консультации. " +
                "Если пользователь задаёт вопросы о тренировках, питании или здоровье, " +
                "но НЕ просит составить план, такой запрос ВСЕГДА относится к 'qa'.\n" +
                "   Примеры 'qa': \"как подтягивания влияют на организм?\", \"что такое бег?\", \"почему важно питание?\", " +
                "\"как упражнения влияют на мозг?\", \"можно ли заниматься спортом каждый день?\"\n\n" +
                "ВАЖНО:\n" +
                "- Если запрос выглядит как вопрос (почему, как, что, можно ли, стоит ли, влияет ли) — это ВСЕГДА 'qa'.\n" +
                "- Если пользователь спрашивает о влиянии упражнений/питания на организм — это 'qa', НЕ 'training' или 'nutrition'.\n" +
                "- Если пользователь просто интересуется тренировками или питанием без запроса на план — это 'qa'.\n" +
                "- Тип 'training' и 'nutrition' выбирай ТОЛЬКО при прямой просьбе составить/создать/сгенерировать план.\n\n" +
                (ragEnabled ? 
                    "ВАЖНО: RAG режим включен. Для типа 'qa' будет использован QA RAG Agent, который берет информацию ТОЛЬКО из контекста загруженных документов.\n\n" :
                    "ВАЖНО: RAG режим выключен. Для типа 'qa' будет использован QA Agent, который может отвечать используя свои знания.\n\n") +
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

        List<ChatMessage> messages = List.of(
                SystemMessage.from(systemPrompt),
                UserMessage.from(routingUserRequest)
        );
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
            if (ragEnabled) {
                log.info("Выбран агент: RagQaAgent (RAG включен) для запроса: \"{}\"", userRequest);
                response = new AgentResponse(
                    ragQaAgent.answerQuestion(userRequest, userId),
                    "RagQaAgent"
                );
            } else {
                log.info("Выбран агент: QAAgent (RAG выключен) для запроса: \"{}\"", userRequest);
                response = new AgentResponse(
                    qaAgent.answerQuestion(userRequest, userId),
                    "QAAgent"
                );
            }
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