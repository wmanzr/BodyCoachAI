package RUT.BodyCoachAI.service;

import RUT.BodyCoachAI.model.InBodyData;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class InBodyStateService {

    private final Map<String, InBodyData> lastInBodyByUser = new ConcurrentHashMap<>();

    public void setLastInBodyData(String userId, InBodyData data) {
        if (userId == null || userId.isBlank() || data == null) return;
        lastInBodyByUser.put(userId, data);
    }

    public InBodyData getLastInBodyData(String userId) {
        if (userId == null || userId.isBlank()) return null;
        return lastInBodyByUser.get(userId);
    }

    public boolean hasLastInBodyData(String userId) {
        return getLastInBodyData(userId) != null;
    }
}

