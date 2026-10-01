package zw.co.zimfete.assetfinance.web;

/** A request that is well-formed but breaks a ZimFete rule (wrong status, maker-checker, queue order...). */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
