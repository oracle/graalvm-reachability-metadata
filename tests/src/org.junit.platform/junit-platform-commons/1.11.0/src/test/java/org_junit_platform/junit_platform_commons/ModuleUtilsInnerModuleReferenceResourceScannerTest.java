/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_junit_platform.junit_platform_commons;

import org.junit.jupiter.api.Test;
import org.junit.platform.commons.util.ModuleUtils;

import static org.assertj.core.api.Assertions.assertThatNullPointerException;

public class ModuleUtilsInnerModuleReferenceResourceScannerTest {

    @Test
    void attemptsToLoadModuleResourcesBeforeApplyingCallerFilter() {
        assertThatNullPointerException().isThrownBy(
                () -> ModuleUtils.findAllResourcesInModule("java.base", resource -> false));
    }
}
