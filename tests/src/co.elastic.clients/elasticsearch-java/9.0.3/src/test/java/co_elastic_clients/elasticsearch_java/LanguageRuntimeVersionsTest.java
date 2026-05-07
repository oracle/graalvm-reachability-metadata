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
    void detectsAvailableJvmLanguageRuntimeVersions() {
        String kotlinVersion = LanguageRuntimeVersions.kotlinVersion();
        String scalaVersion = LanguageRuntimeVersions.scalaVersion();

        assertThat(kotlinVersion).matches("\\d+\\.\\d+");
        assertThat(scalaVersion).matches("\\d+\\.\\d+");
        assertThat(LanguageRuntimeVersions.getRuntimeMetadata())
                .contains(",kt=" + kotlinVersion)
                .contains(",sc=" + scalaVersion);
    }

    @Test
    void returnsNullForUnavailableJvmLanguageRuntimeVersions() {
        assertThat(LanguageRuntimeVersions.clojureVersion()).isNull();
        assertThat(LanguageRuntimeVersions.groovyVersion()).isNull();
        assertThat(LanguageRuntimeVersions.jRubyVersion()).isNull();
    }
}
