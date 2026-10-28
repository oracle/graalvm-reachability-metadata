/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mybatis.mybatis;

import org.apache.ibatis.ognl.Ognl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DefaultClassResolverTest {
    @Test
    void resolvesQualifiedAndJavaLangClassNames() throws Exception {
        Object root = new Object();
        assertEquals(42, Ognl.getValue("@Integer@parseInt('42')", root));
        assertEquals("Ada", Ognl.getValue("new java.lang.String('Ada')", root));
    }
}
