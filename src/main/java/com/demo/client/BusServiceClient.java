package com.demo.client;

import com.demo.dto.ApiResponse;
import com.demo.dto.BusDetailDto;
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

    @GetMapping("/api/buses/internal/{id}/detail")
    ResponseEntity<ApiResponse<BusDetailDto>> getBusById(@PathVariable("id") int id);

    @Component
    class BusServiceFallback implements BusServiceClient {

        private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(BusServiceFallback.class);

        @Override
        public ResponseEntity<ApiResponse<BusDetailDto>> getBusById(int id) {
            log.warn("Fallback triggered for getBusById - busId "+id);
            return ResponseEntity.ok(ApiResponse.error("Bus service is temporarily unavailable"));
        }
    }
}