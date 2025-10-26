package com.resumebuddy.repository;

import com.resumebuddy.model.PaymentRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRecordRepository extends JpaRepository<PaymentRecord, Long> {

    Optional<PaymentRecord> findByStripeSessionId(String stripeSessionId);

    List<PaymentRecord> findByUserIdOrderByCreatedAtDesc(String userId);

    List<PaymentRecord> findByUserIdAndStatus(String userId, PaymentRecord.PaymentStatus status);
}
