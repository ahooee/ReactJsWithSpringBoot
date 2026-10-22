package ir.linuxian.second.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Zarinpal v4 payment gateway settings.
 *
 * <pre>
 * zarinpal.merchant-id=${ZARINPAL_MERCHANT_ID:}
 * zarinpal.base-url=https://payment.zarinpal.com/pg/v4/payment
 * zarinpal.start-pay-url=https://payment.zarinpal.com/pg/StartPay/
 * zarinpal.callback-url=https://yourshop.com/api/payments/zarinpal/callback
 * zarinpal.frontend-base-url=http://localhost:5173
 * </pre>
 */
@Component
@ConfigurationProperties("zarinpal")
public class ZarinpalProperties {

    /** Merchant ID (UUID). Blank = payments disabled with a clear error. */
    private String merchantId = "";

    private String baseUrl = "https://payment.zarinpal.com/pg/v4/payment";

    private String startPayUrl = "https://payment.zarinpal.com/pg/StartPay/";

    private String callbackUrl = "http://localhost:8080/api/payments/zarinpal/callback";

    /** Where the browser is redirected after the gateway callback. */
    private String frontendBaseUrl = "http://localhost:5173";

    public String getMerchantId() { return merchantId; }
    public void setMerchantId(String merchantId) { this.merchantId = merchantId; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getStartPayUrl() { return startPayUrl; }
    public void setStartPayUrl(String startPayUrl) { this.startPayUrl = startPayUrl; }

    public String getCallbackUrl() { return callbackUrl; }
    public void setCallbackUrl(String callbackUrl) { this.callbackUrl = callbackUrl; }

    public String getFrontendBaseUrl() { return frontendBaseUrl; }
    public void setFrontendBaseUrl(String frontendBaseUrl) { this.frontendBaseUrl = frontendBaseUrl; }

    // Record-style accessors kept for existing call sites.
    public String merchantId() { return getMerchantId(); }
    public String baseUrl() { return getBaseUrl(); }
    public String startPayUrl() { return getStartPayUrl(); }
    public String callbackUrl() { return getCallbackUrl(); }
    public String frontendBaseUrl() { return getFrontendBaseUrl(); }

    public boolean isConfigured() {
        return merchantId != null && !merchantId.isBlank();
    }
}
