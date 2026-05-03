/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_zio.izumi_reflect_3;

import izumi.reflect.dottyreflection.ReflectionUtil.UncheckedNonOverloadedSelectable$;
import scala.collection.immutable.Seq;

final class UncheckedSelectableInvoker {
    private UncheckedSelectableInvoker() {
    }

    static Object invoke(Object target, String name, Seq<Class<?>> paramTypes, Seq<Object> args) {
        return UncheckedNonOverloadedSelectable$.MODULE$.applyDynamic$extension(
                target,
                name,
                paramTypes,
                args);
    }
}
