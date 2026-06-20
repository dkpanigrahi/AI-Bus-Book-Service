package com.demo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "booking")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    private Integer seatNo;

    private String passengerName;

    private LocalDate bookingDate;

    private boolean booked;

    private boolean inProcess;

    private LocalDateTime expirationTime;

    // FK references — actual entities live in their respective services
    private Integer userId;

    private Integer busId;
}