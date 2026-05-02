/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_gwtproject.gwt_user;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gwt.user.tools.util.CreatorUtilities;

import org.junit.jupiter.api.Test;

import java.util.List;

public class CreatorUtilitiesTest {
    @Test
    void validatePathsAndModulesReportsMissingModuleDefinition() {
        final boolean valid = CreatorUtilities.validatePathsAndModules(
                "gwt-user.jar",
                null,
                List.of("org_gwtproject.gwt_user.creator.DoesNotExist"));

        assertThat(valid).isFalse();
    }
}
