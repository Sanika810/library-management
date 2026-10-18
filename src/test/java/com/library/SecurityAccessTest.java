package com.library;

import com.library.entity.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SecurityAccessTest extends AbstractIntegrationTest {
    @Autowired MockMvc mvc;

    @Test
    void anonymousUserIsRedirectedToLogin() throws Exception {
        mvc.perform(get("/user/fines")).andExpect(status().is3xxRedirection());
    }

    @Test
    void userCannotOpenLibrarianPages() throws Exception {
        User u = newUser(Role.USER);
        mvc.perform(get("/librarian/fines").with(user(u.getEmail()).roles("USER"))).andExpect(status().isForbidden());
        mvc.perform(get("/librarian/fee-dashboard").with(user(u.getEmail()).roles("USER"))).andExpect(status().isForbidden());
    }

    @Test
    void userCannotPostAdjustmentOrRate() throws Exception {
        User u = newUser(Role.USER);
        Fine fine = newFine(u, "20.00");
        mvc.perform(post("/librarian/fines/" + fine.getId() + "/adjust").with(csrf())
                        .with(user(u.getEmail()).roles("USER"))
                        .param("amount", "5.00").param("reason", "self waiver"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/librarian/fines/rate").with(csrf())
                        .with(user(u.getEmail()).roles("USER")).param("ratePerDay", "0.01"))
                .andExpect(status().isForbidden());
        assertEquals(0, adjustmentRepo.count());
    }

    @Test
    void postWithoutCsrfTokenIsRejected() throws Exception {
        User u = newUser(Role.USER);
        Fine fine = newFine(u, "20.00");
        mvc.perform(post("/user/fines/" + fine.getId() + "/pay").with(user(u.getEmail()).roles("USER"))
                        .param("amount", "5.00").param("method", "UPI").param("idempotencyKey", "k1"))
                .andExpect(status().isForbidden());
        assertEquals(0, paymentRepo.count());
    }

    @Test
    void userCannotSeeOrPayAnotherUsersFine() throws Exception {
        User owner = newUser(Role.USER);
        User other = newUser(Role.USER);
        Fine fine = newFine(owner, "20.00");
        mvc.perform(get("/user/fines/" + fine.getId() + "/pay").with(user(other.getEmail()).roles("USER")))
                .andExpect(status().isNotFound());
        mvc.perform(post("/user/fines/" + fine.getId() + "/pay").with(csrf()).with(user(other.getEmail()).roles("USER"))
                        .param("amount", "5.00").param("method", "UPI").param("idempotencyKey", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
        assertEquals(0, paymentRepo.count());
    }

    @Test
    void userCannotOpenAnotherUsersPaymentResult() throws Exception {
        User owner = newUser(Role.USER);
        User other = newUser(Role.USER);
        Fine fine = newFine(owner, "20.00");
        Payment p = new Payment();
        p.setFine(fine);
        p.setAmount(bd("5.00"));
        p.setMethod(PaymentMethod.UPI);
        p.setStatus(PaymentStatus.SUCCESS);
        p.setTransactionReference("SIM-X1");
        p.setIdempotencyKey(UUID.randomUUID().toString());
        p = paymentRepo.save(p);
        mvc.perform(get("/user/payments/" + p.getId()).with(user(other.getEmail()).roles("USER")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/user/payments/" + p.getId()).with(user(owner.getEmail()).roles("USER")))
                .andExpect(status().isOk());
    }

    @Test
    void clientCannotForceSuccessStatusOrAmountOwner() throws Exception {
        when(processor.process(any())).thenReturn(new com.library.payment.PaymentResult(false, null));
        User u = newUser(Role.USER);
        Fine fine = newFine(u, "20.00");
        mvc.perform(post("/user/fines/" + fine.getId() + "/pay").with(csrf()).with(user(u.getEmail()).roles("USER"))
                        .param("amount", "5.00").param("method", "UPI").param("idempotencyKey", UUID.randomUUID().toString())
                        .param("status", "SUCCESS").param("transactionReference", "FORGED").param("userId", "1"))
                .andExpect(status().is3xxRedirection());
        Payment p = paymentRepo.findAll().get(0);
        assertEquals(PaymentStatus.FAILED, p.getStatus());
        assertEquals(null, p.getTransactionReference());
    }

    @Test
    void financialChangesAreNotPossibleThroughGet() throws Exception {
        User u = newUser(Role.USER);
        Fine fine = newFine(u, "20.00");
        mvc.perform(get("/user/fines/" + fine.getId() + "/pay").with(user(u.getEmail()).roles("USER")))
                .andExpect(status().isOk());
        assertEquals(0, paymentRepo.count());
    }

    @Test
    void userCanOpenOwnFinesPage() throws Exception {
        User u = newUser(Role.USER);
        newFine(u, "20.00");
        mvc.perform(get("/user/fines").with(user(u.getEmail()).roles("USER"))).andExpect(status().isOk());
    }
}
