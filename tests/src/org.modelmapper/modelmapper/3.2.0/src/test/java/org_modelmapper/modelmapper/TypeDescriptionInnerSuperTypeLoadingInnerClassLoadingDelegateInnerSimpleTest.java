/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_modelmapper.modelmapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.modelmapper.internal.bytebuddy.description.type.TypeDescription.SuperTypeLoading.ClassLoadingDelegate.Simple;

public class TypeDescriptionInnerSuperTypeLoadingInnerClassLoadingDelegateInnerSimpleTest {
    @Test
    void loadsClassByName() throws ClassNotFoundException {
        Class<?> loadedType = Simple.INSTANCE.load(String.class.getName(), null);

        assertThat(loadedType).isSameAs(String.class);
    }
}
