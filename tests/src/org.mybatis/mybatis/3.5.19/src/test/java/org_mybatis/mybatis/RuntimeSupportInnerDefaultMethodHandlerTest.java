/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mybatis.mybatis;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.apache.ibatis.javassist.util.proxy.MethodHandler;
import org.apache.ibatis.javassist.util.proxy.RuntimeSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RuntimeSupportInnerDefaultMethodHandlerTest {
    @Test
    void proceedsWithTheOriginalMethodAndPreservesItsStateChanges() throws Throwable {
        Journal journal = new Journal();
        Method append = Journal.class.getMethod("append", String.class);
        MethodHandler handler = RuntimeSupport.default_interceptor;
        assertEquals(1, handler.invoke(journal, append, append, new Object[]{"Ada"}));
        assertEquals(2, handler.invoke(journal, append, append, new Object[]{"Grace"}));
        assertEquals(List.of("Ada", "Grace"), journal.entries);
    }

    public static class Journal {
        private final List<String> entries = new ArrayList<>();

        public int append(String entry) {
            entries.add(entry);
            return entries.size();
        }
    }
}
