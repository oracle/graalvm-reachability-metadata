/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import groovyjarjarantlr4.v4.Tool;
import groovyjarjarantlr4.v4.tool.ANTLRMessage;
import groovyjarjarantlr4.v4.tool.ErrorType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ErrorManagerTest {
    @Test
    void loadsDiagnosticFormatsWhenTheContextLoaderHasNoAntlrResources() {
        Thread thread = Thread.currentThread();
        ClassLoader original = thread.getContextClassLoader();
        try {
            thread.setContextClassLoader(ClassLoader.getPlatformClassLoader());
            Tool tool = new Tool();
            tool.errMgr.setFormat("gnu");
            ANTLRMessage message = new ANTLRMessage(ErrorType.CANNOT_OPEN_FILE, null, "shipment.g4");
            String diagnostic = tool.errMgr.getMessageTemplate(message).render();
            assertTrue(diagnostic.contains("shipment.g4"));
            assertTrue(diagnostic.contains("cannot find or open file"));
        } finally {
            thread.setContextClassLoader(original);
        }
    }
}
