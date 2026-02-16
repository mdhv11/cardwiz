package com.cardwiz.userservice.services;

import com.cardwiz.userservice.dtos.Auth.AuthenticationRequest;
import com.cardwiz.userservice.dtos.Auth.AuthenticationResponse;
import com.cardwiz.userservice.dtos.Auth.RegisterRequest;
import com.cardwiz.userservice.models.User;
import com.cardwiz.userservice.repositories.UserRepository;
import com.cardwiz.userservice.models.UserRole;
import com.cardwiz.userservice.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final UserService userService;

    public AuthenticationResponse register(RegisterRequest request) {
        // 1. Check if user exists
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already registered");
        }

        // 2. Create User
        var user = new User();
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(UserRole.USER);

        userRepository.save(user);

        // 3. Generate Token with userId claim
        var claims = new java.util.HashMap<String, Object>();
        claims.put("userId", String.valueOf(user.getId()));
        var jwtToken = jwtService.generateToken(claims, user);

        return AuthenticationResponse.builder()
                .token(jwtToken)
                .userId(String.valueOf(user.getId()))
                .email(user.getEmail())
                .user(userService.getUserProfile(user.getId()))
                .build();
    }

    public AuthenticationResponse authenticate(AuthenticationRequest request) {
        // 1. Authenticate using Spring Security Manager
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()));

        // 2. Fetch User
        var user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Invalid credentials"));

        // 3. Generate Token with userId claim
        var claims = new java.util.HashMap<String, Object>();
        claims.put("userId", String.valueOf(user.getId()));
        var jwtToken = jwtService.generateToken(claims, user);

        return AuthenticationResponse.builder()
                .token(jwtToken)
                .userId(String.valueOf(user.getId()))
                .email(user.getEmail())
                .user(userService.getUserProfile(user.getId()))
                .build();
    }
}
