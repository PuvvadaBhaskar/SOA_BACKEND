package com.archivalia.fine.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.archivalia.fine.entity.Fine;
import com.archivalia.fine.entity.FineStatus;

public interface FineRepository extends JpaRepository<Fine, Long> {

    List<Fine> findByUserId(Long userId);

    List<Fine> findByBorrowId(Long borrowId);

    Optional<Fine> findFirstByBorrowIdOrderByIdAsc(Long borrowId);

    List<Fine> findByStatus(FineStatus status);
}
