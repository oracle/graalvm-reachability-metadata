/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjweaver;

import static org.assertj.core.api.Assertions.assertThat;

import org.aspectj.apache.bcel.classfile.JavaClass;
import org.aspectj.apache.bcel.util.ClassPath;
import org.aspectj.apache.bcel.util.SyntheticRepository;
import org.junit.jupiter.api.Test;

public class SyntheticRepositoryTest {
    @Test
    void loadClassUsesClassResourceStreamForSuppliedClass() throws Exception {
        SyntheticRepository repository = SyntheticRepository.getInstance(new ClassPath(""));
        repository.clear();

        JavaClass javaClass = repository.loadClass(SyntheticRepository.class);

        assertThat(javaClass.getClassName()).isEqualTo(SyntheticRepository.class.getName());
        assertThat(repository.findClass(SyntheticRepository.class.getName())).isSameAs(javaClass);
    }
}
