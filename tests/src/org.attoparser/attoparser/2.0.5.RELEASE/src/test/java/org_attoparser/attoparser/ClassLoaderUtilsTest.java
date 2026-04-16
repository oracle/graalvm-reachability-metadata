/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_attoparser.attoparser;

import java.io.InputStream;

import org.attoparser.AttoParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassLoaderUtilsTest {

    @Test
    void attoParserVersionFallsBackToTheLibraryClassLoader() {
        Thread currentThread = Thread.currentThread();
        ClassLoader originalClassLoader = currentThread.getContextClassLoader();
        TrackingClassLoader trackingClassLoader = new TrackingClassLoader();

        currentThread.setContextClassLoader(trackingClassLoader);
        try {
            assertEquals("2.0.5.RELEASE", AttoParser.VERSION);
            assertEquals("RELEASE", AttoParser.VERSION_TYPE);
            assertTrue(AttoParser.isVersionStableRelease());

            assertEquals(1, trackingClassLoader.requestCount);
            assertEquals("org/attoparser/attoparser.properties", trackingClassLoader.lastRequestedResourceName);
        } finally {
            currentThread.setContextClassLoader(originalClassLoader);
        }
    }

    private static final class TrackingClassLoader extends ClassLoader {

        private int requestCount;
        private String lastRequestedResourceName;

        private TrackingClassLoader() {
            super(null);
        }

        @Override
        public InputStream getResourceAsStream(final String name) {
            this.requestCount++;
            this.lastRequestedResourceName = name;
            return null;
        }

    }

}
