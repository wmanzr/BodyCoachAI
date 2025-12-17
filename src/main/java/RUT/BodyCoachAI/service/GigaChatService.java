package RUT.BodyCoachAI.service;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
public class GigaChatService implements ChatLanguageModel {
    
    @Value("${gigachat.auth}")
    private String authKey;
    
    private final RestTemplate restTemplate;
    private String accessToken;
    private long tokenExpiresAt = 0;
    
    public GigaChatService() {
        this.restTemplate = new RestTemplate();
        disableSslVerification();
    }

    public void warmUp() {
        ensureToken();
    }
    
    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        ensureToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        StringBuilder messagesJson = new StringBuilder();
        for (ChatMessage msg : messages) {
            String role;
            String content;

            if (msg instanceof SystemMessage) {
                role = "system";
                content = ((SystemMessage) msg).text();
            } else if (msg instanceof UserMessage) {
                role = "user";
                UserMessage um = (UserMessage) msg;
                content = um.hasSingleText() ? um.singleText() : "";
            } else if (msg instanceof dev.langchain4j.data.message.AiMessage) {
                role = "assistant";
                AiMessage aiMsg = (AiMessage) msg;
                content = aiMsg.text();
            } else {
                continue;
            }

            String safeContent = content.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
            messagesJson.append(String.format("{\"role\": \"%s\", \"content\": \"%s\"},", role, safeContent));
        }

        if (messagesJson.length() > 0) {
            messagesJson.setLength(messagesJson.length() - 1);
        }

        String body = String.format("""
                {
                  "model": "GigaChat",
                  "messages": [%s],
                  "temperature": 0.7
                }
                """, messagesJson.toString());

        HttpEntity<String> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<GigaResponse> response = restTemplate.postForEntity(
                    "https://gigachat.devices.sberbank.ru/api/v1/chat/completions",
                    request, GigaResponse.class
            );

            if (response.getBody() != null && !response.getBody().choices.isEmpty()) {
                String content = response.getBody().choices.get(0).message.content;
                return Response.from(AiMessage.from(content != null ? content : ""));
            }
        } catch (Exception e) {
            return Response.from(AiMessage.from("Ошибка: " + e.getMessage()));
        }
        return Response.from(AiMessage.from("GigaChat молчит."));
    }
    
    public String analyzeImage(String base64Image, String prompt) {
        ensureToken();
        try {
            File imageFile = base64ToFile(base64Image);
            String imageId = uploadFile(imageFile);
            
            String safePrompt = prompt.replace("\"", "\\\"").replace("\n", "\\n");
            
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            String body = """
                {
                  "model": "GigaChat-Max",
                  "messages": [
                    {
                      "role": "user",
                      "content": "%s",
                      "attachments": ["%s"]
                    }
                  ],
                  "temperature": 0.5
                }
                """.formatted(safePrompt, imageId);
            
            HttpEntity<String> request = new HttpEntity<>(body, headers);
            
            ResponseEntity<GigaResponse> response = restTemplate.postForEntity(
                    "https://gigachat.devices.sberbank.ru/api/v1/chat/completions",
                    request, GigaResponse.class
            );
            
            imageFile.delete();
            
            if (response.getBody() != null && !response.getBody().choices.isEmpty()) {
                return response.getBody().choices.get(0).message.content;
            }
        } catch (Exception e) {
            return "Ошибка GigaChat Vision: " + e.getMessage();
        }
        return "GigaChat не увидел изображение.";
    }
    
    private File base64ToFile(String base64Image) throws Exception {
        String base64Data = base64Image.contains(",") 
            ? base64Image.split(",")[1] 
            : base64Image;
        
        byte[] imageBytes = Base64.getDecoder().decode(base64Data);
        File tempFile = File.createTempFile("inbody_", ".jpg");
        tempFile.deleteOnExit();
        
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            fos.write(imageBytes);
        }
        
        return tempFile;
    }
    
    private String uploadFile(File file) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new FileSystemResource(file));
        body.add("purpose", "general");
        
        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
        
        ResponseEntity<GigaFileResponse> response = restTemplate.postForEntity(
                "https://gigachat.devices.sberbank.ru/api/v1/files",
                requestEntity,
                GigaFileResponse.class
        );
        
        if (response.getBody() != null) {
            return response.getBody().id;
        }
        throw new RuntimeException("Не удалось загрузить файл");
    }
    
    private void ensureToken() {
        if (System.currentTimeMillis() < tokenExpiresAt) return;
        
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(authKey);
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.add("RqUID", UUID.randomUUID().toString());
        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("scope", "GIGACHAT_API_PERS");
        
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);
        try {
            ResponseEntity<TokenResponse> response = restTemplate.postForEntity(
                    "https://ngw.devices.sberbank.ru:9443/api/v2/oauth",
                    request, TokenResponse.class
            );
            if (response.getBody() != null) {
                this.accessToken = response.getBody().access_token;
                this.tokenExpiresAt = System.currentTimeMillis() + (25 * 60 * 1000);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void disableSslVerification() {
        try {
            javax.net.ssl.SSLContext sslContext = javax.net.ssl.SSLContext.getInstance("TLS");
            sslContext.init(null, new javax.net.ssl.TrustManager[]{
                new javax.net.ssl.X509TrustManager() {
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() { return null; }
                    public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
                }
            }, new java.security.SecureRandom());
            javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
            javax.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public ChatLanguageModel getChatLanguageModel() {
        return this;
    }
    
    static class TokenResponse { public String access_token; }
    static class GigaResponse { public List<Choice> choices; }
    static class Choice { public Message message; }
    static class Message { public String content; }
    static class GigaFileResponse { public String id; }
}