package com.lin.spring.security.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.Data;

@Data
@Entity
public class Role {
    @Id
    @GeneratedValue
    private Long id;

    private String name; // e.g., ROLE_USER, ROLE_ADMIN
}
