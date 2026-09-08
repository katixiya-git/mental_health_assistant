package com.ai.aiproject.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenBlacklistTest {

    @Test
    void addThenContainsReturnsTrue() {
        TokenBlacklist blacklist = new TokenBlacklist();
        blacklist.add("t1", System.currentTimeMillis() + 60_000);
        assertTrue(blacklist.contains("t1"));
    }

    @Test
    void unknownTokenReturnsFalse() {
        TokenBlacklist blacklist = new TokenBlacklist();
        assertFalse(blacklist.contains("not-added"));
    }

    @Test
    void expiredEntryEvictedAndReturnsFalse() {
        TokenBlacklist blacklist = new TokenBlacklist();
        blacklist.add("expired", System.currentTimeMillis() - 1);
        assertFalse(blacklist.contains("expired"));
    }
}
