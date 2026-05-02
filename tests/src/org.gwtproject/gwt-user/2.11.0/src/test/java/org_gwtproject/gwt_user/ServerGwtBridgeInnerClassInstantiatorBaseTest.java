/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_gwtproject.gwt_user;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gwt.core.server.ServerGwtBridge.ClassInstantiatorBase;
import com.google.gwt.core.server.ServerGwtBridge.Properties;

import org.junit.jupiter.api.Test;

public class ServerGwtBridgeInnerClassInstantiatorBaseTest {
    @Test
    void tryCreateWithClassCreatesPublicNoArgType() {
        ExposingClassInstantiator instantiator = new ExposingClassInstantiator();

        ConstructibleType created = instantiator.createFromClass(ConstructibleType.class);

        assertThat(created.message()).isEqualTo("constructed by public constructor");
    }

    @Test
    void tryCreateWithClassNameLoadsAndCreatesPublicNoArgType() {
        ExposingClassInstantiator instantiator = new ExposingClassInstantiator();

        ConstructibleType created = instantiator.createFromName(ConstructibleType.class.getName());

        assertThat(created.message()).isEqualTo("constructed by public constructor");
    }

    public static final class ConstructibleType {
        private final String message;

        public ConstructibleType() {
            message = "constructed by public constructor";
        }

        String message() {
            return message;
        }
    }

    private static final class ExposingClassInstantiator extends ClassInstantiatorBase {
        <T> T createFromClass(Class<T> type) {
            return tryCreate(type);
        }

        <T> T createFromName(String className) {
            return tryCreate(className);
        }

        @Override
        public <T> T create(Class<?> baseClass, Properties properties) {
            throw new UnsupportedOperationException("Use explicit test helper methods");
        }
    }
}
