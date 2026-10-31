package com.finance.personal_finance_tracker.service;

import com.finance.personal_finance_tracker.model.User;
import com.finance.personal_finance_tracker.repository.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public User registerUser(User user) {
        String username = user.getUsername() == null ? "" : user.getUsername().trim();
        String email = user.getEmail() == null ? "" : user.getEmail().trim().toLowerCase(Locale.ROOT);
        String password = user.getPassword();
        if (username.isBlank() || username.length() > 80 || email.isBlank() || email.length() > 254) {
            throw new IllegalArgumentException("Enter a valid username and email address.");
        }
        if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalArgumentException("Enter a valid email address.");
        }
        if (password == null || password.length() < 10
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Password must be at least 10 characters and use no more than 72 UTF-8 bytes.");
        }
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new IllegalArgumentException("That username is already in use.");
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("That email address is already registered.");
        }
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        return userRepository.save(user);
    }

    @Transactional
    public Optional<User> loginUser(String username, String password) {
        if (username == null || password == null) {
            return Optional.empty();
        }
        Optional<User> matchingUser = userRepository.findByUsernameIgnoreCase(username.trim());
        if (matchingUser.isEmpty()) {
            return Optional.empty();
        }
        User user = matchingUser.get();
        String storedPassword = user.getPassword();
        boolean encoded = storedPassword.startsWith("$2a$") || storedPassword.startsWith("$2b$")
                || storedPassword.startsWith("$2y$");
        boolean matches = encoded ? passwordEncoder.matches(password, storedPassword) : storedPassword.equals(password);
        if (!matches) {
            return Optional.empty();
        }
        if (!encoded) {
            user.setPassword(passwordEncoder.encode(password));
        }
        return Optional.of(user);
    }

    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }
}
