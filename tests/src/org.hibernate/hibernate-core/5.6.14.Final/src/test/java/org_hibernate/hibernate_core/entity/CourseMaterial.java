package org_hibernate.hibernate_core.entity;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import java.util.Objects;

@Entity
@Table(name="COURSE_MATERIAL")
public final class CourseMaterial {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private String url;

    @OneToOne(optional = false)
    @JoinColumn(name = "COURSE_ID", referencedColumnName = "ID")
    private Course course;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Course getCourse() {
        return course;
    }

    public void setCourse(Course course) {
        this.course = course;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CourseMaterial that = (CourseMaterial) o;
        return Objects.equals(getUrl(), that.getUrl());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getUrl());
    }
}
