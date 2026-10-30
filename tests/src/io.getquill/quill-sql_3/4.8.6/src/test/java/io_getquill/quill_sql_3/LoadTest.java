/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_getquill.quill_sql_3;

import io.getquill.Escape;
import io.getquill.Literal$;
import io.getquill.NamingStrategy;
import io.getquill.SnakeCase;
import io.getquill.UpperCase;
import io.getquill.util.Load.Module$;
import org.junit.jupiter.api.Test;
import scala.reflect.ClassTag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class LoadTest {
    @Test
    void loadsNamingModuleFromItsStrategyType() {
        SnakeCase naming = Module$.MODULE$.fromClassTag(ClassTag.<SnakeCase>apply(SnakeCase.class)).get();

        assertEquals("purchase_order", naming.table("PurchaseOrder"));
        assertEquals("delivery_date", naming.column("deliveryDate"));
        assertSame(naming, Module$.MODULE$.fromClassTag(ClassTag.<SnakeCase>apply(SnakeCase.class)).get());
    }

    @Test
    void loadsNamingModuleFromItsModuleType() {
        Literal$ naming = Module$.MODULE$.fromClassTag(ClassTag.<Literal$>apply(Literal$.class)).get();

        assertEquals("PurchaseOrder", naming.table("PurchaseOrder"));
        assertEquals("deliveryDate", naming.column("deliveryDate"));
        assertSame(naming, Module$.MODULE$.fromClassTag(ClassTag.<Literal$>apply(Literal$.class)).get());
    }

    @Test
    void composesLoadedNamingModulesInOrder() {
        SnakeCase snake = Module$.MODULE$.fromClassTag(ClassTag.<SnakeCase>apply(SnakeCase.class)).get();
        UpperCase upper = Module$.MODULE$.fromClassTag(ClassTag.<UpperCase>apply(UpperCase.class)).get();
        Escape escape = Module$.MODULE$.fromClassTag(ClassTag.<Escape>apply(Escape.class)).get();
        NamingStrategy naming = NamingStrategy.apply(snake, upper, escape);

        assertEquals("\"PURCHASE_ORDER\"", naming.table("PurchaseOrder"));
        assertEquals("\"DELIVERY_DATE\"", naming.column("deliveryDate"));
    }
}
