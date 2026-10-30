/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

public interface TimeLimitedService {
    String greet(String name);

    int length(String value) throws InterruptedException;

    class Implementation implements TimeLimitedService {
        @Override
        public String greet(String name) {
            return "hello " + name;
        }

        @Override
        public int length(String value) {
            return value.length();
        }
    }
}
