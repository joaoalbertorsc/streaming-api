package com.ebac.streaming.profile;

import com.ebac.streaming.account.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProfileRepository extends JpaRepository<Profile, UUID> {

    long countByAccountAndDeletedAtIsNull(Account account);

    List<Profile> findByAccountAndDeletedAtIsNull(Account account);

    Optional<Profile> findByIdAndAccount(UUID id, Account account);

    boolean existsByAccountAndNameAndDeletedAtIsNull(Account account, String name);

    List<Profile> findByDeletedAtIsNotNullAndDeletedAtBefore(Instant cutoff);

    List<Profile> findByAccountAndDeletedAtAfter(Account account, Instant cutoff);
}