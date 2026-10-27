/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_playframework_anorm.anorm_3;

final class ScalaRuntimeArraysAccess {
    private ScalaRuntimeArraysAccess() {
    }

    static String[][] newStringMatrix(int rows, int columns) {
        return scala.runtime.Arrays$.MODULE$.newArray(
                String.class,
                String[][].class,
                new int[] {rows, columns});
    }

    static int[][] newIntMatrix(int rows, int columns) {
        return scala.runtime.Arrays$.MODULE$.newArray(
                int.class,
                int[][].class,
                new int[] {rows, columns});
    }
}
