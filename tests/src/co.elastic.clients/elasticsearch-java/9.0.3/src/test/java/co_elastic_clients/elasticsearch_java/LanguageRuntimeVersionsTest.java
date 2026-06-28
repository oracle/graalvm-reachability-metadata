/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package co_elastic_clients.elasticsearch_java;

import co.elastic.clients.util.LanguageRuntimeVersions;
import kotlin.KotlinVersion;
import org.junit.jupiter.api.Test;
import scala.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

public class LanguageRuntimeVersionsTest {
    @Test
    void nativeLanguageVersionHelpersReturnDetectedJvmLanguageRuntimes() {
        String detectedKotlinVersion = LanguageRuntimeVersions.kotlinVersion();
        String detectedScalaVersion = LanguageRuntimeVersions.scalaVersion();

        String kotlinVersion = keepMajorMinor(KotlinVersion.CURRENT.toString());
        String scalaVersion = keepMajorMinor(Properties.versionNumberString());

        assertThat(detectedKotlinVersion).isEqualTo(kotlinVersion);
        assertThat(detectedScalaVersion).isEqualTo(scalaVersion);
    }

    private static String keepMajorMinor(String version) {
        int firstDot = version.indexOf('.');
        int secondDot = version.indexOf('.', firstDot + 1);
        if (secondDot < 0) {
            return version;
        }
        return version.substring(0, secondDot);
    }
}
