package com.resumebuddy.repository;

import com.resumebuddy.model.UserCredit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;

@Repository
public interface UserCreditRepository extends JpaRepository<UserCredit, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT uc FROM UserCredit uc WHERE uc.userId = :userId")
    Optional<UserCredit> findByIdWithLock(String userId);
}
