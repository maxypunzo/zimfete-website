package zw.co.zimfete.assetfinance.catalogue;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetCatalogueItemRepository extends JpaRepository<AssetCatalogueItem, Long> {

    boolean existsByCode(String code);

    List<AssetCatalogueItem> findByActiveTrueOrderByName();
}
