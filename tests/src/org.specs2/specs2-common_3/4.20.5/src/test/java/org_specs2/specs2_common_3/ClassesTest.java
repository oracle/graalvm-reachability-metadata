/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_specs2.specs2_common_3;

import org.junit.jupiter.api.Test;
import org.specs2.control.eff.Eff;
import org.specs2.control.eff.Evaluate;
import org.specs2.control.eff.Fx1;
import org.specs2.control.eff.Fx3;
import org.specs2.control.eff.FxAppend;
import org.specs2.control.eff.Safe;
import org.specs2.control.eff.Writer;
import org.specs2.control.package$;
import org.specs2.data.UniqueNames;
import org.specs2.execute.StandardResults$;
import org.specs2.reflect.Classes;
import org.specs2.reflect.Classes$;
import org.specs2.time.SimpleTimer;
import scala.collection.immutable.List;
import scala.collection.immutable.List$;
import scala.reflect.ClassTag$;
import scala.util.Either;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ClassesTest {
    private final Classes classes = Classes$.MODULE$;
    private final ClassLoader loader = ClassesTest.class.getClassLoader();

    @Test
    void loadsAnAvailableClass() {
        Either<Throwable, Class<SimpleTimer>> loaded = run(
                classes.<SimpleTimer>loadClassEither(SimpleTimer.class.getName(), loader));

        assertTrue(loaded.isRight(), loaded::toString);
        assertSame(SimpleTimer.class, loaded.toOption().get());
    }

    @Test
    void findsAnAvailableClass() {
        assertEquals(Boolean.TRUE, run(classes.existsClass(SimpleTimer.class.getName(), loader)));
    }

    @Test
    void createsAClassWithNoConstructorArguments() {
        SimpleTimer timer = run(classes.createInstanceFromClass(
                SimpleTimer.class, loader, List$.MODULE$::empty,
                ClassTag$.MODULE$.apply(SimpleTimer.class)));

        assertTrue(timer.neverStarted());
        assertEquals(0L, timer.totalMillis());
        assertEquals("0 ms", timer.time());
    }

    @Test
    void createsAnInstanceAsARightValue() {
        Either<Throwable, SimpleTimer> created = run(classes.createInstanceEither(
                SimpleTimer.class.getName(), loader, List$.MODULE$::empty,
                ClassTag$.MODULE$.apply(SimpleTimer.class)));

        assertTrue(created.isRight(), created::toString);
        SimpleTimer timer = created.toOption().get();
        assertTrue(timer.neverStarted());
        assertEquals("0 ms", timer.time());
    }

    @Test
    void retrievesTheScalaObjectSingleton() {
        StandardResults$ results = run(classes.createInstance(
                StandardResults$.class.getName(), loader, List$.MODULE$::empty,
                ClassTag$.MODULE$.apply(StandardResults$.class)));

        assertSame(StandardResults$.MODULE$, results);
        assertTrue(results.success().isSuccess());
        assertEquals("success", results.success().message());
    }

    @Test
    void recursivelyCreatesAConstructorDependency() {
        UniqueNames names = run(classes.createInstance(
                UniqueNames.class.getName(), loader, List$.MODULE$::empty,
                ClassTag$.MODULE$.apply(UniqueNames.class)));

        assertEquals("", names.separator());
        assertEquals("example", names.uniqueName("example"));
        assertEquals("example1", names.uniqueName("example"));
        assertEquals("example2", names.uniqueName("example"));
    }

    @Test
    void suppliesAMatchingDefaultConstructorDependency() {
        List<Object> defaults = List$.MODULE$.<Object>empty().$colon$colon("-");
        UniqueNames names = run(classes.createInstanceFromClass(
                UniqueNames.class, loader, () -> defaults,
                ClassTag$.MODULE$.apply(UniqueNames.class)));

        assertEquals("-", names.separator());
        assertEquals("example", names.uniqueName("example"));
        assertEquals("example-1", names.uniqueName("example"));
        assertEquals("example-2", names.uniqueName("example"));
    }

    private static <T> T run(Eff<FxAppend<Fx1<Evaluate<String, Object>>,
            Fx3<Writer<String, Object>, Writer<String, Object>, Safe>>, T> operation) {
        Either<Either<Throwable, String>, T> result = package$.MODULE$.runOperation(
                operation, package$.MODULE$.noLogging());
        assertTrue(result.isRight(), result::toString);
        return result.toOption().get();
    }
}
