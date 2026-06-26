/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_1_2_api;

import java.util.concurrent.Callable;
import org.apache.log4j.helpers.Loader;

public final class Log4jLoaderIsolatedAction implements Callable<String> {

    @Override
    public String call() throws ClassNotFoundException {
        return Loader.loadClass(String.class.getName()).getName();
    }
}
