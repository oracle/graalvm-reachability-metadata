/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_geronimo_specs.geronimo_osgi_locator;

import java.util.concurrent.Callable;

public class GreetingProvider implements Callable<String> {
    @Override
    public String call() {
        return "Hello from the greeting provider";
    }
}
