package com.vietnam.pji.model.auth;

import java.io.Serializable;
import java.time.Instant;
import com.vietnam.pji.model.AbstractEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.vietnam.pji.constant.UserStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "users")
public class User extends AbstractEntity<Long> {

    @Column(name = "fullname")
    private String fullName;

    @Column(name = "phone")
    private String phone;

    @Column(name = "email")
    private String email;

    @Column(name = "department")
    private String department;

    @Column(name = "password")
    private String password;

    @Column(name = "avatar")
    private String avatar;

    @Column(name = "avatar_bucket", length = 200)
    private String avatarBucket;

    @Column(name = "avatar_object_key", length = 500)
    private String avatarObjectKey;

    @Column(name = "refresh_token", columnDefinition = "MEDIUMTEXT")
    private String refreshToken;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status")
    private UserStatus status;

    @Column(name = "last_login")
    private Instant lastLogin;

    // relationships: ---
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id")
    private Role role;

}
