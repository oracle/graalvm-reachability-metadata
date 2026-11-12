/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_reactive.hibernate_reactive_core.entity;

import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Proxy;

import java.util.ArrayList;
import java.util.List;

@Entity
@Proxy(lazy = false)
public class BatchRecord {
    @Id
    public Long id;

    public String label;

    @ElementCollection
    @BatchSize(size = 4)
    public List<String> tags = new ArrayList<>();

    public BatchRecord() {
    }

    public BatchRecord(long id, String label, String... tags) {
        this.id = id;
        this.label = label;
        this.tags.addAll(List.of(tags));
    }
}
