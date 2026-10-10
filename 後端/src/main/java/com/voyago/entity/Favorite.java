package com.voyago.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 會員收藏的行程。跟刪帳號 / 刪行程一起清掉沒關係（不像訂單要留審計紀錄），FK 用 CASCADE。 */
@Entity
@Table(name = "favorite", uniqueConstraints = @UniqueConstraint(columnNames = {"member_id", "route_id"}))
public class Favorite {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id")
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "route_id")
    private Route route;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Member getMember() { return member; }
    public void setMember(Member member) { this.member = member; }
    public Route getRoute() { return route; }
    public void setRoute(Route route) { this.route = route; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
