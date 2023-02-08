/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package org.apache.commons.dbcp2.datasources;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class TestPoolKey {

    private PoolKey poolKey;
    private PoolKey anotherPoolKey;

    @BeforeEach
    public void setUp() {
        poolKey = new PoolKey("ds", "user");
        anotherPoolKey = new PoolKey(null, null);
    }

    @Test
    public void testEquals() {
        assertEquals(poolKey, poolKey);
        assertNotEquals(poolKey, null);
        assertNotEquals(poolKey, new Object());
        assertNotEquals(new PoolKey(null, "user"), poolKey);
        assertEquals(new PoolKey(null, "user"), new PoolKey(null, "user"));
        assertNotEquals(new PoolKey(null, "user"), new PoolKey(null, "foo"));
        assertNotEquals(new PoolKey("ds", null), new PoolKey("foo", null));
        assertNotEquals(new PoolKey("ds", null), poolKey);
        assertEquals(new PoolKey("ds", null), new PoolKey("ds", null));
    }

    @Test
    public void testHashcode() {
        assertEquals(poolKey.hashCode(), new PoolKey("ds", "user").hashCode());
        assertNotEquals(poolKey.hashCode(), anotherPoolKey.hashCode());
    }

    @Test
    public void testToString() {
        assertEquals(poolKey.toString(), new PoolKey("ds", "user").toString());
        assertNotEquals(poolKey.toString(), anotherPoolKey.toString());
    }
}
