package com.library.service;

import com.library.dto.RegisterRequest;
import com.library.entity.Role;
import com.library.entity.User;
import com.library.exception.BusinessException;
import com.library.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;

@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {
    private final UserRepository userRepo;
    private final PasswordEncoder encoder;

    /** Self-registration always creates a USER; the role is never taken from the request. */
    @Transactional
    public User register(RegisterRequest req) {
        String email = req.getEmail().trim().toLowerCase();
        if (userRepo.existsByEmail(email)) {
            throw new BusinessException("This email is already registered");
        }
        User u = new User();
        u.setName(req.getName().trim());
        u.setEmail(email);
        u.setPassword(encoder.encode(req.getPassword()));
        u.setRole(Role.USER);
        return userRepo.save(u);
    }

    public User getByEmail(String email) {
        return userRepo.findByEmail(email.toLowerCase())
                .orElseThrow(() -> BusinessException.notFound("User not found"));
    }

    public Long currentId(Principal principal) {
        return getByEmail(principal.getName()).getId();
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User u = userRepo.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new UsernameNotFoundException("No user " + email));
        return org.springframework.security.core.userdetails.User.withUsername(u.getEmail())
                .password(u.getPassword())
                .roles(u.getRole().name())
                .build();
    }
}
