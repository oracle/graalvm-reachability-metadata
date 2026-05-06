/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_junit_jupiter.junit_jupiter_api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.JRE;

public class JRETest {
    @Test
    void currentVersionUsesRuntimeVersion() {
        JRE currentVersion = JRE.currentVersion();

        assertThat(currentVersion).isEqualTo(expectedCurrentVersion());
        assertThat(currentVersion.isCurrentVersion()).isTrue();
    }

    private static JRE expectedCurrentVersion() {
        int featureVersion = Runtime.version().feature();

        switch (featureVersion) {
            case 8:
                return JRE.JAVA_8;
            case 9:
                return JRE.JAVA_9;
            case 10:
                return JRE.JAVA_10;
            case 11:
                return JRE.JAVA_11;
            case 12:
                return JRE.JAVA_12;
            case 13:
                return JRE.JAVA_13;
            case 14:
                return JRE.JAVA_14;
            case 15:
                return JRE.JAVA_15;
            case 16:
                return JRE.JAVA_16;
            case 17:
                return JRE.JAVA_17;
            case 18:
                return JRE.JAVA_18;
            default:
                return JRE.OTHER;
        }
    }
}
