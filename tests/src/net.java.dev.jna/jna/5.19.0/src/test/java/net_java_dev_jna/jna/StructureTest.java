/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jna.jna;

import com.sun.jna.Callback;
import com.sun.jna.CallbackReference;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class StructureTest {
    @Test
    void createsTypedArrayViewsFromStructureMemory() {
        PointerBackedStructure structure = new PointerBackedStructure();
        structure.value = 7;
        structure.write();

        PointerBackedStructure[] array = (PointerBackedStructure[]) structure.toArray(3);

        assertThat(array).hasSize(3);
        assertThat(array[0]).isSameAs(structure);
        assertThat(array[1]).isInstanceOf(PointerBackedStructure.class);
        assertThat(array[2]).isInstanceOf(PointerBackedStructure.class);
    }

    @Test
    void createsStructureInstanceFromPointerConstructor() {
        PointerBackedStructure original = new PointerBackedStructure();
        original.value = 42;
        original.write();

        PointerBackedStructure overlay = Structure.newInstance(PointerBackedStructure.class, original.getPointer());
        overlay.read();

        assertThat(overlay.value).isEqualTo(42);
        assertThat(overlay.getPointer()).isEqualTo(original.getPointer());
    }

    @Test
    void validatesStructureCallbackParameterTypesWhenCreatingFunctionPointer() {
        Pointer functionPointer = CallbackReference.getFunctionPointer(new NoopStructureCallback());

        assertThat(functionPointer).isNotNull();
    }

    public interface StructureCallback extends Callback {
        void invoke(PointerBackedStructure structure);
    }

    public static final class NoopStructureCallback implements StructureCallback {
        @Override
        public void invoke(PointerBackedStructure structure) {
        }
    }

    public static class PointerBackedStructure extends Structure {
        public int value;

        public PointerBackedStructure() {
        }

        public PointerBackedStructure(Pointer pointer) {
            super(pointer);
        }

        @Override
        protected List<String> getFieldOrder() {
            return Collections.singletonList("value");
        }
    }
}
