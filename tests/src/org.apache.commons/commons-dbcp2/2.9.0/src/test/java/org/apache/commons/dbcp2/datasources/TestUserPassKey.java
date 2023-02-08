/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package org.apache.commons.dbcp2.datasources;

import org.apache.commons.dbcp2.Utils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class TestUserPassKey {

    private UserPassKey userPassKey;
    private UserPassKey anotherUserPassKey;

    @BeforeEach
    public void setUp() {
        userPassKey = new UserPassKey("user", "pass");
        anotherUserPassKey = new UserPassKey((String) null, "");
    }

    @Test
    public void testEquals() {
        assertEquals(new UserPassKey("user"), new UserPassKey("user", (char[]) null));
        assertEquals(userPassKey, userPassKey);
        assertNotEquals(userPassKey, null);
        assertNotEquals(userPassKey, new Object());
        assertNotEquals(new UserPassKey(null), userPassKey);
        assertEquals(new UserPassKey(null), new UserPassKey(null));
        assertNotEquals(new UserPassKey("user", "pass"), new UserPassKey("foo", "pass"));
    }

    @Test
    public void testGettersAndSetters() {
        assertEquals("user", userPassKey.getUserName());
        assertEquals("pass", userPassKey.getPassword());
        assertArrayEquals(Utils.toCharArray("pass"), userPassKey.getPasswordCharArray());
    }

    @Test
    public void testHashcode() {
        assertEquals(userPassKey.hashCode(), new UserPassKey("user", "pass").hashCode());
        assertNotEquals(userPassKey.hashCode(), anotherUserPassKey.hashCode());
    }

    @Test
    public void testToString() {
        assertEquals(userPassKey.toString(), new UserPassKey("user", "pass").toString());
        assertNotEquals(userPassKey.toString(), anotherUserPassKey.toString());
    }
}
