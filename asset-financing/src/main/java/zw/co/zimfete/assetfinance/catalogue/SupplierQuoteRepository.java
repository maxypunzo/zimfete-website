package zw.co.zimfete.assetfinance.catalogue;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplierQuoteRepository extends JpaRepository<SupplierQuote, Long> {

    List<SupplierQuote> findByCatalogueItemIdOrderByQuoteDateDesc(Long catalogueItemId);
}
