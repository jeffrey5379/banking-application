package com.bankapp.repository;

import com.bankapp.model.Operation;
import com.bankapp.model.OperationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface OperationRepository extends JpaRepository<Operation, Long> {
    Page<Operation> findByAccountIdOrderByCreatedAtDesc(Long accountId, Pageable pageable);
    List<Operation> findByAccountIdOrderByCreatedAtAsc(Long accountId);

    @Query("SELECT COALESCE(SUM(o.amount), 0) " +
            "FROM Operation o " +
            "WHERE o.account.id = :accountId " +
            "AND o.type IN :types")
    BigDecimal sumAmountByAccountIdAndTypes(@Param("accountId") Long accountId,
                                            @Param("types") List<OperationType> types);
}
