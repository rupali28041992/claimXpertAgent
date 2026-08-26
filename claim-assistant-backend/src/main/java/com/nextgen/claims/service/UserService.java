package com.nextgen.claims.service;

import com.nextgen.claims.dto.UserLoginResponse;
import com.nextgen.claims.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public UserLoginResponse login(String userId, String password) {
        return userRepository.findByUserIdAndPassword(userId, password)
                .map(u -> UserLoginResponse.builder()
                        .userId(u.getUserId())
                        .name(u.getName())
                        .email(u.getEmail())
                        .customerId(u.getCustomerId())
                        .success(true)
                        .message("Login successful")
                        .build())
                .orElseGet(() -> UserLoginResponse.builder()
                        .success(false)
                        .message("Invalid user ID or password. Please try again.")
                        .build());
    }
}
