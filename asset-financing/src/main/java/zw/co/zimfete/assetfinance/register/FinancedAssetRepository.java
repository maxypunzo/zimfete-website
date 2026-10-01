package zw.co.zimfete.assetfinance.register;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FinancedAssetRepository extends JpaRepository<FinancedAsset, Long> {

    Optional<FinancedAsset> findByApplicationId(Long applicationId);

    @Query("""
            select f from FinancedAsset f
            where (:ownership is null or f.ownership = :ownership)
              and (:officeId is null or f.officeId = :officeId)
              and (:clientId is null or f.fineractClientId = :clientId)
            order by f.id desc""")
    List<FinancedAsset> search(@Param("ownership") Ownership ownership, @Param("officeId") Long officeId,
                               @Param("clientId") Long clientId);
}
