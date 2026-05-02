/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.arjuna.common.logging;

import java.io.Serializable;
import java.net.URL;
import java.text.MessageFormat;
import java.util.Locale;

import org.jboss.logging.Logger;

// CheckStyle: start generated
public class commonI18NLogger_$logger implements commonI18NLogger, Serializable {
    private static final long serialVersionUID = 1L;

    private final Logger log;

    public commonI18NLogger_$logger(Logger log) {
        this.log = log;
    }

    @Override
    public void warn_could_not_find_manifest(String manifestName, Throwable cause) {
        log.warn(format("Could not find manifest {0}", manifestName), cause);
    }

    @Override
    public void warn_could_not_find_config_file(URL url) {
        log.warn(format("Could not find configuration file, URL was: {0}", url));
    }

    @Override
    public void warn_common_ClassloadingUtility_1() {
        log.warn("className is null");
    }

    @Override
    public void warn_common_ClassloadingUtility_2(String className, Throwable cause) {
        log.warn(format("attempt to load {0} threw ClassNotFound. Wrong classloader?", className), cause);
    }

    @Override
    public void warn_common_ClassloadingUtility_3(String className, String interfaceName, Throwable cause) {
        log.warn(format("class {0} does not implement {1}", className, interfaceName), cause);
    }

    @Override
    public String warn_common_ClassloadingUtility_4(String className, Throwable cause) {
        return format("cannot create new instance of {0}", className);
    }

    @Override
    public String warn_common_ClassloadingUtility_5(String className, Throwable cause) {
        return format("cannot access {0}", className);
    }

    @Override
    public void warn_common_ClassloadingUtility_6(String value, Throwable cause) {
        log.warn(format("cannot initialize from string {0}", value), cause);
    }

    @Override
    public String warn_common_NoSuchMethodException(String className, Throwable cause) {
        return format("cannot create new instance of {0} as no zero-argument constructor exists", className);
    }

    private static String format(String pattern, Object... arguments) {
        return new MessageFormat(pattern, Locale.ROOT).format(arguments);
    }
}
// CheckStyle: stop generated
