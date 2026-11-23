package com.ticketbooking.repository;

import com.ticketbooking.model.Payment;
import com.ticketbooking.model.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByBookingId(Long bookingId);

    Optional<Payment> findByTransactionRef(String transactionRef);

    List<Payment> findByStatus(PaymentStatus status);
}
