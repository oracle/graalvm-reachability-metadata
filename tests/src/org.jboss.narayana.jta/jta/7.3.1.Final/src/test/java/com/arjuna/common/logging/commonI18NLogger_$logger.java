/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.arjuna.common.logging;

import java.io.Serializable;
import java.net.URL;

import org.jboss.logging.Logger;

@SuppressWarnings("checkstyle:TypeName")
public final class commonI18NLogger_$logger implements commonI18NLogger, Serializable {
    private static final long serialVersionUID = 1L;

    private final Logger logger;

    public commonI18NLogger_$logger(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void warn_could_not_find_manifest(String manifestName, Throwable throwable) {
        logger.warnf(throwable, "Could not find manifest %s", manifestName);
    }

    @Override
    public void warn_could_not_find_config_file(URL url) {
        logger.warnf("Could not find configuration file %s", url);
    }

    @Override
    public void warn_common_ClassloadingUtility_1() {
        logger.warn("Classloading utility could not load a class");
    }

    @Override
    public void warn_common_ClassloadingUtility_2(String className, Throwable throwable) {
        logger.warnf(throwable, "Could not load class %s", className);
    }

    @Override
    public void warn_common_ClassloadingUtility_3(String className, String loaderName, Throwable throwable) {
        logger.warnf(throwable, "Could not load class %s with class loader %s", className, loaderName);
    }

    @Override
    public String warn_common_ClassloadingUtility_4(String className, Throwable throwable) {
        return String.format("Could not instantiate class %s: %s", className, throwable);
    }

    @Override
    public String warn_common_ClassloadingUtility_5(String className, Throwable throwable) {
        return String.format("Could not access class %s: %s", className, throwable);
    }

    @Override
    public void warn_common_ClassloadingUtility_6(String className, Throwable throwable) {
        logger.warnf(throwable, "Could not create class %s", className);
    }

    @Override
    public String warn_common_NoSuchMethodException(String methodName, Throwable throwable) {
        return String.format("Could not find method %s: %s", methodName, throwable);
    }
}
