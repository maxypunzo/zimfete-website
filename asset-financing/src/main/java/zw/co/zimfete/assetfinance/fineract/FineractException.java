package zw.co.zimfete.assetfinance.fineract;

/** Fineract rejected a request or could not be reached. */
public class FineractException extends RuntimeException {

    public FineractException(String message) {
        super(message);
    }

    public FineractException(String message, Throwable cause) {
        super(message, cause);
    }
}
