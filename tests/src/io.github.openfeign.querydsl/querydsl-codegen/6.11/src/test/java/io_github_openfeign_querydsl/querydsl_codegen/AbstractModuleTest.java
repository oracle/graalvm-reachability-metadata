/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_github_openfeign_querydsl.querydsl_codegen;

import com.querydsl.codegen.CodegenModule;
import com.querydsl.codegen.JavaTypeMappings;
import com.querydsl.codegen.QueryTypeFactory;
import com.querydsl.codegen.QueryTypeFactoryImpl;
import com.querydsl.codegen.TypeMappings;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractModuleTest {

    @Test
    void createsConfiguredCodegenServices() {
        CodegenModule module = new CodegenModule();

        assertThat(module.get(TypeMappings.class)).isInstanceOf(JavaTypeMappings.class);
        assertThat(module.get(QueryTypeFactory.class)).isInstanceOf(QueryTypeFactoryImpl.class);
    }
}
