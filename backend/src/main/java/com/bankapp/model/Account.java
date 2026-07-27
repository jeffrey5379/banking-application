package com.bankapp.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "accountNumber")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID publicId = UUID.randomUUID();

    @Column(nullable = false, unique = true)
    private String accountNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Currency currency;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance = BigDecimal.ZERO;

    // No FK - the owning user lives in a different service/database (identity-service). Both
    // fields come straight off the caller's validated JWT at creation time; ownerUsername is a
    // denormalized display copy (safe since usernames are immutable in this app), so transfer
    // descriptions and recipient lookups never need to call out to identity-service.
    @Column(nullable = false, updatable = false)
    private UUID ownerId;

    @Column(nullable = false, updatable = false)
    private String ownerUsername;

    @Version
    private Long version;

    @OneToMany(mappedBy = "account", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Operation> operations;

    @Override
    public String toString() {
        return "Account{accountNumber='" + accountNumber + "', currency=" + currency + ", balance=" + balance + "}";
    }
}
