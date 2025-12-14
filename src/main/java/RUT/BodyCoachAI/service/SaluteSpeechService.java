package RUT.BodyCoachAI.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SaluteSpeechService {
    private final String authKey;
    private final RestTemplate restTemplate;
    private static final String STT_URL = "https://smartspeech.sber.ru/rest/v1/speech:recognize";
    private static final String OAUTH_URL = "https://ngw.devices.sberbank.ru:9443/api/v2/oauth";

    private String accessToken;
    private long tokenExpiresAt = 0;
    
    public SaluteSpeechService(@Value("${salute.auth}") String authKey) {
        this.authKey = authKey;
        this.restTemplate = new RestTemplate();
    }

    public void warmUp() {
        ensureToken();
    }

    public String speechToText(MultipartFile audioFile) {
        try {
            ensureToken();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("audio/x-pcm;bit=16;rate=16000"));
            headers.setBearerAuth(accessToken);
            
            byte[] audioBytes = audioFile.getBytes();
            HttpEntity<byte[]> request = new HttpEntity<>(audioBytes, headers);
            
            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> response = (ResponseEntity<Map<String, Object>>) (ResponseEntity<?>) restTemplate.exchange(
                    STT_URL,
                    HttpMethod.POST,
                    request,
                    Map.class
            );

            if (response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                Object raw = body.get("result");
                if (raw instanceof String s) {
                    return s;
                }
                if (raw instanceof List<?> list && !list.isEmpty()) {
                    Object first = list.get(0);
                    if (first != null) {
                        return first.toString();
                    }
                }
                return "";
            }
            
            return "";
        } catch (Exception e) {
            throw new RuntimeException("Ошибка при распознавании речи", e);
        }
    }

    private void ensureToken() {
        if (System.currentTimeMillis() < tokenExpiresAt && accessToken != null && !accessToken.isBlank()) {
            return;
        }

        if (authKey == null || authKey.isBlank()) {
            throw new IllegalStateException("SaluteSpeech authKey is blank (salute.auth)");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(authKey);
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.add("RqUID", UUID.randomUUID().toString());

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("scope", "SALUTE_SPEECH_PERS");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(form, headers);
        @SuppressWarnings("unchecked")
        ResponseEntity<Map<String, Object>> response = (ResponseEntity<Map<String, Object>>) (ResponseEntity<?>) restTemplate.exchange(
                OAUTH_URL,
                HttpMethod.POST,
                request,
                Map.class
        );

        if (response.getBody() == null || response.getBody().get("access_token") == null) {
            throw new IllegalStateException("Не удалось получить access_token SaluteSpeech");
        }

        this.accessToken = response.getBody().get("access_token").toString();
        this.tokenExpiresAt = System.currentTimeMillis() + (25 * 60 * 1000);
    }
}