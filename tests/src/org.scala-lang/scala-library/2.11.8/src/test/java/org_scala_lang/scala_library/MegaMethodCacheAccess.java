/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scala_lang.scala_library;

import scala.Product;
import scala.runtime.MegaMethodCache;

public final class MegaMethodCacheAccess {
    private MegaMethodCacheAccess() {
    }

    public static String findProductArityMethodName() {
        MegaMethodCache cache = new MegaMethodCache("productArity", new Class<?>[0]);
        return cache.find(Product.class).getName();
    }
}
