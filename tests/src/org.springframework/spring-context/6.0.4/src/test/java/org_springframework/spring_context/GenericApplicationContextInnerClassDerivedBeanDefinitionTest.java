/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_context;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

public class GenericApplicationContextInnerClassDerivedBeanDefinitionTest {
    @Test
    void registerBeanWithoutSupplierUsesPreferredPublicConstructors() {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.registerBean(PublicConstructorBean.class);
            context.refresh();

            PublicConstructorBean bean = context.getBean(PublicConstructorBean.class);

            assertThat(bean.message()).isEqualTo("created through public constructor");
        }
    }

    public static final class PublicConstructorBean {
        private final String message;

        public PublicConstructorBean() {
            this.message = "created through public constructor";
        }

        String message() {
            return message;
        }
    }
}
