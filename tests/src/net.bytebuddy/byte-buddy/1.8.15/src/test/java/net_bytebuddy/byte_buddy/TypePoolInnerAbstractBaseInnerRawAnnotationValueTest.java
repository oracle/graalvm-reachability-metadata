/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import java.io.InputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Map;

import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.annotation.AnnotationValue;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.pool.TypePool;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TypePoolInnerAbstractBaseInnerRawAnnotationValueTest {
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Envelope {
        TestFixtures.Label value();
    }

    @Test
    void loadsNestedAnnotationFromAnOfflineClassFile() throws Exception {
        byte[] bytes;
        try (InputStream input = getClass().getResourceAsStream("/nested-annotation.bin")) {
            assertNotNull(input);
            bytes = input.readAllBytes();
        }
        String name = "net_bytebuddy.byte_buddy.OfflineAnnotationEnvelope";
        TypePool.CacheProvider cache = TypePool.CacheProvider.Simple.withObjectType();
        for (Class<?> type : new Class<?>[]{Envelope.class, TestFixtures.Label.class}) {
            cache.register(type.getName(), new TypePool.Resolution.Simple(TypeDescription.ForLoadedType.of(type)));
        }
        // The fixture is class-file data for parsing, never a class to define or execute.
        TypePool pool = new TypePool.Default(cache, new ClassFileLocator.Simple(Map.of(name, bytes)),
                TypePool.Default.ReaderMode.FAST);
        AnnotationDescription envelope = pool.describe(name).resolve().getDeclaredAnnotations().ofType(Envelope.class);
        AnnotationValue<?, ?> nested = envelope.getValue(TestFixtures.property(Envelope.class, "value"));

        assertEquals("parsed-nested", nested.resolve(AnnotationDescription.class)
                .getValue(TestFixtures.property(TestFixtures.Label.class, "value")).resolve(String.class));
        AnnotationValue.Loaded<?> loaded = nested.load(getClass().getClassLoader());
        assertTrue(loaded.getState().isResolved());
        assertEquals("parsed-nested", loaded.resolve(TestFixtures.Label.class).value());
        assertEquals("parsed-nested", envelope.prepare(Envelope.class).load().value().value());
    }
}
