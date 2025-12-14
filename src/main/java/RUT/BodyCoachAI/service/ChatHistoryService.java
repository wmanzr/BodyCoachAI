package RUT.BodyCoachAI.service;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.AiMessage;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ChatHistoryService {
    
    private final Map<String, List<ChatMessage>> chatHistories = new ConcurrentHashMap<>();
    
    public List<ChatMessage> getHistory(String userId) {
        return chatHistories.getOrDefault(userId, new ArrayList<>());
    }
    
    public void addUserMessage(String userId, String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        List<ChatMessage> history = chatHistories.computeIfAbsent(userId, k -> new ArrayList<>());
        history.add(UserMessage.from(content));
    }
    
    public void addAiMessage(String userId, String content) {
        List<ChatMessage> history = chatHistories.computeIfAbsent(userId, k -> new ArrayList<>());
        history.add(AiMessage.from(content));
    }
    
    public void setSystemMessage(String userId, String systemPrompt) {
        List<ChatMessage> history = chatHistories.computeIfAbsent(userId, k -> new ArrayList<>());
        if (history.isEmpty() || !(history.get(0) instanceof SystemMessage)) {
            history.add(0, SystemMessage.from(systemPrompt));
        } else {
            history.set(0, SystemMessage.from(systemPrompt));
        }
    }
    
    public List<ChatMessage> buildMessagesWithHistory(String userId, String systemPrompt, String userMessage) {
        List<ChatMessage> messages = new ArrayList<>();
        List<ChatMessage> history = getHistory(userId);
        
        if (history.isEmpty() || !(history.get(0) instanceof SystemMessage)) {
            messages.add(SystemMessage.from(systemPrompt));
        }
        
        messages.addAll(history);
        messages.add(UserMessage.from(userMessage));
        
        return messages;
    }
    
    public void clearHistory(String userId) {
        chatHistories.remove(userId);
    }
}
