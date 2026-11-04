package com.voyago.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "member")
public class Member {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, unique = true, length = 120)
    private String email;

    @Column(length = 30)
    private String phone;

    @Column(length = 255)
    private String address;

    private LocalDate birthday;

    @Column(length = 100)
    private String password;             // BCrypt；Google 使用者可為 null

    @Column(nullable = false, length = 20)
    private String role = "MEMBER";      // MEMBER / STAFF

    @Column(nullable = false, length = 20)
    private String provider = "LOCAL";   // LOCAL / GOOGLE

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    /**
     * 登入憑證版本：發出的 JWT 會帶著這個數字，每次請求都比對資料庫。
     * 「登出所有裝置」、帳號被接管後回收等情況把它 +1，舊的 token 立刻全部失效（JWT 本身無法撤銷，靠這個補上）。
     */
    @Column(name = "token_version", nullable = false)
    private Integer tokenVersion = 0;

    /** Email 是否確認過所有權：Google 登入（Google 已驗證）為 true；帳密註冊不寄驗證信，為 false。 */
    @Column(name = "email_verified", nullable = false)
    private Boolean emailVerified = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Integer getTokenVersion() { return tokenVersion; }
    public void setTokenVersion(Integer tokenVersion) { this.tokenVersion = tokenVersion; }
    public Boolean getEmailVerified() { return emailVerified; }
    public void setEmailVerified(Boolean emailVerified) { this.emailVerified = emailVerified; }
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public LocalDate getBirthday() { return birthday; }
    public void setBirthday(LocalDate birthday) { this.birthday = birthday; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
