/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringApplicationAotProcessor;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

public class SpringApplicationAotProcessorTest {

    @TempDir
    Path tempDir;

    @Test
    void mainProcessesApplicationWithNoArgumentMainMethod() throws Exception {
        Path sourceOutput = this.tempDir.resolve("source");
        Path resourceOutput = this.tempDir.resolve("resources");
        Path classOutput = this.tempDir.resolve("classes");

        SpringApplicationAotProcessor.main(new String[] { NoArgumentSpringBootApplication.class.getName(),
                sourceOutput.toString(), resourceOutput.toString(), classOutput.toString(), "org.example", "demo" });

        assertThat(Files.isDirectory(sourceOutput)).isTrue();
        assertThat(Files.isDirectory(resourceOutput)).isTrue();
    }

}

@Configuration(proxyBeanMethods = false)
class NoArgumentSpringBootApplication {

    public static void main() {
        SpringApplication.run(NoArgumentSpringBootApplication.class);
    }

}
