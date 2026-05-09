/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_autoconfigure;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.boot.autoconfigure.session.SessionProperties;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;

import static org.assertj.core.api.Assertions.assertThat;

public class SessionAutoConfigurationInnerAbstractSessionRepositoryImplementationValidatorTest {

    private static final String VALIDATOR_CLASS_NAME = "org.springframework.boot.autoconfigure.session."
            + "SessionAutoConfiguration$ServletSessionRepositoryImplementationValidator";

    @Test
    void servletSessionRepositoryValidatorScansCandidateImplementations() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(SessionProperties.class);
            context.registerBeanDefinition("sessionRepositoryImplementationValidator",
                    BeanDefinitionBuilder.genericBeanDefinition(VALIDATOR_CLASS_NAME).getBeanDefinition());

            context.refresh();

            Object validator = context.getBean("sessionRepositoryImplementationValidator");
            assertThat(validator.getClass().getName()).isEqualTo(VALIDATOR_CLASS_NAME);
            assertThat(JdbcIndexedSessionRepository.class.getName())
                    .isEqualTo("org.springframework.session.jdbc.JdbcIndexedSessionRepository");
            assertThat(context.getBean(SessionProperties.class).getStoreType()).isNull();
        }
    }

}
