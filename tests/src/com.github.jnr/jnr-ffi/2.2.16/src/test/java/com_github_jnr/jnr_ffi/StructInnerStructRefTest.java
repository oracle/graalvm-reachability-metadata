/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_jnr.jnr_ffi;

import jnr.ffi.Runtime;
import jnr.ffi.Struct;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class StructInnerStructRefTest {
    public static class Item extends Struct {
        public final Signed32 value = new Signed32();

        public Item(Runtime runtime) {
            super(runtime);
        }
    }

    public static class Holder extends Struct {
        public final StructRef<Item> items = new StructRef<>(Item.class, 2);

        public Holder(Runtime runtime) {
            super(runtime);
        }
    }

    @Test
    void materializesSingleAndArrayStructReferences() {
        Holder holder = new Holder(Runtime.getSystemRuntime());
        Item[] items = holder.items.get(2);
        items[0].value.set(41);
        items[1].value.set(43);

        assertThat(holder.items.get().value.get()).isEqualTo(41);
        assertThat(holder.items.get(2)).extracting(item -> item.value.get()).containsExactly(41, 43);
    }
}
