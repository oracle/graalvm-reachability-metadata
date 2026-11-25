/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjweaver;

import org.aspectj.apache.bcel.classfile.JavaClass;
import org.aspectj.apache.bcel.util.ClassPath;
import org.aspectj.apache.bcel.util.SyntheticRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SyntheticRepositoryTest {
    @Test
    void parsesAndCachesBytecodeForRuntimeClass() throws Exception {
        SyntheticRepository repository = SyntheticRepository.getInstance(new ClassPath(""));
        repository.clear();
        try {
            JavaClass parsed = repository.loadClass(SyntheticRepository.class);

            assertThat(parsed.getClassName()).isEqualTo(SyntheticRepository.class.getName());
            assertThat(parsed.getSuperclassName()).isEqualTo(Object.class.getName());
            assertThat(repository.findClass(parsed.getClassName())).isSameAs(parsed);
        } finally {
            repository.clear();
        }
    }
}
