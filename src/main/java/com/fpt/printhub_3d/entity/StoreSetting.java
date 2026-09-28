package com.fpt.printhub_3d.entity;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name="store_settings") @Getter @Setter
public class StoreSetting {
    @Id @Column(name="setting_key",length=100) private String key;
    @Column(name="setting_value",length=1000) private String value;
}
