/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.sun.jdmk.comm;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.DynamicMBean;
import javax.management.InvalidAttributeValueException;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanOperationInfo;
import javax.management.ReflectionException;
import java.util.concurrent.atomic.AtomicInteger;

public class HtmlAdapterServer implements DynamicMBean {
    private static final AtomicInteger START_COUNT = new AtomicInteger();

    public static int getStartCount() {
        return START_COUNT.get();
    }

    public static void reset() {
        START_COUNT.set(0);
    }

    public void start() {
        START_COUNT.incrementAndGet();
    }

    @Override
    public Object getAttribute(String attribute) throws AttributeNotFoundException {
        throw new AttributeNotFoundException(attribute);
    }

    @Override
    public void setAttribute(Attribute attribute) throws AttributeNotFoundException, InvalidAttributeValueException {
        throw new AttributeNotFoundException(attribute == null ? null : attribute.getName());
    }

    @Override
    public AttributeList getAttributes(String[] attributes) {
        return new AttributeList();
    }

    @Override
    public AttributeList setAttributes(AttributeList attributes) {
        return new AttributeList();
    }

    @Override
    public Object invoke(String actionName, Object[] params, String[] signature) throws MBeanException, ReflectionException {
        if ("start".equals(actionName)) {
            start();
            return null;
        }
        throw new ReflectionException(new NoSuchMethodException(actionName));
    }

    @Override
    public MBeanInfo getMBeanInfo() {
        return new MBeanInfo(
                HtmlAdapterServer.class.getName(),
                "Test HtmlAdapterServer",
                null,
                null,
                new MBeanOperationInfo[] {
                        new MBeanOperationInfo("start", "Starts the adapter", null, "void", MBeanOperationInfo.ACTION)
                },
                null
        );
    }
}
