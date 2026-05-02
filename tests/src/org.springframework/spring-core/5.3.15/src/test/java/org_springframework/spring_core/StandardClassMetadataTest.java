/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.core.type.StandardClassMetadata;

public class StandardClassMetadataTest {

    @Test
    @SuppressWarnings("deprecation")
    void reportsDeclaredMemberClassNames() {
        StandardClassMetadata metadata = new StandardClassMetadata(ClassWithMemberTypes.class);

        String[] memberClassNames = metadata.getMemberClassNames();

        assertThat(memberClassNames).containsExactlyInAnyOrder(
                ClassWithMemberTypes.PublicNestedClass.class.getName(),
                ClassWithMemberTypes.PrivateNestedClass.class.getName(),
                ClassWithMemberTypes.NestedInterface.class.getName()
        );
    }

    private static final class ClassWithMemberTypes {

        public static class PublicNestedClass {
        }

        private static class PrivateNestedClass {
        }

        private interface NestedInterface {
        }
    }
}
