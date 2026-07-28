/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_api;

public class ReflectionUtilsFixture {
    private final String value;

    public ReflectionUtilsFixture(String value) {
        this.value = value;
    }

    public static String describe(Integer number) {
        return "fixture-" + number;
    }

    public String value() {
        return value;
    }
}
