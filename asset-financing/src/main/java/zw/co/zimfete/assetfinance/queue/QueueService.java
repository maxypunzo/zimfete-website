package zw.co.zimfete.assetfinance.queue;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import zw.co.zimfete.assetfinance.application.ApplicationStatus;
import zw.co.zimfete.assetfinance.application.AssetApplication;
import zw.co.zimfete.assetfinance.application.AssetApplicationRepository;
import zw.co.zimfete.assetfinance.config.ZimfeteProperties;
import zw.co.zimfete.assetfinance.config.ZimfeteProperties.QueueRule;

/**
 * The waiting queue of members who reached their deposit target, and the cash ZimFete needs to
 * serve it. Positions are always ranked across ALL locations (one SACCO, one pool of money);
 * an officer simply sees the entries for their own location.
 *
 * Cash needed per asset is the FULL asset cost: the supplier is paid in full, and the member's
 * deposit is already in the pooled money (much of it lent out as soft loans).
 */
@Service
@Transactional(readOnly = true)
public class QueueService {

    private final AssetApplicationRepository applications;
    private final QueueRule rule;
    private final Clock clock;

    public QueueService(AssetApplicationRepository applications, ZimfeteProperties properties, Clock clock) {
        this.applications = applications;
        this.rule = properties.policy().queueRule();
        this.clock = clock;
    }

    public record QueueEntry(int position, long applicationId, String reference, long clientId, String memberName,
                             long officeId, String assetName, BigDecimal assetCost, String currency,
                             BigDecimal depositedAmount, LocalDate openedOn, LocalDate qualifiedOn, long daysWaiting,
                             BigDecimal cumulativeCashNeeded) {
    }

    public List<AssetApplication> rankedQueue() {
        Sort sort = switch (rule) {
            case FIRST_QUALIFIED -> Sort.by("qualifiedAt", "id");
            case FIRST_OPENED -> Sort.by("openedOn", "id");
        };
        return applications.findByStatus(ApplicationStatus.QUALIFIED, sort);
    }

    public boolean isFirstInQueue(AssetApplication app) {
        List<AssetApplication> queue = rankedQueue();
        return !queue.isEmpty() && queue.getFirst().getId().equals(app.getId());
    }

    public List<QueueEntry> queue(Long officeId) {
        LocalDate today = LocalDate.now(clock);
        ZoneId zone = clock.getZone();
        List<QueueEntry> entries = new ArrayList<>();
        BigDecimal cumulative = BigDecimal.ZERO;
        int position = 0;
        for (AssetApplication a : rankedQueue()) {
            position++;
            cumulative = cumulative.add(a.getAssetCost());
            if (officeId != null && a.getOfficeId() != officeId) {
                continue;
            }
            LocalDate qualifiedOn = a.getQualifiedAt() == null ? null : a.getQualifiedAt().atZone(zone).toLocalDate();
            entries.add(new QueueEntry(position, a.getId(), a.getReference(), a.getFineractClientId(),
                    a.getMemberName(), a.getOfficeId(), a.getCatalogueItem().getName(), a.getAssetCost(),
                    a.getCurrency(), a.getDepositedAmount(), a.getOpenedOn(), qualifiedOn,
                    qualifiedOn == null ? 0 : ChronoUnit.DAYS.between(qualifiedOn, today), cumulative));
        }
        return entries;
    }

    public record OfficeForecast(long officeId, int inQueue, BigDecimal queueCash, int expectedToQualify,
                                 BigDecimal expectedCash) {
    }

    public record Forecast(LocalDate asOf, LocalDate horizon, Map<String, CurrencyForecast> byCurrency) {
    }

    public record CurrencyForecast(int inQueue, BigDecimal cashNeededNow, int expectedToQualify,
                                   BigDecimal expectedCash, BigDecimal totalCashNeeded,
                                   List<OfficeForecast> byOffice) {
    }

    /**
     * Cash needed to serve everyone already in the queue, plus members expected to reach their
     * target within {@code days} at their recent deposit pace. Compare this with available cash
     * before approving new soft loans.
     */
    public Forecast forecast(int days, Long officeId) {
        LocalDate today = LocalDate.now(clock);
        LocalDate horizon = today.plusDays(days);
        Map<String, Map<Long, Tally>> tallies = new TreeMap<>();

        for (AssetApplication a : applications.findByStatusIn(List.of(ApplicationStatus.QUALIFIED,
                ApplicationStatus.SAVING))) {
            if (officeId != null && a.getOfficeId() != officeId) {
                continue;
            }
            boolean queued = a.getStatus() == ApplicationStatus.QUALIFIED;
            boolean expected = !queued && a.getEstimatedTargetDate() != null
                    && !a.getEstimatedTargetDate().isAfter(horizon);
            if (queued || expected) {
                tallies.computeIfAbsent(a.getCurrency(), c -> new TreeMap<>())
                        .computeIfAbsent(a.getOfficeId(), o -> new Tally())
                        .add(queued, a.getAssetCost());
            }
        }

        Map<String, CurrencyForecast> byCurrency = new TreeMap<>();
        tallies.forEach((currency, offices) -> {
            Tally total = new Tally();
            List<OfficeForecast> rows = new ArrayList<>();
            offices.forEach((office, t) -> {
                rows.add(new OfficeForecast(office, t.queued, t.queueCash, t.expected, t.expectedCash));
                total.merge(t);
            });
            byCurrency.put(currency, new CurrencyForecast(total.queued, total.queueCash, total.expected,
                    total.expectedCash, total.queueCash.add(total.expectedCash), rows));
        });
        return new Forecast(today, horizon, byCurrency);
    }

    private static final class Tally {
        int queued;
        BigDecimal queueCash = BigDecimal.ZERO;
        int expected;
        BigDecimal expectedCash = BigDecimal.ZERO;

        void add(boolean inQueue, BigDecimal cost) {
            if (inQueue) {
                queued++;
                queueCash = queueCash.add(cost);
            } else {
                expected++;
                expectedCash = expectedCash.add(cost);
            }
        }

        void merge(Tally other) {
            queued += other.queued;
            queueCash = queueCash.add(other.queueCash);
            expected += other.expected;
            expectedCash = expectedCash.add(other.expectedCash);
        }
    }
}
