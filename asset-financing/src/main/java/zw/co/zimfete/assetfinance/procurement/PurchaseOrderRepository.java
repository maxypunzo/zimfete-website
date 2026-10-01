package zw.co.zimfete.assetfinance.procurement;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    boolean existsByApplicationIdAndStatusIn(Long applicationId, Collection<PurchaseOrderStatus> statuses);

    Optional<PurchaseOrder> findFirstByApplicationIdAndStatus(Long applicationId, PurchaseOrderStatus status);

    @Query("""
            select p from PurchaseOrder p
            where (:status is null or p.status = :status)
              and (:officeId is null or p.application.officeId = :officeId)
            order by p.id desc""")
    List<PurchaseOrder> search(@Param("status") PurchaseOrderStatus status, @Param("officeId") Long officeId);
}
