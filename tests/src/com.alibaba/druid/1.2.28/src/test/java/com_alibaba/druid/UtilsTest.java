/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.druid;

import com.alibaba.druid.util.Utils;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class UtilsTest {
    private static final String CONTEXT_CLASS_ALIAS = "com_alibaba.druid.ContextLoadedAlias";
    private static final String FILTER_RESOURCE = "META-INF/druid-filter.properties";

    @Test
    void loadsClassFromContextClassLoaderWhenDefaultLookupMisses() {
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        AliasClassLoader contextClassLoader = new AliasClassLoader(original);
        try {
            Thread.currentThread().setContextClassLoader(contextClassLoader);
            assertSame(Utils.class, Utils.loadClass(CONTEXT_CLASS_ALIAS));
            assertTrue(contextClassLoader.wasAliasRequested());
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    @Test
    void loadsClassesAndBundledResources() throws Exception {
        assertEquals(Utils.class, Utils.loadClass("com.alibaba.druid.util.Utils"));
        assertFalse(Utils.loadProperties(FILTER_RESOURCE).isEmpty());
        assertFalse(Utils.readLines(FILTER_RESOURCE).isEmpty());
        assertTrue(Utils.readFromResource(FILTER_RESOURCE).contains("druid.filters.stat"));
        assertTrue(Utils.readByteArrayFromResource(FILTER_RESOURCE).length > 0);

        Set<String> lines = new HashSet<>();
        Utils.loadFromFile(FILTER_RESOURCE, lines);
        assertFalse(lines.isEmpty());

        ClassLoader original = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(ClassLoader.getPlatformClassLoader());
            assertNotNull(Utils.readFromResource("/META-INF/druid-filter.properties"));
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    private static final class AliasClassLoader extends ClassLoader {
        private boolean aliasRequested;

        private AliasClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (CONTEXT_CLASS_ALIAS.equals(name)) {
                aliasRequested = true;
                return Utils.class;
            }
            return super.loadClass(name);
        }

        private boolean wasAliasRequested() {
            return aliasRequested;
        }
    }
}
