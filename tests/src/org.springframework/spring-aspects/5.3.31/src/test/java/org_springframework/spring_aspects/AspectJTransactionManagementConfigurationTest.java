/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_aspects;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.aspectj.AnnotationTransactionAspect;
import org.springframework.transaction.aspectj.AspectJTransactionManagementConfiguration;

public class AspectJTransactionManagementConfigurationTest {

    @Test
    void createsTheSingletonTransactionAspect() {
        AspectJTransactionManagementConfiguration configuration =
                new AspectJTransactionManagementConfiguration();

        AnnotationTransactionAspect aspect = configuration.transactionAspect();

        assertThat(aspect).isSameAs(AnnotationTransactionAspect.aspectOf());
        assertThat(AnnotationTransactionAspect.hasAspect()).isTrue();
    }
}
