package zw.co.zimfete.assetfinance.fineract.demo;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import zw.co.zimfete.assetfinance.application.ApplicationService;
import zw.co.zimfete.assetfinance.application.AssetApplication;
import zw.co.zimfete.assetfinance.catalogue.AssetCatalogueItem;
import zw.co.zimfete.assetfinance.catalogue.AssetCatalogueItemRepository;
import zw.co.zimfete.assetfinance.catalogue.AssetCategory;
import zw.co.zimfete.assetfinance.catalogue.Supplier;
import zw.co.zimfete.assetfinance.catalogue.SupplierRepository;
import zw.co.zimfete.assetfinance.fineract.FineractClient;
import zw.co.zimfete.assetfinance.security.AppUser;
import zw.co.zimfete.assetfinance.security.Role;

/** Demo mode only: fills an empty system with a catalogue and members at different stages. */
@Component
@ConditionalOnProperty(name = "zimfete.fineract.mode", havingValue = "demo")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final AssetCatalogueItemRepository items;
    private final SupplierRepository suppliers;
    private final ApplicationService applications;
    private final DemoFineractClient fineract;
    private final Clock clock;

    public DemoDataSeeder(AssetCatalogueItemRepository items, SupplierRepository suppliers,
                          ApplicationService applications, FineractClient fineract, Clock clock) {
        this.items = items;
        this.suppliers = suppliers;
        this.applications = applications;
        this.fineract = (DemoFineractClient) fineract;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (items.count() > 0) {
            // Catalogue is kept in the database, but the pretend Fineract starts empty on every restart.
            log.info("Demo data already present; not seeding again");
            return;
        }
        AssetCatalogueItem borehole = items.save(new AssetCatalogueItem("BH-40", "Borehole, 40 m, solar pump",
                AssetCategory.BOREHOLE, "Drilling, casing, 1.1 kW solar pump and 5,000 L tank", money("4000"), "USD"));
        AssetCatalogueItem tractor = items.save(new AssetCatalogueItem("TR-50", "Tractor, 50 HP, with plough",
                AssetCategory.TRACTOR, "Two-wheel drive, 3-disc plough", money("18500"), "USD"));
        AssetCatalogueItem drip = items.save(new AssetCatalogueItem("IR-1HA", "Drip irrigation kit, 1 ha",
                AssetCategory.IRRIGATION, "Lines, filters, fittings and installation", money("2600"), "USD"));
        items.save(new AssetCatalogueItem("SL-3KW", "Solar home system, 3 kW", AssetCategory.SOLAR,
                "Panels, inverter and batteries", money("3200"), "USD"));
        suppliers.save(new Supplier("Mashonaland Drilling (Pvt) Ltd", "+263 77 000 0001", null, "Marondera"));
        suppliers.save(new Supplier("AgriMech Tractors", "+263 77 000 0002", null, "Harare"));
        suppliers.save(new Supplier("Sun & Water Irrigation", "+263 77 000 0003", null, "Mutoko"));

        AppUser system = new AppUser("demo-seed", 1, null, EnumSet.copyOf(Role.ADMIN.withLowerRoles()));
        LocalDate today = LocalDate.now(clock);
        // member, asset, monthly deposits (oldest first)
        seed(system, 101, borehole, today, "600", "550", "500", "400");          // qualified
        seed(system, 102, borehole, today, "300", "300", "250");                 // saving
        seed(system, 104, drip, today, "450", "450", "400");                     // qualified
        seed(system, 106, borehole, today, "200", "200", "150", "200", "150");   // saving
        seed(system, 110, tractor, today, "1500", "1200", "1300", "1500");       // saving (large)
        seed(system, 112, drip, today, "650", "700");                            // qualified
        seed(system, 116, borehole, today, "100");                               // just started
        log.info("Demo data seeded");
    }

    private void seed(AppUser user, long clientId, AssetCatalogueItem item, LocalDate today, String... deposits) {
        AssetApplication app = applications.open(clientId, item.getId(), null, user);
        for (int i = 0; i < deposits.length; i++) {
            fineract.deposit(app.getFineractSavingsAccountId(), money(deposits[i]),
                    today.minusMonths(deposits.length - i).plusDays(clientId % 7));
        }
        applications.refresh(app.getId(), user);
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }
}
