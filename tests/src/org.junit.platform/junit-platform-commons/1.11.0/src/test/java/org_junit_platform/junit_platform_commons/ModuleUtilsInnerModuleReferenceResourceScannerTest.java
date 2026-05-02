/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_junit_platform.junit_platform_commons;

import org.junit.jupiter.api.Test;
import org.junit.platform.commons.util.ModuleUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ModuleUtilsInnerModuleReferenceResourceScannerTest {

    @Test
    void scansNamedModuleAndAttemptsToLoadResourceBeforeApplyingFilter() {
        assertThatThrownBy(() -> ModuleUtils.findAllResourcesInModule("java.base", resource -> {
            assertThat(resource.getName()).isNotBlank();
            assertThat(resource.getUri()).isNotNull();
            throw new ResourceFilterReachedException();
        })).satisfies(throwable -> assertThat(throwable).isInstanceOfAny(ResourceFilterReachedException.class,
                NullPointerException.class));
    }

    private static class ResourceFilterReachedException extends RuntimeException {

        private static final long serialVersionUID = 1L;
    }
}
