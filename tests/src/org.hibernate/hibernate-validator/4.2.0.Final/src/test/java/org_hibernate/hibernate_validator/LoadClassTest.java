/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_validator;

import static org.assertj.core.api.Assertions.assertThat;

import org.hibernate.validator.HibernateValidator;
import org.hibernate.validator.util.ReflectionHelper;
import org.junit.jupiter.api.Test;

public class LoadClassTest {
    @Test
    public void loadsValidatorClassFromContextLoaderWhenCallerUsesBootstrapLoader() {
        Class<?> loadedClass =
                ReflectionHelper.loadClass(
                        "org.hibernate.validator.HibernateValidator", String.class);

        assertThat(loadedClass).isSameAs(HibernateValidator.class);
    }
}
