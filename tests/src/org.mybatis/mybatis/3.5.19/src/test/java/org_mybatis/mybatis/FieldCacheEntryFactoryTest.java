/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mybatis.mybatis;

import org.apache.ibatis.scripting.xmltags.OgnlCache;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FieldCacheEntryFactoryTest {
    @Test
    void readsInheritedFieldsInExpressions() {
        assertEquals("Ada", OgnlCache.getValue("name", new User()));
    }

    public static class Named {
        public String name = "Ada";
    }

    public static class User extends Named {
    }
}
