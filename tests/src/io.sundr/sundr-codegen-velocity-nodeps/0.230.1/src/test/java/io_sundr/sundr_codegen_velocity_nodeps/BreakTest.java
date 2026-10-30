/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.sundr.deps.org.apache.velocity.VelocityContext;
import io.sundr.deps.org.apache.velocity.exception.VelocityException;
import org.junit.jupiter.api.Test;

public class BreakTest {
    @Test
    void exercisesPublicApi() throws Exception {
        VelocityContext context = new VelocityContext();
        assertEquals("12", TestSupport.render(context, "#foreach($n in [1..5])#if($n == 3)#break($foreach)#end$n#end"));
    }

    @Test
    void rejectsBreakArgumentsThatAreNotScopes() {
        VelocityContext context = new VelocityContext();
        context.put("scope", "not a scope");
        VelocityException exception = assertThrows(VelocityException.class,
                () -> TestSupport.render(context, "#foreach($n in [1..2])#break($scope)#end"));
        assertTrue(exception.getMessage().contains("not a valid"));
        assertTrue(exception.getMessage().contains("io.sundr.deps.org.apache.velocity.runtime.directive.Scope"));
    }
}
