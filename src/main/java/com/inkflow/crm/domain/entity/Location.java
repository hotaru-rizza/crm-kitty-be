package com.inkflow.crm.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "locations")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Location extends BaseEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "address", nullable = false)
    private String address;

    @Column(name = "phone")
    private String phone;

    @Column(name = "google_maps_link")
    private String googleMapsLink;

    @Column(name = "color", nullable = false)
    private String color;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "photo_url", columnDefinition = "TEXT")
    private String photoUrl;

    @Column(name = "navigation_instructions", columnDefinition = "TEXT")
    private String navigationInstructions;

    @Column(name = "telegram_contact")
    private String telegramContact;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "city")
    private String city;

    @ManyToMany(mappedBy = "locations")
    @Builder.Default
    private Set<Staff> staff = new HashSet<>();
}
