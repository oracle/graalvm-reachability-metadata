/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jdbi.jdbi3_core;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.extension.Extensions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ExtensionFactoryDelegateTest {
    @Test
    void attachesAVirtualExtensionThroughAProxy() {
        Jdbi jdbi = Jdbi.create("jdbc:h2:mem:extension_delegate;DB_CLOSE_DELAY=-1");
        jdbi.getConfig(Extensions.class).register(extensionType -> extensionType == VirtualExtension.class);

        String result = jdbi.withExtension(VirtualExtension.class, VirtualExtension::value);

        assertThat(result).isEqualTo("virtual");
    }

    public interface VirtualExtension {
        default String value() {
            return "virtual";
        }
    }
}
