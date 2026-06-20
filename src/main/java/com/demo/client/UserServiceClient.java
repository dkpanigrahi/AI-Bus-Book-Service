package com.demo.client;

import com.demo.dto.ApiResponse;
import com.demo.dto.UserDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
    name = "user-service",
    fallback = UserServiceClient.UserServiceFallback.class
)
public interface UserServiceClient {

    @GetMapping("/api/users/{id}")
    ResponseEntity<ApiResponse<UserDto>> getUserById(@PathVariable("id") int id);

    @GetMapping("/api/users/email/{email}")
    ResponseEntity<ApiResponse<UserDto>> getUserByEmail(@PathVariable("email") String email);

    @Component
    @Slf4j
    class UserServiceFallback implements UserServiceClient {

        @Override
        public ResponseEntity<ApiResponse<UserDto>> getUserById(int id) {
            log.warn("Fallback triggered for getUserById - userId: {}", id);
            return ResponseEntity.ok(ApiResponse.error("User service is temporarily unavailable"));
        }

        @Override
        public ResponseEntity<ApiResponse<UserDto>> getUserByEmail(String email) {
            log.warn("Fallback triggered for getUserByEmail - email: {}", email);
            return ResponseEntity.ok(ApiResponse.error("User service is temporarily unavailable"));
        }
    }
}