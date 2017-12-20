package io.spm.parknshop.catalog.domain;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;

/**
 * @author Eric Zhao
 */
@Entity
public class Category {

  @Id
  @GeneratedValue
  private Long id;
  private String name;

  public Long getId() {
    return id;
  }

  public Category setId(Long id) {
    this.id = id;
    return this;
  }

  public String getName() {
    return name;
  }

  public Category setName(String name) {
    this.name = name;
    return this;
  }

  @Override
  public String toString() {
    return "Category{" +
      "id=" + id +
      ", name='" + name + '\'' +
      '}';
  }
}