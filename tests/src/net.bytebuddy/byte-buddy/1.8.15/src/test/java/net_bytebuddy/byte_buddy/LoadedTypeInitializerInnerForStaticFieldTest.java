/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import java.lang.reflect.Modifier;

import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.scaffold.InstrumentedType;
import net.bytebuddy.implementation.FixedValue;
import net.bytebuddy.implementation.LoadedTypeInitializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LoadedTypeInitializerInnerForStaticFieldTest {
    public static class Initialized {
        public static Object message;
    }

    @Test
    void initializesExistingStaticFieldFromPreparedFixedValue() {
        Object value = new Object();
        InstrumentedType type = FixedValue.reference(value, "message").prepare(
                InstrumentedType.Factory.Default.MODIFIABLE.subclass(Initialized.class.getName(),
                        Modifier.PUBLIC, TypeDescription.Generic.OBJECT));
        LoadedTypeInitializer initializer = type.getLoadedTypeInitializer();
        assertTrue(initializer.isAlive());
        try {
            initializer.onLoad(Initialized.class);
            assertSame(value, Initialized.message);
        } finally {
            Initialized.message = null;
        }
    }
}
