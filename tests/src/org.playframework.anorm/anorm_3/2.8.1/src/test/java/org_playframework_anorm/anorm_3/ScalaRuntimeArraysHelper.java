/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_playframework_anorm.anorm_3;

import scala.runtime.Arrays;

public final class ScalaRuntimeArraysHelper {
    private ScalaRuntimeArraysHelper() {
    }

    public static String[][] newStringMatrix(int rows, int columns) {
        Object matrix = Arrays.newArray(String.class, String[][].class, new int[] { rows, columns });
        return (String[][]) matrix;
    }
}
