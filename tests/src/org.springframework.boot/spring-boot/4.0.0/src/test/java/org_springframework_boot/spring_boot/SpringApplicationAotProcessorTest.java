/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringApplicationAotProcessor;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class SpringApplicationAotProcessorTest {
    private static final String DEPLOYMENT_PROPERTY = "reachability.boot.deployment";

    @TempDir
    Path directory;

    @Test
    void forwardsApplicationArgumentsAndSurfacesApplicationValidation() {
        assertThatThrownBy(() -> SpringApplicationAotProcessor.main(new String[] {
                ArgumentApplication.class.getName(), directory.resolve("sources").toString(),
                directory.resolve("resources").toString(), directory.resolve("classes").toString(),
                "example", "application", "unknown-deployment" }))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported deployment: unknown-deployment");
    }

    @Test
    void supportsNoArgumentMainAndSurfacesApplicationValidation() {
        String previous = System.getProperty(DEPLOYMENT_PROPERTY);
        System.setProperty(DEPLOYMENT_PROPERTY, "unknown-deployment");
        try {
            assertThatThrownBy(() -> SpringApplicationAotProcessor.main(new String[] {
                    NoArgumentApplication.class.getName(), directory.resolve("sources").toString(),
                    directory.resolve("resources").toString(), directory.resolve("classes").toString(),
                    "example", "application" }))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Unsupported deployment: unknown-deployment");
        } finally {
            if (previous == null) {
                System.clearProperty(DEPLOYMENT_PROPERTY);
            } else {
                System.setProperty(DEPLOYMENT_PROPERTY, previous);
            }
        }
    }

    public static class ArgumentApplication {
        public static void main(String[] args) {
            String deployment = args.length > 0 ? args[0] : "local";
            if (!deployment.equals("local")) {
                throw new IllegalArgumentException("Unsupported deployment: " + deployment);
            }
            SpringApplication.run(ArgumentApplication.class, args);
        }
    }

    public static class NoArgumentApplication {
        public static void main() {
            String deployment = System.getProperty(DEPLOYMENT_PROPERTY, "local");
            if (!deployment.equals("local")) {
                throw new IllegalArgumentException("Unsupported deployment: " + deployment);
            }
            SpringApplication.run(NoArgumentApplication.class);
        }
    }
}
