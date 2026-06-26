package com.fpt.printhub_3d.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "system_blacklist")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemBlacklist {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, columnDefinition = "uniqueidentifier")
    private UUID id;

    @Column(name = "cccd_number", unique = true, nullable = false, length = 20)
    private String cccdNumber;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "blacklisted_at", nullable = false)
    private Instant blacklistedAt;
}
