/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import java.util.Locale;
import java.util.TimeZone;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.bouncycastle.pkix.util.LocalizedMessage;

import static org.assertj.core.api.Assertions.assertThat;

public class BouncyCastlePkixLocalizedMessageTest {
    private static final String BUNDLE = "org_testcontainers.testcontainers.messages";

    @Test
    void resolvesPkixMessagesWithDefaultAndExplicitClassLoaders() {
        LocalizedMessage defaultLoader = new LocalizedMessage(BUNDLE, "greeting");
        LocalizedMessage explicitLoader = new LocalizedMessage(BUNDLE, "greeting");
        explicitLoader.setClassLoader(getClass().getClassLoader());

        assertThat(defaultLoader.getEntry(null, Locale.ROOT, TimeZone.getTimeZone("UTC"))).isEqualTo("Hello");
        assertThat(explicitLoader.getEntry(null, Locale.ROOT, TimeZone.getTimeZone("UTC"))).isEqualTo("Hello");
    }
}
