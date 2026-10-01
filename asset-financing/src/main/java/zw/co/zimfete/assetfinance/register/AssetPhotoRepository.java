package zw.co.zimfete.assetfinance.register;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetPhotoRepository extends JpaRepository<AssetPhoto, Long> {

    List<AssetPhoto> findByAssetIdOrderByUploadedAtDesc(Long assetId);
}
