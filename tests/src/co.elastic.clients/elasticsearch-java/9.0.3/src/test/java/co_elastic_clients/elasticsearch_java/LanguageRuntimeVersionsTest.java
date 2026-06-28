/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package co_elastic_clients.elasticsearch_java;

import co.elastic.clients.util.LanguageRuntimeVersions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LanguageRuntimeVersionsTest {
    @Test
    void detectsJvmLanguageRuntimeVersionsOnTheTestClasspath() {
        String metadata = LanguageRuntimeVersions.getRuntimeMetadata();

        assertThat(metadata)
            .containsPattern("(^|,)kt=\\d+\\.\\d+(,|$)")
            .containsPattern("(^|,)sc=\\d+\\.\\d+(,|$)");
        assertThat(LanguageRuntimeVersions.kotlinVersion()).matches("\\d+\\.\\d+");
        assertThat(LanguageRuntimeVersions.scalaVersion()).matches("\\d+\\.\\d+");
    }
}
