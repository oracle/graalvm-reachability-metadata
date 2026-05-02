/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_hk2.hk2_utils;

import java.lang.reflect.Method;

import org.glassfish.hk2.utilities.reflection.ClassReflectionHelper;
import org.glassfish.hk2.utilities.reflection.internal.ClassReflectionHelperImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassReflectionHelperImplTest {
    private final ClassReflectionHelper helper = new ClassReflectionHelperImpl();

    @Test
    public void findsConventionLifecycleMethodsThroughMatchingClassOptimization() {
        final Method postConstruct = helper.findPostConstruct(LifecycleService.class, LifecycleMarker.class);
        final Method preDestroy = helper.findPreDestroy(LifecycleService.class, LifecycleMarker.class);

        assertThat(postConstruct.getName()).isEqualTo("postConstruct");
        assertThat(postConstruct.getParameterCount()).isZero();
        assertThat(postConstruct.getDeclaringClass()).isEqualTo(LifecycleService.class);
        assertThat(preDestroy.getName()).isEqualTo("preDestroy");
        assertThat(preDestroy.getParameterCount()).isZero();
        assertThat(preDestroy.getDeclaringClass()).isEqualTo(LifecycleService.class);
    }

    public interface LifecycleMarker {
    }

    public static final class LifecycleService implements LifecycleMarker {
        public void postConstruct() {
        }

        public void preDestroy() {
        }
    }
}
