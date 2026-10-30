/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;


import static org.junit.jupiter.api.Assertions.assertEquals;

import io.sundr.deps.org.apache.velocity.VelocityContext;
import java.util.Iterator;
import java.util.List;
import org.junit.jupiter.api.Test;

public class UberspectImplTest {
    @Test
    void exercisesPublicApi() throws Exception {
        VelocityContext context = new VelocityContext();
        context.put("items", new Items());
        assertEquals("onetwo", TestSupport.render(context, "#foreach($item in $items)$item#end"));
    }

    public static class Items {
        public Iterator<String> iterator() {
            return List.of("one", "two").iterator();
        }
    }
}
