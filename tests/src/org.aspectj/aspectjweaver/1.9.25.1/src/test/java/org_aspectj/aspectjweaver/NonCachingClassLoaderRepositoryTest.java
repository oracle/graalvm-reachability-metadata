/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjweaver;

import org.aspectj.apache.bcel.classfile.JavaClass;
import org.aspectj.apache.bcel.util.NonCachingClassLoaderRepository;
import org.aspectj.weaver.tools.PointcutParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class NonCachingClassLoaderRepositoryTest {
    @Test
    void parsesClassLoaderBytecodeResourceAndKeepsLocalIdentity() throws Exception {
        NonCachingClassLoaderRepository repository = new NonCachingClassLoaderRepository(getClass().getClassLoader());
        JavaClass parsed = repository.loadClass(PointcutParser.class.getName());

        assertThat(parsed.getClassName()).isEqualTo(PointcutParser.class.getName());
        assertThat(parsed.getMethods()).extracting(method -> method.getName()).contains("parsePointcutExpression");
        assertThat(repository.loadClass(PointcutParser.class.getName())).isSameAs(parsed);
        repository.clear();
        assertThat(repository.findClass(PointcutParser.class.getName())).isNull();
    }
}
