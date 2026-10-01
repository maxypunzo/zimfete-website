package zw.co.zimfete.assetfinance.register;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import zw.co.zimfete.assetfinance.security.AppUser;

@RestController
@RequestMapping("/api")
public class RegisterController {

    private final RegisterService service;

    public RegisterController(RegisterService service) {
        this.service = service;
    }

    public record DeliveryRequest(String serialNumber,
                                  @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
                                  @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
                                  @NotNull LocalDate deliveredOn, boolean memberAcknowledged, String notes) {
    }

    public record InspectionRequest(@NotNull LocalDate inspectedOn, @NotNull AssetCondition condition,
                                    @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
                                    @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude, String notes) {
    }

    public record RepossessRequest(@NotBlank String reason) {
    }

    public record AssetView(Long id, Long applicationId, String applicationReference, String memberName,
                            long clientId, long officeId, Long catalogueItemId, String assetName, String serialNumber,
                            BigDecimal latitude, BigDecimal longitude, LocalDate deliveredOn, String deliveredBy,
                            String notes, Ownership ownership, Instant ownershipChangedAt, String ownershipNote) {
        static AssetView of(FinancedAsset f) {
            var a = f.getApplication();
            return new AssetView(f.getId(), a.getId(), a.getReference(), a.getMemberName(), f.getFineractClientId(),
                    f.getOfficeId(), f.getCatalogueItem().getId(), f.getCatalogueItem().getName(),
                    f.getSerialNumber(), f.getLatitude(), f.getLongitude(), f.getDeliveredOn(), f.getDeliveredBy(),
                    f.getNotes(), f.getOwnership(), f.getOwnershipChangedAt(), f.getOwnershipNote());
        }
    }

    public record InspectionView(Long id, LocalDate inspectedOn, AssetCondition condition, BigDecimal latitude,
                                 BigDecimal longitude, String notes, String inspectedBy) {
        static InspectionView of(AssetInspection i) {
            return new InspectionView(i.getId(), i.getInspectedOn(), i.getCondition(), i.getLatitude(),
                    i.getLongitude(), i.getNotes(), i.getInspectedBy());
        }
    }

    public record PhotoView(Long id, PhotoKind kind, String originalName, String contentType, long sizeBytes,
                            String uploadedBy, Instant uploadedAt) {
        static PhotoView of(AssetPhoto p) {
            return new PhotoView(p.getId(), p.getKind(), p.getOriginalName(), p.getContentType(), p.getSizeBytes(),
                    p.getUploadedBy(), p.getUploadedAt());
        }
    }

    @PostMapping("/applications/{applicationId}/delivery")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('OFFICER')")
    public AssetView recordDelivery(@PathVariable long applicationId, @Valid @RequestBody DeliveryRequest r,
                                    @AuthenticationPrincipal AppUser user) {
        return AssetView.of(service.recordDelivery(applicationId, r.serialNumber(), r.latitude(), r.longitude(),
                r.deliveredOn(), r.memberAcknowledged(), r.notes(), user));
    }

    @GetMapping("/assets")
    public List<AssetView> search(@RequestParam(required = false) Ownership ownership,
                                  @RequestParam(required = false) Long officeId,
                                  @RequestParam(required = false) Long clientId,
                                  @AuthenticationPrincipal AppUser user) {
        return service.search(ownership, officeId, clientId, user).stream().map(AssetView::of).toList();
    }

    @GetMapping("/assets/{id}")
    public AssetView get(@PathVariable long id, @AuthenticationPrincipal AppUser user) {
        return AssetView.of(service.get(id, user));
    }

    @PostMapping("/assets/{id}/inspections")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('OFFICER')")
    public InspectionView inspect(@PathVariable long id, @Valid @RequestBody InspectionRequest r,
                                  @AuthenticationPrincipal AppUser user) {
        return InspectionView.of(service.inspect(id, r.inspectedOn(), r.condition(), r.latitude(), r.longitude(),
                r.notes(), user));
    }

    @GetMapping("/assets/{id}/inspections")
    public List<InspectionView> inspections(@PathVariable long id, @AuthenticationPrincipal AppUser user) {
        return service.inspections(id, user).stream().map(InspectionView::of).toList();
    }

    @PostMapping(path = "/assets/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('OFFICER')")
    public PhotoView addPhoto(@PathVariable long id, @RequestParam(defaultValue = "DELIVERY") PhotoKind kind,
                              @RequestPart("file") MultipartFile file, @AuthenticationPrincipal AppUser user) {
        return PhotoView.of(service.addPhoto(id, kind, file, user));
    }

    @GetMapping("/assets/{id}/photos")
    public List<PhotoView> photos(@PathVariable long id, @AuthenticationPrincipal AppUser user) {
        return service.photos(id, user).stream().map(PhotoView::of).toList();
    }

    @GetMapping("/assets/{id}/photos/{photoId}")
    public ResponseEntity<Resource> photo(@PathVariable long id, @PathVariable long photoId,
                                          @AuthenticationPrincipal AppUser user) {
        AssetPhoto photo = service.photo(id, photoId, user);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.getContentType()))
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=86400")
                .body(service.photoContent(photo));
    }

    @PostMapping("/assets/{id}/repossess")
    @PreAuthorize("hasRole('MANAGER')")
    public AssetView repossess(@PathVariable long id, @Valid @RequestBody RepossessRequest r,
                               @AuthenticationPrincipal AppUser user) {
        return AssetView.of(service.repossess(id, r.reason(), user));
    }
}
