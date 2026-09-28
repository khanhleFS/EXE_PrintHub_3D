package com.fpt.printhub_3d.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="file_assets") @Getter @Setter
public class FileAsset {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @ManyToOne(optional=false,fetch=FetchType.LAZY) private User owner;
    @Column(nullable=false,length=255) private String fileName;
    @Column(nullable=false,length=255) private String storageName;
    private long sizeBytes;
    private Instant createdAt;
    private boolean deleted;
}
