/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pdfbox.pdfbox;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pdfbox.pdmodel.encryption.SecurityHandler;
import org.apache.pdfbox.pdmodel.encryption.SecurityHandlerFactory;
import org.apache.pdfbox.pdmodel.encryption.StandardSecurityHandler;
import org.junit.jupiter.api.Test;

public class SecurityHandlerFactoryTest {
    @Test
    void newSecurityHandlerForFilterCreatesRegisteredStandardHandler() {
        SecurityHandler<?> securityHandler = SecurityHandlerFactory.INSTANCE
                .newSecurityHandlerForFilter(StandardSecurityHandler.FILTER);

        assertThat(securityHandler).isInstanceOf(StandardSecurityHandler.class);
    }
}
