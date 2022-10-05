/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.ecwid.consul.utils;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;

import jakarta.servlet.http.HttpServlet;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.Tomcat;

public final class ConsulTestUtils {

    private ConsulTestUtils() {
        throw new IllegalStateException("Do not instantiate utility class");
    }

    public static Tomcat initTomcat(int port, HttpServlet servlet) throws LifecycleException {
        Tomcat tomcat = new Tomcat();
        Connector connector = new Connector("HTTP/1.1");
        connector.setPort(port);
        tomcat.setConnector(connector);
        Context context = tomcat.addContext("", new File(".").getAbsolutePath());
        Tomcat.addDefaultMimeTypeMappings(context);
        Tomcat.addServlet(context, "test", servlet);
        context.addServletMappingDecoded("/*", "test");
        tomcat.start();
        return tomcat;
    }


    public static int getFreePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
