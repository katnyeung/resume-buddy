package com.resumebuddy.repository;

import com.resumebuddy.model.CreditTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CreditTransactionRepository extends JpaRepository<CreditTransaction, Long> {

    List<CreditTransaction> findByUserIdOrderByCreatedAtDesc(String userId);

    Page<CreditTransaction> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    List<CreditTransaction> findByJobId(String jobId);
}
