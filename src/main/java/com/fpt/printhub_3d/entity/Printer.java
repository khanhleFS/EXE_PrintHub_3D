package com.fpt.printhub_3d.entity;
import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;
@Entity @Table(name="printers") @Getter @Setter
public class Printer {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(nullable=false,length=100) private String name;
    @Column(nullable=false,length=20) private String type;
    @Column(nullable=false,length=20) private String status;
    @Column(length=500) private String note;
}
