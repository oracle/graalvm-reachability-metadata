/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cglib.cglib;

import net.sf.cglib.core.ClassEmitter;
import net.sf.cglib.core.CodeEmitter;
import net.sf.cglib.core.Constants;
import net.sf.cglib.core.EmitUtils;
import net.sf.cglib.core.Signature;
import net.sf.cglib.transform.NullClassVisitor;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Type;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

public class EmitUtilsTest {
    @Test
    void rejectsUnsupportedArrayElementConstants() {
        ClassEmitter emitter = new ClassEmitter(NullClassVisitor.INSTANCE);
        emitter.begin_class(Constants.V1_2, Constants.ACC_PUBLIC,
                "cglib.cglib.ConstantArray", Type.getType(Object.class), null, null);
        CodeEmitter method = emitter.begin_method(Constants.ACC_PUBLIC | Constants.ACC_STATIC,
                new Signature("values", "()[Ljava/lang/StringBuilder;"), null, null);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> EmitUtils.push_array(method, new StringBuilder[]{new StringBuilder("value")}))
                .withMessage("unknown type: class java.lang.StringBuilder");
    }
}
