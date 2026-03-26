package com.lin.spring.elasticsearch.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@Entity
@Table(name = "products")
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(length = 1000)
    private String description;

    private Double price;

    private String category;

    private String tags; // JSON string storage

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Helper methods for tags conversion
    public List<String> getTagsAsList() {
        try {
            if (tags == null || tags.isEmpty()) {
                return List.of();
            }
            return new ObjectMapper().readValue(tags, List.class);
        } catch (Exception e) {
            return List.of();
        }
    }

    public void setTagsFromList(List<String> tagList) {
        try {
            if (tagList == null || tagList.isEmpty()) {
                this.tags = "[]";
            } else {
                this.tags = new ObjectMapper().writeValueAsString(tagList);
            }
        } catch (Exception e) {
            this.tags = "[]";
        }
    }
}
