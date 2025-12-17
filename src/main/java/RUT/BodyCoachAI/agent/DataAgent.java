package RUT.BodyCoachAI.agent;

import RUT.BodyCoachAI.agent.tools.DataBodyTools;
import RUT.BodyCoachAI.model.InBodyData;
import RUT.BodyCoachAI.service.GigaChatService;
import RUT.BodyCoachAI.service.InBodyStateService;
import RUT.BodyCoachAI.service.MarkdownFormatter;
import com.google.gson.Gson;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class DataAgent {
    private static final Logger log = LoggerFactory.getLogger(DataAgent.class);

    private final DataBodyTools tools;
    private final GigaChatService gigaChatService;
    private final InBodyStateService inBodyStateService;
    private final ChatLanguageModel chatModel;
    private final Gson gson;
    private final MarkdownFormatter markdownFormatter;

    public DataAgent(DataBodyTools tools, GigaChatService gigaChatService, InBodyStateService inBodyStateService, MarkdownFormatter markdownFormatter) {
        this.tools = tools;
        this.gigaChatService = gigaChatService;
        this.inBodyStateService = inBodyStateService;
        this.chatModel = gigaChatService.getChatLanguageModel();
        this.gson = new Gson();
        this.markdownFormatter = markdownFormatter;
    }

    public String handleDataTools(String userRequest, String userId, String base64Image) {
        String safeRequest = userRequest == null ? "" : userRequest.trim();
        if (safeRequest.isBlank()) {
            safeRequest = "Проанализируй отчёт InBody и дай краткие выводы и рекомендации.";
        }

        long t0 = System.currentTimeMillis();
        boolean hasImage = base64Image != null && !base64Image.isBlank();
        log.info("Начат выбор инструментов для userId={}, с длиной запроса ={}, изображение ={}, длина изображения={}", userId, safeRequest.length(), hasImage,
                base64Image == null ? 0 : base64Image.length());

        InBodyData data;
        if (hasImage) {
            data = extractInBodyDataFromImage(base64Image);
            if (data != null) {
                inBodyStateService.setLastInBodyData(userId, data);
            }
            log.info("Данные из изображения структурированы в InBodyData Model {}мс", System.currentTimeMillis() - t0);
        } else {
            data = inBodyStateService.getLastInBodyData(userId);
        }

        ActionDecision decision = decideActionWithModel(safeRequest, data);

        String action = decision != null && decision.action != null ? decision.action.toLowerCase(Locale.ROOT).trim() : "text";

        if ("excel".equals(action)) {
            if (data == null) {
                return "Чтобы сделать Excel-таблицу, нужно загрузить фото отчёта InBody.";
            }
            String fileName = tools.createTableByDataForUser(data);
            log.info("handleDataTools done in {}ms (excel)", System.currentTimeMillis() - t0);
            return "EXCEL_FILE:" + fileName;
        }

        if ("kbju".equals(action)) {
            if (data == null) {
                return "Чтобы рассчитать КБЖУ, нужно загрузить фото отчёта InBody.";
            }
            Map<String, Double> stats = tools.calculateBodyStats(data);
            Double calories = stats.get("calories");
            Double protein = stats.get("protein");
            Double fat = stats.get("fat");
            Double carbs = stats.get("carbs");
            if (calories == null) {
                return "Не удалось рассчитать КБЖУ: в отчёте не найден BMR.";
            }
            log.info("handleDataTools done in {}ms (kbju)", System.currentTimeMillis() - t0);
            return "<b>Рекомендация по КБЖУ (в день)</b><br>" +
                    "<ul>" +
                    "<li>Калории: " + Math.round(calories) + " ккал</li>" +
                    "<li>Белки: " + String.format(Locale.ROOT, "%.1f", protein) + " г</li>" +
                    "<li>Жиры: " + String.format(Locale.ROOT, "%.1f", fat) + " г</li>" +
                    "<li>Углеводы: " + String.format(Locale.ROOT, "%.1f", carbs) + " г</li>" +
                    "</ul>";
        }
        
        String html = decision != null ? decision.replyHtml : null;
        html = html != null ? html.trim() : null;
        if (html == null || html.isBlank()) {
            return "Не удалось сформировать ответ. Попробуйте ещё раз.";
        }
        log.info("handleDataTools done in {}ms (text)", System.currentTimeMillis() - t0);
        return markdownFormatter.markdownToHtml(html);
    }

    private InBodyData extractInBodyDataFromImage(String base64Image) {
        String prompt = """
                На изображении отчёт InBody.
                Извлеки значения и верни ТОЛЬКО валидный JSON (без пояснений, без markdown, без текста вокруг).
                Верни только те поля, которые есть в моей модели InBodyData.
                Если поле не найдено — ставь null.
                Числа возвращай как number (не строки), десятичный разделитель — точка.

                Строгая схема JSON:
                {
                  "age": number|null,
                  "height": number|null,
                  "gender": "male"|"female"|null,
                  "weight": number|null,
                  "muscleMass": number|null,
                  "fatMass": number|null,
                  "bodyFatPercentage": number|null,
                  "bmi": number|null,
                  "visceralFatLevel": number|null,
                  "bmr": number|null,
                  "inBodyScore": number|null
                }
                """;

        try {
            String raw = gigaChatService.analyzeImage(base64Image, prompt);
            String json = extractJson(raw);
            InBodyData parsed = gson.fromJson(json, InBodyData.class);
            return parsed != null ? parsed : new InBodyData();
        } catch (Exception e) {
            log.error("Обработка изображения закончилась ошибкой", e);
            return new InBodyData();
        }
    }

    private String extractJson(String text) {
        if (text == null) return "{}";
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return "{}";
    }

    private ActionDecision decideActionWithModel(String userRequest, InBodyData data) {
        String systemPrompt = """
                Ты DataAgent — помощник по анализу отчётов InBody.
                Твоя задача: определить, нужен ли инструмент, и вернуть ТОЛЬКО JSON (без лишнего текста).

                Доступные действия (поле action):
                - "excel"  — если пользователь просит Excel/таблицу/файл со статистикой.
                - "kbju"   — если пользователь просит расчёт КБЖУ (калории/белки/жиры/углеводы).
                - "text"   — в любом другом случае (например, просто текстовые рекомендации/объяснение показателей).

                Если action="text", обязательно заполни поле replyHtml с готовым ответом.
                Если action="excel" или "kbju", replyHtml можно оставить пустым/ null.

                Формат JSON:
                {
                  "action": "excel|kbju|text",
                  "replyHtml": "string|null"
                }
                """;

        String userMsg = "Запрос пользователя: " + userRequest +
                "\n\nДанные InBody:\n" + (data != null ? data.toPromptString() : "нет (изображение не загружено)");

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(systemPrompt));
        messages.add(UserMessage.from(userMsg));

        try {
            String raw = chatModel.generate(messages).content().text();
            String json = extractJson(raw);
            return gson.fromJson(json, ActionDecision.class);
        } catch (Exception e) {
            log.error("decideActionWithModel failed", e);
            ActionDecision fallback = new ActionDecision();
            fallback.action = "text";
            fallback.replyHtml = "Не удалось определить действие. Попробуйте переформулировать запрос.";
            return fallback;
        }
    }

    private static class ActionDecision {
        String action;
        String replyHtml;
    }
}