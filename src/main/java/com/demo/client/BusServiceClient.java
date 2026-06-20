package com.demo.client;

import com.demo.dto.ApiResponse;
import com.demo.dto.BusDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
    name = "bus-service",
    fallback = BusServiceClient.BusServiceFallback.class
)
public interface BusServiceClient {

    @GetMapping("/api/buses/{id}")
    ResponseEntity<ApiResponse<BusDto>> getBusById(@PathVariable("id") int id);

    @Component
    @Slf4j
    class BusServiceFallback implements BusServiceClient {

        @Override
        public ResponseEntity<ApiResponse<BusDto>> getBusById(int id) {
            log.warn("Fallback triggered for getBusById - busId: {}", id);
            return ResponseEntity.ok(ApiResponse.error("Bus service is temporarily unavailable"));
        }
    }
}