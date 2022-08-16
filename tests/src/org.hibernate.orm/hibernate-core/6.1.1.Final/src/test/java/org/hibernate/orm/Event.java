/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.hibernate.orm;

import java.util.Date;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.persistence.Transient;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.bytecode.enhance.internal.tracker.DirtyTracker;
import org.hibernate.bytecode.enhance.internal.tracker.NoopCollectionTracker;
import org.hibernate.bytecode.enhance.internal.tracker.SimpleFieldTracker;
import org.hibernate.bytecode.enhance.spi.CollectionTracker;
import org.hibernate.engine.spi.EntityEntry;
import org.hibernate.engine.spi.ManagedEntity;
import org.hibernate.engine.spi.PersistentAttributeInterceptable;
import org.hibernate.engine.spi.PersistentAttributeInterceptor;
import org.hibernate.engine.spi.SelfDirtinessTracker;
import org.hibernate.internal.util.collections.ArrayHelper;

/**
 * Source matching hibernate enhanced bytecode.
 * Alternatively use the <a href="https://github.com/hibernate/hibernate-orm/tree/main/tooling/hibernate-gradle-plugin#enhance">hibernate-gradle-plugin</a>.
 */
@Entity
@Table(name = "EVENTS")
public class Event implements ManagedEntity, PersistentAttributeInterceptable, SelfDirtinessTracker {

    @Id
    @GeneratedValue(generator = "increment")
    @GenericGenerator(name = "increment", strategy = "increment")
    private Long id;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "EVENT_DATE")
    private Date date;

    private String title;

    @Transient
    private transient EntityEntry $$_hibernate_entityEntryHolder;

    @Transient
    private transient ManagedEntity $$_hibernate_previousManagedEntity;

    @Transient
    private transient ManagedEntity $$_hibernate_nextManagedEntity;

    @Transient
    private transient PersistentAttributeInterceptor $$_hibernate_attributeInterceptor;

    @Transient
    private transient DirtyTracker $$_hibernate_tracker;

    protected Event() {
    }

    public Event(String title, Date date) {
        this.$$_hibernate_write_title(title);
        this.$$_hibernate_write_date(date);
    }

    public Long getId() {
        return this.$$_hibernate_read_id();
    }

    public void setId(Long id) {
        this.$$_hibernate_write_id(id);
    }

    public Date getDate() {
        return this.$$_hibernate_read_date();
    }

    public void setDate(Date date) {
        this.$$_hibernate_write_date(date);
    }

    public String getTitle() {
        return this.$$_hibernate_read_title();
    }

    public void setTitle(String title) {
        this.$$_hibernate_write_title(title);
    }

    public Object $$_hibernate_getEntityInstance() {
        return this;
    }

    public EntityEntry $$_hibernate_getEntityEntry() {
        return this.$$_hibernate_entityEntryHolder;
    }

    public void $$_hibernate_setEntityEntry(EntityEntry var1) {
        this.$$_hibernate_entityEntryHolder = var1;
    }

    public ManagedEntity $$_hibernate_getPreviousManagedEntity() {
        return this.$$_hibernate_previousManagedEntity;
    }

    public void $$_hibernate_setPreviousManagedEntity(ManagedEntity var1) {
        this.$$_hibernate_previousManagedEntity = var1;
    }

    public ManagedEntity $$_hibernate_getNextManagedEntity() {
        return this.$$_hibernate_nextManagedEntity;
    }

    public void $$_hibernate_setNextManagedEntity(ManagedEntity var1) {
        this.$$_hibernate_nextManagedEntity = var1;
    }

    public PersistentAttributeInterceptor $$_hibernate_getInterceptor() {
        return this.$$_hibernate_attributeInterceptor;
    }

    public void $$_hibernate_setInterceptor(PersistentAttributeInterceptor var1) {
        this.$$_hibernate_attributeInterceptor = var1;
    }

    public void $$_hibernate_trackChange(String var1) {
        if (this.$$_hibernate_tracker == null) {
            this.$$_hibernate_tracker = new SimpleFieldTracker();
        }

        this.$$_hibernate_tracker.add(var1);
    }

    public String[] $$_hibernate_getDirtyAttributes() {
        String[] var1 = null;
        var1 = this.$$_hibernate_tracker == null ? ArrayHelper.EMPTY_STRING_ARRAY : this.$$_hibernate_tracker.get();
        return var1;
    }

    public boolean $$_hibernate_hasDirtyAttributes() {
        boolean var1 = false;
        var1 = this.$$_hibernate_tracker != null && !this.$$_hibernate_tracker.isEmpty();
        return var1;
    }

    public void $$_hibernate_clearDirtyAttributes() {
        if (this.$$_hibernate_tracker != null) {
            this.$$_hibernate_tracker.clear();
        }
    }

    public void $$_hibernate_suspendDirtyTracking(boolean var1) {
        if (this.$$_hibernate_tracker == null) {
            this.$$_hibernate_tracker = new SimpleFieldTracker();
        }

        this.$$_hibernate_tracker.suspend(var1);
    }

    public CollectionTracker $$_hibernate_getCollectionTracker() {
        CollectionTracker var1 = null;
        var1 = NoopCollectionTracker.INSTANCE;
        return var1;
    }

    public Long $$_hibernate_read_id() {
        if (this.$$_hibernate_getInterceptor() != null) {
            this.id = (Long) this.$$_hibernate_getInterceptor().readObject(this, "id", this.id);
        }

        return this.id;
    }

    public void $$_hibernate_write_id(Long var1) {
        if (this.$$_hibernate_getInterceptor() != null) {
            this.id = (Long) this.$$_hibernate_getInterceptor().writeObject(this, "id", this.id, var1);
        } else {
            this.id = (Long) var1;
        }
    }

    public Date $$_hibernate_read_date() {
        if (this.$$_hibernate_getInterceptor() != null) {
            this.date = (Date) this.$$_hibernate_getInterceptor().readObject(this, "date", this.date);
        }

        return this.date;
    }

    public void $$_hibernate_write_date(Date var1) {
        if (!Objects.deepEquals(var1, this.date)) {
            this.$$_hibernate_trackChange("date");
        }

        if (this.$$_hibernate_getInterceptor() != null) {
            this.date = (Date) this.$$_hibernate_getInterceptor().writeObject(this, "date", this.date, var1);
        } else {
            this.date = (Date) var1;
        }
    }

    public String $$_hibernate_read_title() {
        if (this.$$_hibernate_getInterceptor() != null) {
            this.title = (String) this.$$_hibernate_getInterceptor().readObject(this, "title", this.title);
        }

        return this.title;
    }

    public void $$_hibernate_write_title(String var1) {
        if (!Objects.deepEquals(var1, this.title)) {
            this.$$_hibernate_trackChange("title");
        }

        if (this.$$_hibernate_getInterceptor() != null) {
            this.title = (String) this.$$_hibernate_getInterceptor().writeObject(this, "title", this.title, var1);
        } else {
            this.title = (String) var1;
        }
    }
}
