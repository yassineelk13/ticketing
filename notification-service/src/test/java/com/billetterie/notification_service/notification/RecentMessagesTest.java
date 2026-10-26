package com.billetterie.notification_service.notification;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RecentMessagesTest {

    @Test
    void aKeyIsKnownOnlyOnceAdded() {
        RecentMessages recent = new RecentMessages(10);

        assertThat(recent.contains("a")).isFalse();
        recent.add("a");
        assertThat(recent.contains("a")).isTrue();
    }

    @Test
    void whenFull_theOldestKeysAreForgotten() {
        RecentMessages recent = new RecentMessages(2);

        recent.add("a");
        recent.add("b");
        recent.add("c");

        assertThat(recent.contains("a")).isFalse();
        assertThat(recent.contains("b")).isTrue();
        assertThat(recent.contains("c")).isTrue();
    }
}