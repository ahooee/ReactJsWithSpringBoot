package ir.linuxian.second.components;

import com.fasterxml.jackson.databind.JsonNode;
import ir.linuxian.second.config.ZarinpalProperties;
import ir.linuxian.second.entities.payment.PaymentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.HashMap;
import java.util.Map;

@Component
public class ZarinpalClient {
    private static final Logger log = LoggerFactory.getLogger(ZarinpalClient.class);

    private final RestClient rest;
    private final ZarinpalProperties props;

    public ZarinpalClient(ZarinpalProperties props) {
        this.props = props;
        String base = props.baseUrl() == null || props.baseUrl().isBlank()
                ? "https://payment.zarinpal.com/pg/v4/payment"
                : props.baseUrl().trim();
        this.rest = RestClient.builder().baseUrl(base).build();
    }

    /** Calls Zarinpal request.json, returns the authority code. */
    public String requestPayment(long amountRial, String description, String email, String mobile) {
        if (!props.isConfigured()) {
            throw new PaymentException("Zarinpal merchant-id is not configured. "
                    + "Set ZARINPAL_MERCHANT_ID env var or zarinpal.merchant-id property.");
        }
        if (amountRial <= 0) {
            throw new PaymentException("Invalid payment amount: " + amountRial);
        }
        String desc = description == null || description.isBlank() ? "Shop order" : description.trim();

        Map<String, Object> metadata = new HashMap<>();
        if (email != null && !email.isBlank()) metadata.put("email", email.trim());
        if (mobile != null && !mobile.isBlank()) metadata.put("mobile", mobile.trim());

        Map<String, Object> body = new HashMap<>();
        body.put("merchant_id", props.merchantId().trim());
        body.put("amount", amountRial);
        body.put("callback_url", props.callbackUrl());
        body.put("description", desc);
        body.put("metadata", metadata);

        JsonNode res;
        try {
            res = rest.post().uri("/request.json").body(body)
                    .retrieve().body(JsonNode.class);
        } catch (RestClientException e) {
            log.error("Zarinpal request failed (network): {}", e.getMessage());
            throw new PaymentException("Zarinpal request failed: " + e.getMessage(), e);
        }
        if (res == null) {
            throw new PaymentException("Zarinpal request failed: empty response");
        }

        JsonNode data = res.path("data");
        int code = data.path("code").asInt(-1);
        if (code != 100) {
            String err = res.path("errors").path("message").asText("");
            if (err.isBlank()) err = res.path("errors").toString();
            if (err.isBlank() || err.equals("null")) err = "unknown error (code=" + code + ")";
            log.warn("Zarinpal request declined: code={} errors={}", code, res.path("errors"));
            throw new PaymentException("Zarinpal request failed: " + err);
        }
        String authority = data.path("authority").asText(null);
        if (authority == null || authority.isBlank()) {
            throw new PaymentException("Zarinpal request failed: missing authority");
        }
        return authority;
    }

    public VerifyResult verify(String authority, long amountRial) {
        if (!props.isConfigured()) {
            throw new PaymentException("Zarinpal merchant-id is not configured.");
        }
        Map<String, Object> body = Map.of(
                "merchant_id", props.merchantId().trim(),
                "amount", amountRial,
                "authority", authority);

        JsonNode res;
        try {
            res = rest.post().uri("/verify.json").body(body)
                    .retrieve().body(JsonNode.class);
        } catch (RestClientException e) {
            log.error("Zarinpal verify failed (network): {}", e.getMessage());
            return new VerifyResult(false, 0L, null);
        }
        if (res == null) {
            return new VerifyResult(false, 0L, null);
        }

        JsonNode data = res.path("data");
        int code = data.path("code").asInt(-1);
        boolean ok = code == 100 || code == 101; // 101 = already verified
        if (!ok) {
            log.warn("Zarinpal verify declined: code={} body={}", code, res);
        }
        return new VerifyResult(ok, data.path("ref_id").asLong(0L),
                data.path("card_pan").asText(null));
    }

    public record VerifyResult(boolean success, long refId, String cardPan) {}
}
