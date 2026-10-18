package com.library.config;

import com.library.entity.FineSetting;
import com.library.entity.Role;
import com.library.entity.User;
import com.library.repository.FineSettingRepository;
import com.library.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {
    private final UserRepository userRepo;
    private final FineSettingRepository settingRepo;
    private final PasswordEncoder encoder;

    @Value("${app.librarian.email:librarian@library.com}")
    private String librarianEmail;

    @Value("${app.librarian.password:Librarian@123}")
    private String librarianPassword;

    @Override
    public void run(String... args) {
        if (userRepo.countByRole(Role.LIBRARIAN) == 0) {
            User u = new User();
            u.setName("Librarian");
            u.setEmail(librarianEmail.toLowerCase());
            u.setPassword(encoder.encode(librarianPassword));
            u.setRole(Role.LIBRARIAN);
            userRepo.save(u);
            log.info("Seeded librarian account: {}", librarianEmail);
        }
        if (settingRepo.count() == 0) {
            FineSetting s = new FineSetting();
            s.setRatePerDay(new BigDecimal("5.00"));
            settingRepo.save(s);
        }
    }
}
