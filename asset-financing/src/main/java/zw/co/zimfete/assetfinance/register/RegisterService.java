package zw.co.zimfete.assetfinance.register;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import zw.co.zimfete.assetfinance.application.ApplicationPaidOff;
import zw.co.zimfete.assetfinance.application.AssetApplication;
import zw.co.zimfete.assetfinance.application.AssetApplicationRepository;
import zw.co.zimfete.assetfinance.security.AppUser;
import zw.co.zimfete.assetfinance.security.OfficeAccess;
import zw.co.zimfete.assetfinance.web.BusinessRuleException;
import zw.co.zimfete.assetfinance.web.NotFoundException;

@Service
@Transactional
public class RegisterService {

    private final FinancedAssetRepository assets;
    private final AssetInspectionRepository inspections;
    private final AssetPhotoRepository photos;
    private final AssetApplicationRepository applications;
    private final PhotoStorage storage;
    private final OfficeAccess officeAccess;
    private final Clock clock;

    public RegisterService(FinancedAssetRepository assets, AssetInspectionRepository inspections,
                           AssetPhotoRepository photos, AssetApplicationRepository applications, PhotoStorage storage,
                           OfficeAccess officeAccess, Clock clock) {
        this.assets = assets;
        this.inspections = inspections;
        this.photos = photos;
        this.applications = applications;
        this.storage = storage;
        this.officeAccess = officeAccess;
        this.clock = clock;
    }

    /** The officer confirms in the field that the asset was delivered/installed. */
    public FinancedAsset recordDelivery(long applicationId, String serialNumber, BigDecimal latitude,
                                        BigDecimal longitude, LocalDate deliveredOn, boolean memberAcknowledged,
                                        String notes, AppUser user) {
        AssetApplication app = applications.findById(applicationId)
                .orElseThrow(() -> new NotFoundException("Application", applicationId));
        officeAccess.check(user, app.getOfficeId());
        if (!memberAcknowledged) {
            throw new BusinessRuleException("The member must acknowledge receipt before delivery is recorded");
        }
        if (deliveredOn.isAfter(LocalDate.now(clock))) {
            throw new BusinessRuleException("Delivery date cannot be in the future");
        }
        app.markDelivered();
        return assets.save(new FinancedAsset(app, serialNumber, latitude, longitude, deliveredOn, user.username(),
                true, notes, clock.instant()));
    }

    @Transactional(readOnly = true)
    public FinancedAsset get(long assetId, AppUser user) {
        FinancedAsset asset = assets.findById(assetId).orElseThrow(() -> new NotFoundException("Asset", assetId));
        officeAccess.check(user, asset.getOfficeId());
        return asset;
    }

    @Transactional(readOnly = true)
    public FinancedAsset forApplication(long applicationId, AppUser user) {
        FinancedAsset asset = assets.findByApplicationId(applicationId)
                .orElseThrow(() -> new NotFoundException("Asset for application", applicationId));
        officeAccess.check(user, asset.getOfficeId());
        return asset;
    }

    @Transactional(readOnly = true)
    public List<FinancedAsset> search(Ownership ownership, Long officeId, Long clientId, AppUser user) {
        return assets.search(ownership, officeAccess.scope(user, officeId), clientId);
    }

    public AssetInspection inspect(long assetId, LocalDate date, AssetCondition condition, BigDecimal latitude,
                                   BigDecimal longitude, String notes, AppUser user) {
        FinancedAsset asset = get(assetId, user);
        return inspections.save(new AssetInspection(asset, date, condition, latitude, longitude, notes,
                user.username()));
    }

    @Transactional(readOnly = true)
    public List<AssetInspection> inspections(long assetId, AppUser user) {
        get(assetId, user);
        return inspections.findByAssetIdOrderByInspectedOnDesc(assetId);
    }

    public AssetPhoto addPhoto(long assetId, PhotoKind kind, MultipartFile file, AppUser user) {
        FinancedAsset asset = get(assetId, user);
        String stored;
        try (InputStream in = file.getInputStream()) {
            stored = storage.store(assetId, file.getContentType(), in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return photos.save(new AssetPhoto(asset, kind, stored, file.getOriginalFilename(), file.getContentType(),
                file.getSize(), user.username(), clock.instant()));
    }

    @Transactional(readOnly = true)
    public List<AssetPhoto> photos(long assetId, AppUser user) {
        get(assetId, user);
        return photos.findByAssetIdOrderByUploadedAtDesc(assetId);
    }

    @Transactional(readOnly = true)
    public AssetPhoto photo(long assetId, long photoId, AppUser user) {
        get(assetId, user);
        return photos.findById(photoId).filter(p -> p.getAsset().getId().equals(assetId))
                .orElseThrow(() -> new NotFoundException("Photo", photoId));
    }

    public Resource photoContent(AssetPhoto photo) {
        return storage.load(photo.getStoredName());
    }

    /**
     * Records that ZimFete took the asset back. Recovering or writing off the loan balance is done
     * in Mifos according to the default policy.
     */
    public FinancedAsset repossess(long assetId, String reason, AppUser user) {
        FinancedAsset asset = get(assetId, user);
        asset.repossess(reason, clock.instant());
        return asset;
    }

    @EventListener
    public void onPaidOff(ApplicationPaidOff event) {
        assets.findByApplicationId(event.applicationId()).ifPresent(a -> a.transferToMember(clock.instant()));
    }
}
