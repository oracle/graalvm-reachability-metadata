/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_collections4;

import org.apache.commons.collections4.Trie;
import org.apache.commons.collections4.trie.PatriciaTrie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the Patricia trie subsystem for routing request paths to endpoint handlers.
 */
public class ApiRouteTrieTest {
    @Test
    @Timeout(60)
    public void findsHandlersForAnApiPathPrefix() {
        Trie<String, String> routes = new PatriciaTrie<>();
        routes.put("/api/users", "user-service");
        routes.put("/api/users/profile", "profile-service");
        routes.put("/api/orders", "order-service");
        routes.put("/assets/logo.svg", "asset-service");

        Map<String, String> userRoutes = routes.prefixMap("/api/users");

        assertEquals(2, userRoutes.size());
        assertEquals("user-service", userRoutes.get("/api/users"));
        assertEquals("profile-service", userRoutes.get("/api/users/profile"));
        assertTrue(userRoutes.keySet().containsAll(Set.of("/api/users", "/api/users/profile")));
        assertFalse(userRoutes.containsKey("/api/orders"));
        assertEquals(4, routes.size());
    }
}
