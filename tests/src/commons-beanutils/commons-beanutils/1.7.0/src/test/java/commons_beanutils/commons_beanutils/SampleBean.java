/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_beanutils.commons_beanutils;

import java.util.HashMap;
import java.util.Map;

public class SampleBean {
    private String name = "untitled";
    private int count;
    private double amount;
    private String[] tags = {"first", "second"};
    private SampleBean child;
    private final Map<String, String> labels = new HashMap<>();

    public SampleBean() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public String[] getTags() {
        return tags;
    }

    public void setTags(String[] tags) {
        this.tags = tags;
    }

    public SampleBean getChild() {
        return child;
    }

    public void setChild(SampleBean child) {
        this.child = child;
    }

    public String getLabel(String key) {
        return labels.get(key);
    }

    public void setLabel(String key, String value) {
        labels.put(key, value);
    }
}
