package zw.co.zimfete.assetfinance.register;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetInspectionRepository extends JpaRepository<AssetInspection, Long> {

    List<AssetInspection> findByAssetIdOrderByInspectedOnDesc(Long assetId);
}
