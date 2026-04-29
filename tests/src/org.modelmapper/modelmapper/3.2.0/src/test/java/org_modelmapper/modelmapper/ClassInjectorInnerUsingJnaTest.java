/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_modelmapper.modelmapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.modelmapper.internal.bytebuddy.dynamic.loading.ClassInjector;

public class ClassInjectorInnerUsingJnaTest {

    @Test
    void returnsAlreadyVisibleClassWithoutDefiningItAgain() {
        ClassInjector.UsingJna classInjector = new ClassInjector.UsingJna(getClass().getClassLoader());
        String visibleTypeName = ClassInjectorInnerUsingJnaTest.class.getName();

        Map<String, Class<?>> injectedTypes = classInjector.injectRaw(
            Collections.singletonMap(visibleTypeName, new byte[0]));

        assertThat(injectedTypes).containsEntry(visibleTypeName, ClassInjectorInnerUsingJnaTest.class);
    }
}
