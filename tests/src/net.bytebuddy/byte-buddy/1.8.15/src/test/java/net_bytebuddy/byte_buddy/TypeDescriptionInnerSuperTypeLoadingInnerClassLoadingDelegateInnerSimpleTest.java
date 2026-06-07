/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import net.bytebuddy.description.type.TypeDescription;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TypeDescriptionInnerSuperTypeLoadingInnerClassLoadingDelegateInnerSimpleTest {
    @Test
    void loadsSuperClassThroughDefaultClassLoadingDelegate() {
        TypeDescription delegate = TypeDescription.ForLoadedType.of(LoadedChild.class);
        TypeDescription typeDescription = new TypeDescription.SuperTypeLoading(
                delegate,
                getClass().getClassLoader());

        TypeDescription loadedSuperType = typeDescription.getSuperClass().asErasure();

        assertThat(loadedSuperType.represents(LoadedParent.class)).isTrue();
    }

    private static class LoadedParent {
    }

    private static class LoadedChild extends LoadedParent {
    }
}
