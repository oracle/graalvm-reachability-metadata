/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jdiff_tests;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import jdiff.API;
import jdiff.XMLToAPI;

final class ApiFiles {
    private ApiFiles() {
    }

    static API read(Path directory, String name, String packages) throws IOException {
        Path file = directory.resolve(name + ".xml");
        Files.writeString(file, "<api name=\"" + name + "\">" + packages + "</api>");
        return XMLToAPI.readFile(file.toUri().toString(), false, file.getFileName().toString());
    }

    static String packageWithClass(String packageName, String members) {
        return """
                <package name="%s">
                  <class name="Widget" abstract="false" static="false" final="false"
                         deprecated="not deprecated" visibility="public">
                    %s
                  </class>
                </package>
                """.formatted(packageName, members);
    }

    static String method(String name, String returnType) {
        return """
                <method name="%s" return="%s" abstract="false" native="false"
                        synchronized="false" static="false" final="false"
                        deprecated="not deprecated" visibility="public">
                  <param name="key" type="java.lang.String"/>
                  <exception name="java.io.IOException"/>
                </method>
                """.formatted(name, returnType);
    }
}
