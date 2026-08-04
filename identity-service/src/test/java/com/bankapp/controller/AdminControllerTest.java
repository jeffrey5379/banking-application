package com.bankapp.controller;

import com.bankapp.dto.AdminDtos.AdminUserSummary;
import com.bankapp.exception.GlobalExceptionHandler;
import com.bankapp.model.kyc.KycStatus;
import com.bankapp.service.AdminService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = AdminController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
@Import(GlobalExceptionHandler.class)
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminService adminService;

    @Autowired
    private ObjectMapper objectMapper;

    private static final UUID ALICE_ID = UUID.randomUUID();

    @Test
    void listUsers_returnsTheServicesList() throws Exception {
        AdminUserSummary alice = new AdminUserSummary(ALICE_ID, "alice", "alice@example.com", true, KycStatus.VERIFIED);
        when(adminService.listUsers()).thenReturn(List.of(alice));

        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("alice"))
                .andExpect(jsonPath("$[0].kycStatus").value("VERIFIED"));
    }

    @Test
    void setActive_deactivate_returnsUpdatedSummary() throws Exception {
        AdminUserSummary updated = new AdminUserSummary(ALICE_ID, "alice", "alice@example.com", false, KycStatus.VERIFIED);
        when(adminService.setActive(eq(ALICE_ID), eq(false))).thenReturn(updated);

        mockMvc.perform(post("/api/admin/users/{id}/active", ALICE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("active", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }
}
