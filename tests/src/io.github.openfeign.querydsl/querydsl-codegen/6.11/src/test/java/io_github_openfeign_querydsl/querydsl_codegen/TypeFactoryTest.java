/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_github_openfeign_querydsl.querydsl_codegen;

import java.util.List;

import com.querydsl.codegen.TypeFactory;
import com.querydsl.codegen.utils.model.Type;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TypeFactoryTest {

    @Test
    void createsModelTypeForGenericClass() {
        Type type = new TypeFactory().get(List.class);

        assertThat(type.getJavaClass()).isEqualTo(List.class);
    }
}
