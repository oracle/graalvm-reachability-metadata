/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ai_djl.api;

import static org.assertj.core.api.Assertions.assertThat;

import ai.djl.repository.JarRepository;
import ai.djl.repository.Repository;
import org.junit.jupiter.api.Test;

public class RepositoryFactoryImplInnerJarRepositoryFactoryTest {
    @Test
    void createsRepositoryFromClasspathJarResource() {
        Repository repository = Repository.newInstance("fixture", "jar:/djl-test-model.txt");

        assertThat(repository).isInstanceOf(JarRepository.class);
        assertThat(repository.getName()).isEqualTo("fixture");
    }
}
