package com.archivalia.borrow.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.archivalia.borrow.entity.Borrow;
import com.archivalia.borrow.entity.BorrowStatus;

public interface BorrowRepository extends JpaRepository<Borrow, Long> {

    List<Borrow> findByUserId(Long userId);

    List<Borrow> findByStatus(BorrowStatus status);

    boolean existsByUserIdAndBookIdAndStatusIn(Long userId, Long bookId, Collection<BorrowStatus> statuses);

    List<Borrow> findByFinePendingTrue();
}
