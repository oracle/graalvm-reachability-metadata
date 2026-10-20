/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.dubbo;

public class DubboEchoMock implements AbstractInterfaceConfigTest.Echo {
    public DubboEchoMock() { }

    @Override
    public String echo(String value) {
        return "mock:" + value;
    }
}
