package zw.co.zimfete.assetfinance.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingMemberNotifier implements MemberNotifier {

    private static final Logger log = LoggerFactory.getLogger(LoggingMemberNotifier.class);

    @Override
    public void depositTargetReached(AssetApplication a) {
        log.info("NOTIFY member {} (client {}): deposit target {} {} reached for {} ({}). You are now in the queue.",
                a.getMemberName(), a.getFineractClientId(), a.getCurrency(), a.getDepositTarget(),
                a.getCatalogueItem().getName(), a.getReference());
    }
}
