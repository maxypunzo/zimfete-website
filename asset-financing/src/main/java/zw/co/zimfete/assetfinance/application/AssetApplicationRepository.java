package zw.co.zimfete.assetfinance.application;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssetApplicationRepository extends JpaRepository<AssetApplication, Long> {

    Optional<AssetApplication> findByFineractSavingsAccountId(long savingsAccountId);

    Optional<AssetApplication> findByFineractLoanId(long loanId);

    List<AssetApplication> findByStatusIn(Collection<ApplicationStatus> statuses);

    List<AssetApplication> findByStatus(ApplicationStatus status, Sort sort);

    @Query("""
            select a from AssetApplication a
            where (:status is null or a.status = :status)
              and (:officeId is null or a.officeId = :officeId)
              and (:clientId is null or a.fineractClientId = :clientId)
            order by a.id desc""")
    List<AssetApplication> search(@Param("status") ApplicationStatus status, @Param("officeId") Long officeId,
                                  @Param("clientId") Long clientId);
}
