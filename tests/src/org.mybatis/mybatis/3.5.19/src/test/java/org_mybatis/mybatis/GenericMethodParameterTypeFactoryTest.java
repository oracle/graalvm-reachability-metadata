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

public class GenericMethodParameterTypeFactoryTest {
    @Test
    void resolvesGenericArrayParametersForInheritedMethod() {
        Strings target = new Strings();
        assertEquals(2, OgnlCache.getValue("count(new java.lang.String[] {'Ada', 'Grace'})", target));
    }

    public static class Values<T> {
        public int count(T[] values) {
            return values.length;
        }
    }

    public static class Strings extends Values<String> {
    }
}
