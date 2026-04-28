/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javassist_javassist;

import java.io.Serializable;

import javassist.ClassClassPath;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.tools.rmi.Proxy;
import javassist.tools.rmi.Sample;
import javassist.tools.rmi.StubGenerator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class StubGeneratorTest {
    @Test
    void createsProxyClassFromRuntimeClassMethods() throws Exception {
        ClassPool classPool = createClassPoolWithSampleClass();
        StubGenerator generator = new StubGenerator();
        generator.start(classPool);

        boolean created = generator.makeProxyClass(Sample.class);

        assertThat(created).isTrue();
        assertThat(generator.isProxyClass(Sample.class.getName())).isTrue();
        CtClass proxyClass = classPool.get(Sample.class.getName());
        assertThat(proxyClass.getDeclaredMethod("_getObjectId")).isNotNull();
        assertThat(proxyClass.subtypeOf(classPool.get(Proxy.class.getName()))).isTrue();
        assertThat(proxyClass.subtypeOf(classPool.get(Serializable.class.getName()))).isTrue();
    }

    @Test
    void reportsExistingProxyClassWithoutRegeneratingIt() throws Exception {
        ClassPool classPool = createClassPoolWithSampleClass();
        StubGenerator generator = new StubGenerator();
        generator.start(classPool);
        generator.makeProxyClass(Sample.class);

        boolean createdAgain = generator.makeProxyClass(Sample.class);

        assertThat(createdAgain).isFalse();
        assertThat(generator.isProxyClass(Sample.class.getName())).isTrue();
    }

    private static ClassPool createClassPoolWithSampleClass() {
        ClassPool classPool = new ClassPool(true);
        classPool.insertClassPath(new ClassClassPath(Sample.class));
        return classPool;
    }
}
