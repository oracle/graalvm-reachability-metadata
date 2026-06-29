/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.hsqldb.dynamicaccess;

public final class RoutineJavaMethods {
    private RoutineJavaMethods() {
    }

    public static Integer increment(Integer value) {
        return value + 1;
    }

    public static Integer totalLength(
            String value,
            Boolean finished,
            Integer[] total,
            Integer[] itemCount) {

        int currentTotal = total[0] == null ? 0 : total[0];
        int currentCount = itemCount[0] == null ? 0 : itemCount[0];
        boolean finalCall = Boolean.TRUE.equals(finished);

        if (!finalCall && value != null) {
            currentTotal += value.length();
            currentCount++;
        }

        total[0] = currentTotal;
        itemCount[0] = currentCount;

        return currentTotal;
    }
}
