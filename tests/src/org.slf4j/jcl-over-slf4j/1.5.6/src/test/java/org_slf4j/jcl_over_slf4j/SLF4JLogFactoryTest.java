/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_slf4j.jcl_over_slf4j;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.impl.SLF4JLogFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SLF4JLogFactoryTest {

    @Test
    void getInstanceReturnsCachedLoggerForNameAndClass() {
        SLF4JLogFactory factory = new SLF4JLogFactory();
        String loggerName = SLF4JLogFactoryTest.class.getName();

        Log namedLog = factory.getInstance(loggerName);
        Log secondNamedLog = factory.getInstance(loggerName);
        Log classLog = factory.getInstance(SLF4JLogFactoryTest.class);

        assertThat(namedLog).isNotNull();
        assertThat(namedLog).isSameAs(secondNamedLog);
        assertThat(classLog).isSameAs(namedLog);
    }

    @Test
    void attributesCanBeListedRetrievedAndRemoved() {
        SLF4JLogFactory factory = new SLF4JLogFactory();

        factory.setAttribute("alpha", "one");
        factory.setAttribute("beta", 2);

        assertThat(factory.getAttribute("alpha")).isEqualTo("one");
        assertThat(factory.getAttribute("beta")).isEqualTo(2);
        assertThat(factory.getAttributeNames()).containsExactlyInAnyOrder("alpha", "beta");

        factory.setAttribute("alpha", null);
        factory.removeAttribute("beta");

        assertThat(factory.getAttribute("alpha")).isNull();
        assertThat(factory.getAttribute("beta")).isNull();
        assertThat(factory.getAttributeNames()).isEmpty();
    }

    @Test
    void releaseWarnsAboutUnsupportedCommonsLoggingDeployment() {
        SLF4JLogFactory factory = new SLF4JLogFactory();

        String output = captureStandardOut(factory::release);

        assertThat(output).contains(
                "WARN: The method",
                SLF4JLogFactory.class.getName(),
                "#release() was invoked.",
                "http://www.slf4j.org/codes.html#release");
    }

    private static String captureStandardOut(Runnable action) {
        PrintStream previousOut = System.out;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (PrintStream capturedOut = new PrintStream(out, true, StandardCharsets.UTF_8)) {
            System.setOut(capturedOut);
            action.run();
        } finally {
            System.setOut(previousOut);
        }
        return out.toString(StandardCharsets.UTF_8);
    }
}
