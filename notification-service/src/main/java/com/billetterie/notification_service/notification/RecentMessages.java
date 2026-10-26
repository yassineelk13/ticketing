package com.billetterie.notification_service.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Component
public class RecentMessages {

    private final Set<String> handled;

    public RecentMessages(@Value("${app.notification.dedupe-size:10000}") int maxSize) {
        this.handled = Collections.synchronizedSet(Collections.newSetFromMap(
                new LinkedHashMap<String, Boolean>() {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                        return size() > maxSize;
                    }
                }));
    }

    public boolean contains(String key) {
        return handled.contains(key);
    }

    public void add(String key) {
        handled.add(key);
    }
}