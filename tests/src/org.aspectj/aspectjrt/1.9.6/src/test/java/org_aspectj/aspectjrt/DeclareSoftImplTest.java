/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjrt;

import java.io.IOException;

import org.aspectj.internal.lang.annotation.ajcDeclareSoft;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.AjType;
import org.aspectj.lang.reflect.AjTypeSystem;
import org.aspectj.lang.reflect.DeclareSoft;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DeclareSoftImplTest {

    @Test
    void exposesSoftenedExceptionTypeForCompilerDeclareSoftMarker() throws Exception {
        AjType<DeclareSoftAspect> aspectType = AjTypeSystem.getAjType(DeclareSoftAspect.class);

        DeclareSoft[] declareSofts = aspectType.getDeclareSofts();

        assertThat(declareSofts).hasSize(1);
        assertThat(declareSofts[0].getDeclaringType().getJavaClass()).isEqualTo(DeclareSoftAspect.class);
        assertThat(declareSofts[0].getPointcutExpression().asString()).isEqualTo("execution(* *(..))");
        assertThat(declareSofts[0].getSoftenedExceptionType().getJavaClass()).isEqualTo(IOException.class);
    }

    @Aspect
    public static class DeclareSoftAspect {
        @ajcDeclareSoft(
                exceptionType = "java.io.IOException",
                pointcut = "execution(* *(..))"
        )
        public void softenIoExceptions() {
        }
    }
}
