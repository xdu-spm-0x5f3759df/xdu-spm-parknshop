package io.spm.parknshop.user.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import java.util.Date;

import static javax.persistence.GenerationType.IDENTITY;

/**
 * User entity.
 *
 * @author Eric Zhao
 */
@Entity
@JsonIgnoreProperties(value = {"password"}, allowSetters = true)
public class User {
  @Id
  @GeneratedValue(strategy = IDENTITY)
  private Long id;

  private Date gmtCreate;
  private Date gmtModified;

  private String username;
  private String password;

  private String email;
  private String telephone;

  private int userType;
  private int userStatus;

  public static User deletedUser(Long id) {
    return new User().setUsername("(Deleted Account)").setId(id);
  }

  public Long getId() {
    return id;
  }

  public User setId(Long id) {
    this.id = id;
    return this;
  }

  public Date getGmtCreate() {
    return gmtCreate;
  }

  public User setGmtCreate(Date gmtCreate) {
    this.gmtCreate = gmtCreate;
    return this;
  }

  public Date getGmtModified() {
    return gmtModified;
  }

  public User setGmtModified(Date gmtModified) {
    this.gmtModified = gmtModified;
    return this;
  }

  public String getUsername() {
    return username;
  }

  public User setUsername(String username) {
    this.username = username;
    return this;
  }

  public String getPassword() {
    return password;
  }

  public User setPassword(String password) {
    this.password = password;
    return this;
  }

  public String getEmail() {
    return email;
  }

  public User setEmail(String email) {
    this.email = email;
    return this;
  }

  public String getTelephone() {
    return telephone;
  }

  public User setTelephone(String telephone) {
    this.telephone = telephone;
    return this;
  }

  public int getUserType() {
    return userType;
  }

  public User setUserType(int userType) {
    this.userType = userType;
    return this;
  }

  public int getUserStatus() {
    return userStatus;
  }

  public User setUserStatus(int userStatus) {
    this.userStatus = userStatus;
    return this;
  }

  @Override
  public String toString() {
    return "User{" +
      "id=" + id +
      ", gmtCreate=" + gmtCreate +
      ", gmtModified=" + gmtModified +
      ", username='" + username + '\'' +
      ", password='" + password + '\'' +
      ", email='" + email + '\'' +
      ", telephone='" + telephone + '\'' +
      ", userType=" + userType +
      ", userStatus=" + userStatus +
      '}';
  }
}
