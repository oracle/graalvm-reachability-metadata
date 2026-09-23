/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_azure.azure_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.azure.core.implementation.SemanticVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(60)
public class SemanticVersionTest {
    @Test
    void resolvesThePackageVersionOfAnAzureCoreClass() {
        SemanticVersion version = SemanticVersion.getPackageVersionForClass("com.azure.core.util.CoreUtils");

        assertThat(version.isValid()).isTrue();
        assertThat(version.getMajorVersion()).isPositive();
        assertThat(version.getVersionString()).isNotBlank();
    }
}
