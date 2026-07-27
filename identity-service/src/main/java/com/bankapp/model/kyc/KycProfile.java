package com.bankapp.model.kyc;

import com.bankapp.model.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "kyc_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "publicId")
public class KycProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID publicId = UUID.randomUUID();

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private KycStatus status = KycStatus.NOT_STARTED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private KycLevel level = KycLevel.NONE;

    private String firstName;
    private String lastName;

    @Enumerated(EnumType.STRING)
    private IssuingCountry issuingCountry;

    private String documentNumber;

    private LocalDateTime submittedAt;
    private LocalDateTime reviewedAt;
    private String rejectionReason;

    @OneToMany(mappedBy = "kycProfile", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<KycDocument> documents = new ArrayList<>();
}
