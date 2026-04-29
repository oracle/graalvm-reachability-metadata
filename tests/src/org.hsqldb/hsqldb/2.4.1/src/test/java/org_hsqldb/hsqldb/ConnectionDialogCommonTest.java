/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Hashtable;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ConnectionDialogCommonTest {
    private static final String SETTING_NAME = "connection-dialog-common-coverage";

    @TempDir
    Path tempDir;

    @Test
    public void persistsAndReloadsRecentConnectionSettings() throws Exception {
        Class<?> commonClass = Class.forName("org.hsqldb.util.ConnectionDialogCommon");
        File settingsFile = tempDir.resolve("hsqlprefs.dat").toFile();
        Object previousRecentSettings = setStaticField(commonClass, "recentSettings", settingsFile);
        Object previousHomeDir = setStaticField(commonClass, "homedir", tempDir.toString());

        try {
            Object setting = newConnectionSetting();
            Hashtable<String, Object> settings = new Hashtable<>();

            settings.put(SETTING_NAME, setting);
            invokeStatic(commonClass, "addToRecentConnectionSettings",
                    new Class<?>[] { Hashtable.class, setting.getClass() }, settings, setting);

            assertTrue(Files.size(settingsFile.toPath()) > 0);

            Hashtable<?, ?> loadedSettings = (Hashtable<?, ?>) invokeStatic(commonClass, "loadRecentConnectionSettings",
                    new Class<?>[0]);

            assertTrue(loadedSettings.containsKey(SETTING_NAME));
        } finally {
            setStaticField(commonClass, "recentSettings", previousRecentSettings);
            setStaticField(commonClass, "homedir", previousHomeDir);
        }
    }

    private static Object newConnectionSetting() throws Exception {
        Class<?> settingClass = Class.forName("org.hsqldb.util.ConnectionSetting");
        Constructor<?> constructor = settingClass.getDeclaredConstructor(String.class, String.class, String.class,
                String.class, String.class);

        constructor.setAccessible(true);

        return constructor.newInstance(SETTING_NAME, "org.hsqldb.jdbc.JDBCDriver", "jdbc:hsqldb:mem:coverage", "SA",
                "");
    }

    private static Object invokeStatic(Class<?> targetClass, String methodName, Class<?>[] parameterTypes,
            Object... arguments) throws Exception {
        Method method = targetClass.getDeclaredMethod(methodName, parameterTypes);

        method.setAccessible(true);

        return method.invoke(null, arguments);
    }

    private static Object setStaticField(Class<?> targetClass, String fieldName, Object newValue) throws Exception {
        Field field = targetClass.getDeclaredField(fieldName);

        field.setAccessible(true);
        Object previousValue = field.get(null);

        field.set(null, newValue);

        return previousValue;
    }
}
