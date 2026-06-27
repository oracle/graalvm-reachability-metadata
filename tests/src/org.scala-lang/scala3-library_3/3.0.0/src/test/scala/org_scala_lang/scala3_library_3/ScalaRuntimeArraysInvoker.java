/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scala_lang.scala3_library_3;

final class ScalaRuntimeArraysInvoker {
    private ScalaRuntimeArraysInvoker() {
    }

    static String[][] newStringMatrix(int[] dimensions) {
        return scala.runtime.Arrays.newArray(String.class, String[][].class, dimensions);
    }
}
