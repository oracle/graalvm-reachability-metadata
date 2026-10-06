/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import org.apache.catalina.startup.Catalina;
import org.apache.catalina.core.StandardServer;
import org.apache.tomcat.util.digester.Digester;

public class DigesterGeneratedCodeLoader implements Digester.GeneratedCodeLoader {
    private static String requestedClassName;

    public DigesterGeneratedCodeLoader() {
    }

    @Override
    public Object loadGeneratedCode(String className) {
        requestedClassName = className;
        return (Catalina.ServerXml) catalina -> catalina.setServer(new StandardServer());
    }

    public static String getRequestedClassName() {
        return requestedClassName;
    }
}
