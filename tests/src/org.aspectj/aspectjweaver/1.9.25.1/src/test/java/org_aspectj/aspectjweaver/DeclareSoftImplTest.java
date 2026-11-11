/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjweaver;

import java.io.IOException;

import org.aspectj.internal.lang.annotation.ajcDeclareSoft;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.AjTypeSystem;
import org.aspectj.lang.reflect.DeclareSoft;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DeclareSoftImplTest {
    @Test
    void resolvesSoftenedExceptionInAspectScope() throws Exception {
        DeclareSoft[] declarations = AjTypeSystem.getAjType(SofteningAspect.class).getDeclareSofts();

        assertThat(declarations).hasSize(1);
        assertThat(declarations[0].getSoftenedExceptionType().getJavaClass()).isEqualTo(IOException.class);
        assertThat(declarations[0].getPointcutExpression().asString()).isEqualTo("execution(* *(..))");
        assertThat(declarations[0].getDeclaringType().getJavaClass()).isEqualTo(SofteningAspect.class);
    }

    @Aspect
    public static class SofteningAspect {
        @ajcDeclareSoft(exceptionType = "java.io.IOException", pointcut = "execution(* *(..))")
        public void softenIo() {
        }
    }
}
