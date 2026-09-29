/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_weaver_privilizer_api;

import java.util.Locale;
import org.apache.commons.weaver.privilizer.Privileged;
import org.apache.commons.weaver.privilizer.Privilizing;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Commons_weaver_privilizer_apiTest {
    @Test
    void privilegedMethodRemainsUsableAsAnOrdinaryPublicMethod() {
        PrivilegedService service = new PrivilegedService();

        assertThat(service.normalize("  hello  ")).isEqualTo("HELLO");
    }

    @Test
    void privilizingDeclarationSupportsMultipleBlueprintMethods() {
        BlueprintService service = new BlueprintService();

        assertThat(service.describe("weaver")).isEqualTo("WEAVER:6");
    }

    public static class PrivilegedService {
        @Privileged
        public String normalize(String value) {
            return value.trim().toUpperCase(Locale.ROOT);
        }
    }

    @Privilizing({
        @Privilizing.CallTo(value = String.class, methods = {"toUpperCase", "length"})
    })
    public static class BlueprintService {
        public String describe(String value) {
            String normalized = value.toUpperCase(Locale.ROOT);
            return normalized + ":" + normalized.length();
        }
    }
}
