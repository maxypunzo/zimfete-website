package zw.co.zimfete.assetfinance.application;

/** Messages to members. Replace the logging version with an SMS/WhatsApp gateway adapter. */
public interface MemberNotifier {

    void depositTargetReached(AssetApplication application);
}
