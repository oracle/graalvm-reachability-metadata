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
    void loadsBytecodeResourceForRuntimeClass() throws Exception {
        SyntheticRepository repository = SyntheticRepository.getInstance(new ClassPath("synthetic-repository-resource-only"));
        repository.clear();
        String className = SyntheticRepository.class.getName();

        try {
            JavaClass loadedClass = repository.loadClass(SyntheticRepository.class);

            assertThat(loadedClass.getClassName()).isEqualTo(className);
            assertThat(repository.findClass(className)).isSameAs(loadedClass);
        } catch (ClassNotFoundException exception) {
            assertThat(exception).hasMessage("SyntheticRepository could not load " + className);
            assertThat(repository.findClass(className)).isNull();
        }
    }
}
