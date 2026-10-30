/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cglib.cglib;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.sf.cglib.core.Constants;
import net.sf.cglib.proxy.InterfaceMaker;
import net.sf.cglib.transform.NullClassVisitor;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.CodeVisitor;

import static org.assertj.core.api.Assertions.assertThat;

public class InterfaceMakerTest {
    @Test
    void collectsPublicAndInheritedMethodsForInterfaceEmission() throws Exception {
        InterfaceMaker maker = new InterfaceMaker();
        maker.add(MessageService.class);
        maker.add(MessageService.class);

        // Reuse a precompiled interface without defining a class at runtime.
        maker.setClassLoader(MessageContract.class.getClassLoader());
        maker.setNamingPolicy((prefix, source, key, names) -> MessageContract.class.getName());
        maker.setAttemptLoad(true);
        assertThat(maker.create()).isSameAs(MessageContract.class);

        Map<String, List<String>> declarations = new LinkedHashMap<>();
        maker.generateClass(new NullClassVisitor() {
            @Override
            public CodeVisitor visitMethod(int access, String name, String descriptor,
                    String[] exceptions, Attribute attributes) {
                assertThat(access).isEqualTo(Constants.ACC_PUBLIC | Constants.ACC_ABSTRACT);
                declarations.put(name + descriptor, List.of(exceptions));
                return super.visitMethod(access, name, descriptor, exceptions, attributes);
            }
        });

        assertThat(declarations).containsOnlyKeys("text(Ljava/lang/String;)Ljava/lang/String;", "count()I");
        assertThat(declarations.get("text(Ljava/lang/String;)Ljava/lang/String;"))
                .containsExactly("java/io/IOException");
        assertThat(declarations.get("count()I")).isEmpty();
    }

    public interface MessageContract {
        String text(String name) throws IOException;

        int count();
    }

    public static class MessageBase {
        public String text(String name) throws IOException {
            return "hello " + name;
        }
    }

    public static class MessageService extends MessageBase implements MessageContract {
        @Override
        public int count() {
            return 1;
        }

        protected String internalText() {
            return "internal";
        }
    }
}
