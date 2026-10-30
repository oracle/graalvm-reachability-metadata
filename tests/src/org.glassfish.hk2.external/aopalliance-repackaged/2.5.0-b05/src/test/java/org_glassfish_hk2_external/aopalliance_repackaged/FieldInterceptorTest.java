/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_hk2_external.aopalliance_repackaged;

import org.aopalliance.intercept.FieldAccess;
import org.aopalliance.intercept.FieldInterceptor;
import org.junit.jupiter.api.Test;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class FieldInterceptorTest {
    @Test
    void interceptsWritesAndDecoratesReadsOfATargetField() throws Throwable {
        final Settings target = new Settings();
        final List<String> events = new ArrayList<>();
        final FieldInterceptor auditing = new FieldInterceptor() {
            @Override
            public Object get(FieldAccess access) throws Throwable {
                assertThat(access.getAccessType()).isEqualTo(FieldAccess.READ);
                assertThat(access.getThis()).isSameAs(target);
                assertThat(access.getField().getName()).isEqualTo("label");
                assertThat(access.getStaticPart()).isSameAs(access.getField());
                events.add("read");
                return "label=" + access.proceed();
            }

            @Override
            public Object set(FieldAccess access) throws Throwable {
                assertThat(access.getAccessType()).isEqualTo(FieldAccess.WRITE);
                assertThat(access.getValueToSet()).isEqualTo("production");
                events.add("write:" + access.getValueToSet());
                return access.proceed();
            }
        };

        assertThat(auditing.set(new LabelAccess(target, FieldAccess.WRITE, "production"))).isEqualTo("production");
        assertThat(target.label).isEqualTo("production");
        assertThat(auditing.get(new LabelAccess(target, FieldAccess.READ, null))).isEqualTo("label=production");
        assertThat(events).containsExactly("write:production", "read");
    }

    public static final class Settings {
        public String label = "default";
    }

    private static final class LabelAccess implements FieldAccess {
        private final Settings target;
        private final int accessType;
        private final Object value;
        private final Field field;

        private LabelAccess(Settings target, int accessType, Object value) throws NoSuchFieldException {
            this.target = target;
            this.accessType = accessType;
            this.value = value;
            this.field = Settings.class.getField("label");
        }

        @Override
        public Object proceed() {
            if (accessType == WRITE) {
                target.label = (String) value;
            }
            return target.label;
        }

        @Override
        public Object getThis() {
            return target;
        }

        @Override
        public AccessibleObject getStaticPart() {
            return field;
        }

        @Override
        public Field getField() {
            return field;
        }

        @Override
        public Object getValueToSet() {
            return value;
        }

        @Override
        public int getAccessType() {
            return accessType;
        }
    }
}
