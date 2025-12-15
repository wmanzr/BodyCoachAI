package RUT.BodyCoachAI.controller;

import RUT.BodyCoachAI.agent.AgentRouter;
import RUT.BodyCoachAI.service.ChatHistoryService;
import RUT.BodyCoachAI.service.GigaChatService;
import RUT.BodyCoachAI.service.RagService;
import RUT.BodyCoachAI.service.SaluteSpeechService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Controller
public class ChatController {
    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private final AgentRouter agentRouter;
    private final GigaChatService gigaChatService;
    private final SaluteSpeechService speechService;
    private final ChatHistoryService chatHistoryService;
    private final RagService ragService;
    
    public ChatController(AgentRouter agentRouter, GigaChatService gigaChatService,
                         SaluteSpeechService speechService, ChatHistoryService chatHistoryService,
                         RagService ragService) {
        this.agentRouter = agentRouter;
        this.gigaChatService = gigaChatService;
        this.speechService = speechService;
        this.chatHistoryService = chatHistoryService;
        this.ragService = ragService;
    }
    
    @GetMapping("/")
    public String index() {
        return "index";
    }
    
    @PostMapping("/api/init")
    public ResponseEntity<Map<String, Object>> initialize() {
        Map<String, Object> response = new HashMap<>();
        
        String gigachatKey = System.getProperty("GIGACHAT_KEY");
        if (gigachatKey == null || gigachatKey.isEmpty()) {
            response.put("status", "error");
            response.put("message", "Ключ GigaChat не найден");
            return ResponseEntity.ok(response);
        }

        String saluteKey = System.getProperty("SALUTE_SPEECH_KEY");
        if (saluteKey == null || saluteKey.isEmpty()) {
            response.put("status", "error");
            response.put("message", "Ключ SaluteSpeech не найден");
            return ResponseEntity.ok(response);
        }

        try {
            gigaChatService.warmUp();
            speechService.warmUp();
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Ошибка инициализации токенов: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
        
        response.put("status", "success");
        response.put("message", "Система инициализирована (токены получены)");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/api/chat")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> chat(@RequestBody ChatRequest request, HttpSession session) {
        String userId = getOrCreateUserId(session);

        String text = request.getText() == null ? "" : request.getText();
        String image = request.getImage();
        boolean ragEnabled = ragService.isRagEnabled(session);

        AgentRouter.AgentResponse agentResponse = agentRouter.route(text, image, userId, ragEnabled);

        chatHistoryService.addUserMessage(userId, text);
        String responseText = agentResponse.getResponse();

        Map<String, Object> data = null;
        if (responseText != null && responseText.startsWith("EXCEL_FILE:")) {
            String fileName = responseText.substring("EXCEL_FILE:".length());
            data = new HashMap<>();
            data.put("fileName", fileName);
            responseText = null;
        }
        
        chatHistoryService.addAiMessage(userId, responseText);

        Map<String, Object> response = new HashMap<>();
        response.put("response", responseText);
        response.put("agent", agentResponse.getAgent());
        response.put("type", "text");
        response.put("data", data);
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/api/rag/toggle")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> toggleRag(HttpSession session) {
        ragService.toggleRag(session);
        boolean ragEnabled = ragService.isRagEnabled(session);
        
        Map<String, Object> response = new HashMap<>();
        response.put("ragEnabled", ragEnabled);
        response.put("status", "success");
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/api/rag/status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getRagStatus(HttpSession session) {
        boolean ragEnabled = ragService.isRagEnabled(session);
        
        Map<String, Object> response = new HashMap<>();
        response.put("ragEnabled", ragEnabled);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/files/{fileName:.+}")
    @ResponseBody
    public ResponseEntity<FileSystemResource> downloadFile(@PathVariable String fileName) {
        Path dataDir = Path.of("data").toAbsolutePath().normalize();
        Path filePath = dataDir.resolve(fileName).normalize();
        if (!filePath.startsWith(dataDir) || !filePath.toFile().exists()) {
            return ResponseEntity.notFound().build();
        }

        FileSystemResource resource = new FileSystemResource(filePath.toFile());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(resource);
    }

    private String getOrCreateUserId(HttpSession session) {
        String userId = (String) session.getAttribute("userId");
        if (userId == null) {
            userId = UUID.randomUUID().toString();
            session.setAttribute("userId", userId);
        }
        return userId;
    }
    
    @PostMapping("/api/speech")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> speechToText(
            @RequestParam("audio") MultipartFile audioFile) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            String text = speechService.speechToText(audioFile);
            log.info("STT text: {}", text);
            response.put("text", text);
            response.put("status", "success");
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }

    public static class ChatRequest {
        private String text;
        private String image;
        
        public String getText() {
            return text;
        }
        
        public void setText(String text) {
            this.text = text;
        }
        
        public String getImage() {
            return image;
        }
        
        public void setImage(String image) {
            this.image = image;
        }
    }
}
