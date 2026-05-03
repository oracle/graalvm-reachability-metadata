/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_xml_dtd_parser.dtd_parser.bootstrap;

import com.sun.xml.dtdparser.MessageCatalog;

public final class BootstrapMessageCatalog extends MessageCatalog {
    public static final BootstrapMessageCatalog INSTANCE = new BootstrapMessageCatalog();

    private BootstrapMessageCatalog() {
        super(BootstrapMessageCatalog.class);
    }

    public static boolean isLoadedByBootstrap() {
        return BootstrapMessageCatalog.class.getClassLoader() == null;
    }
}
