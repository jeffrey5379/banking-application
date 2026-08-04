package com.bankapp.service;

import com.bankapp.dto.AdminDtos.AdminUserSummary;
import com.bankapp.exception.ResourceNotFoundException;
import com.bankapp.model.Role;
import com.bankapp.model.User;
import com.bankapp.model.kyc.KycProfile;
import com.bankapp.model.kyc.KycStatus;
import com.bankapp.repository.KycProfileRepository;
import com.bankapp.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private KycProfileRepository kycProfileRepository;

    @InjectMocks
    private AdminService adminService;

    private User alice;
    private User admin;
    private User bank;

    @BeforeEach
    void setUp() {
        alice = new User();
        alice.setId(1L);
        alice.setUsername("alice");
        alice.setEmail("alice@example.com");
        alice.setRole(Role.USER);

        admin = new User();
        admin.setId(2L);
        admin.setUsername("admin");
        admin.setEmail("admin@example.com");
        admin.setRole(Role.ADMIN);

        bank = new User();
        bank.setId(3L);
        bank.setUsername("bank");
        bank.setEmail("bank@example.com");
        bank.setRole(Role.SYSTEM);
    }

    // ── listUsers ────────────────────────────────────────────────────────────

    @Test
    void listUsers_excludesAdminAndSystemAccounts() {
        when(userRepository.findAll()).thenReturn(List.of(alice, admin, bank));
        when(kycProfileRepository.findByUserId(1L)).thenReturn(Optional.empty());

        List<AdminUserSummary> result = adminService.listUsers();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).username()).isEqualTo("alice");
    }

    @Test
    void listUsers_noKycProfileYet_reportsNotStarted() {
        when(userRepository.findAll()).thenReturn(List.of(alice));
        when(kycProfileRepository.findByUserId(1L)).thenReturn(Optional.empty());

        List<AdminUserSummary> result = adminService.listUsers();

        assertThat(result.get(0).kycStatus()).isEqualTo(KycStatus.NOT_STARTED);
    }

    @Test
    void listUsers_withKycProfile_reportsItsStatus() {
        KycProfile profile = new KycProfile();
        profile.setStatus(KycStatus.PENDING);
        when(userRepository.findAll()).thenReturn(List.of(alice));
        when(kycProfileRepository.findByUserId(1L)).thenReturn(Optional.of(profile));

        List<AdminUserSummary> result = adminService.listUsers();

        assertThat(result.get(0).kycStatus()).isEqualTo(KycStatus.PENDING);
    }

    @Test
    void listUsers_reflectsActiveFlagFromEnabled() {
        alice.setEnabled(false);
        when(userRepository.findAll()).thenReturn(List.of(alice));
        when(kycProfileRepository.findByUserId(1L)).thenReturn(Optional.empty());

        List<AdminUserSummary> result = adminService.listUsers();

        assertThat(result.get(0).active()).isFalse();
    }

    // ── setActive ────────────────────────────────────────────────────────────

    @Test
    void setActive_customerAccount_updatesEnabledFlag() {
        when(userRepository.findByPublicId(alice.getPublicId())).thenReturn(Optional.of(alice));
        when(kycProfileRepository.findByUserId(1L)).thenReturn(Optional.empty());

        AdminUserSummary result = adminService.setActive(alice.getPublicId(), false);

        assertThat(alice.isEnabled()).isFalse();
        assertThat(result.active()).isFalse();
        verify(userRepository).save(alice);
    }

    @Test
    void setActive_adminAccount_throwsIllegalArgumentException() {
        when(userRepository.findByPublicId(admin.getPublicId())).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> adminService.setActive(admin.getPublicId(), false))
                .isInstanceOf(IllegalArgumentException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void setActive_systemAccount_throwsIllegalArgumentException() {
        when(userRepository.findByPublicId(bank.getPublicId())).thenReturn(Optional.of(bank));

        assertThatThrownBy(() -> adminService.setActive(bank.getPublicId(), true))
                .isInstanceOf(IllegalArgumentException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void setActive_unknownUser_throwsResourceNotFoundException() {
        UUID unknown = UUID.randomUUID();
        when(userRepository.findByPublicId(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.setActive(unknown, true))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
