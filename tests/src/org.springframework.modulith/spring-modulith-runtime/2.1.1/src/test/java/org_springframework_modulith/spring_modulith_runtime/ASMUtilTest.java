/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_modulith.spring_modulith_runtime;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.runtime.minidev.asm.ASMUtil;
import org.springframework.modulith.runtime.minidev.asm.Accessor;

public class ASMUtilTest {
    @Test
    void discoversInheritedFieldsAndExcludesTransientAndStaticState() {
        Accessor[] properties = ASMUtil.getAccessors(ModuleRecord.class, null);

        assertThat(properties).extracting(Accessor::getName).containsExactlyInAnyOrder("name", "owner");
        assertThat(properties).allSatisfy(property -> {
            assertThat(property.getType()).isEqualTo(String.class);
            assertThat(property.isPublic()).isTrue();
            assertThat(property.isReadable()).isTrue();
            assertThat(property.isWritable()).isTrue();
        });
    }

    public static class OwnedRecord {
        public String owner;
    }

    public static class ModuleRecord extends OwnedRecord {
        public static final String CATEGORY = "module";
        public String name;
        public transient String cachedDescription;
    }
}
