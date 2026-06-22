package com.fpt.printhub_3d.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "categories")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
public class Category {
    @Id
    @Column(name = "category_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long categoryId;   // ✅ đổi String -> Long

    @Column(name = "category_name")
    private String categoryName;

    @Column(name = "description")
    private String description;

}
