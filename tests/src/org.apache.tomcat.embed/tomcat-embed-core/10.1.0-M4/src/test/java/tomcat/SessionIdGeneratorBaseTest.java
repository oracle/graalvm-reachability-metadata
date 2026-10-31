package tomcat;

import java.security.SecureRandom;

import org.apache.catalina.util.StandardSessionIdGenerator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SessionIdGeneratorBaseTest {
    @Test
    void generatesRoutedIdentifiersWithConfiguredRandomClass() {
        StandardSessionIdGenerator generator = new StandardSessionIdGenerator();
        generator.setSecureRandomClass(SecureRandom.class.getName());
        generator.setSessionIdLength(24);
        generator.setJvmRoute("worker");
        String first = generator.generateSessionId();
        String second = generator.generateSessionId();
        assertThat(first).matches("[0-9A-F]{48}\\.worker");
        assertThat(second).matches("[0-9A-F]{48}\\.worker").isNotEqualTo(first);
    }
}
