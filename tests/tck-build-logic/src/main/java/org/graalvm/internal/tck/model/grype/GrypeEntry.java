/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.model.grype;

import java.util.List;
import java.util.Map;

/*
 * JSON model for metadata/index.json.
 */
public class GrypeEntry {
    public String id;
    public String dataSource;
    public String namespace;
    public String severity;
    public List<String> urls;
    public String description;
    public List<Cvss> cvss;
    public List<KnownExploited> knownExploited;
    public List<Epss> epss;
    public List<Cwe> cwes;
    public Fix fix;
    public List<Object> advisories; // unknown structure
    public Double risk;
}

class Cwe {
    public String cve;
    public String cwe;
    public String source;
    public String type;
}

class Cvss {
    public String source;
    public String type;
    public String version;
    public String vector;
    public Metrics metrics;
    public Map<String, Object> vendorMetadata;
}

class Metrics {
    public Double baseScore;
    public Double exploitabilityScore;
    public Double impactScore;
}

class KnownExploited {
    public String cve;
    public String vendorProject;
    public String product;
    public String dateAdded;
    public String requiredAction;
    public String dueDate;
    public String knownRansomwareCampaignUse;
    public String notes;
    public List<String> urls;
    public List<String> cwes;
}

class Epss {
    public String cve;
    public Double epss;
    public Double percentile;
    public String date;
}

class Fix {
    public List<String> versions;
    public String state;
    public List<AvailableFix> available;
}

class AvailableFix {
    public String version;
    public String date;
    public String kind;
}
