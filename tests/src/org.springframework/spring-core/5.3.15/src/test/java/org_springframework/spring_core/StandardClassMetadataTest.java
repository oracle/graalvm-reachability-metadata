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

/** Verifies reflective discovery of member classes. */
@SuppressWarnings("deprecation")
public class StandardClassMetadataTest {
    @Test
    void returnsDeclaredMemberClassNames() {
        StandardClassMetadata metadata = new StandardClassMetadata(Container.class);

        assertThat(metadata.getMemberClassNames()).containsExactly(Container.Member.class.getName());
    }

    public static final class Container {
        public static final class Member {}
    }
}
