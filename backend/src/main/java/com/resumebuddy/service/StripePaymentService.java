package com.resumebuddy.service;

import com.resumebuddy.model.PaymentPackage;
import com.resumebuddy.model.PaymentRecord;
import com.resumebuddy.repository.PaymentRecordRepository;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class StripePaymentService {

    private final PaymentRecordRepository paymentRecordRepository;
    private final UserCreditService userCreditService;

    @Value("${stripe.api-key:}")
    private String stripeApiKey;

    @Value("${stripe.webhook-secret:}")
    private String webhookSecret;

    @Value("${stripe.success-url:}")
    private String successUrl;

    @Value("${stripe.cancel-url:}")
    private String cancelUrl;

    @Value("${stripe.price-id-200:}")
    private String priceId200;

    @Value("${stripe.price-id-500:}")
    private String priceId500;

    @Value("${stripe.price-id-1000:}")
    private String priceId1000;

    @PostConstruct
    public void init() {
        Stripe.apiKey = stripeApiKey;
    }

    /**
     * Create Stripe Checkout Session for credit purchase
     */
    @Transactional
    public Session createCheckoutSession(String userId, String packageId) throws StripeException {
        log.info("Creating Stripe checkout session for user {} with package {}", userId, packageId);

        PaymentPackage pkg = PaymentPackage.fromPackageId(packageId);
        String priceId = getPriceId(pkg);

        // Create metadata to track user and package
        Map<String, String> metadata = new HashMap<>();
        metadata.put("userId", userId);
        metadata.put("packageId", packageId);
        metadata.put("credits", pkg.getCredits().toString());

        // Build Stripe Checkout Session
        SessionCreateParams params = SessionCreateParams.builder()
            .setMode(SessionCreateParams.Mode.PAYMENT)
            .setSuccessUrl(successUrl)
            .setCancelUrl(cancelUrl)
            .addLineItem(
                SessionCreateParams.LineItem.builder()
                    .setPrice(priceId)
                    .setQuantity(1L)
                    .build()
            )
            .putAllMetadata(metadata)
            .build();

        Session session = Session.create(params);

        // Create pending payment record
        PaymentRecord paymentRecord = new PaymentRecord();
        paymentRecord.setUserId(userId);
        paymentRecord.setStripeSessionId(session.getId());
        paymentRecord.setAmountGbp(pkg.getPriceGbp());
        paymentRecord.setCreditsPurchased(pkg.getCredits());
        paymentRecord.setStatus(PaymentRecord.PaymentStatus.PENDING);
        paymentRecordRepository.save(paymentRecord);

        log.info("Created checkout session {} for user {}", session.getId(), userId);
        return session;
    }

    /**
     * Handle Stripe webhook events
     */
    @Transactional
    public void handleWebhook(String payload, String sigHeader) throws SignatureVerificationException {
        Event event = Webhook.constructEvent(payload, sigHeader, webhookSecret);

        log.info("Received Stripe webhook event: {}", event.getType());

        if ("checkout.session.completed".equals(event.getType())) {
            try {
                // Use EventDataObjectDeserializer for safe deserialization
                Session session = null;

                if (event.getDataObjectDeserializer().getObject().isPresent()) {
                    session = (Session) event.getDataObjectDeserializer().getObject().get();
                } else {
                    // Fallback to deserializeUnsafe if API versions don't match
                    session = (Session) event.getDataObjectDeserializer().deserializeUnsafe();
                }

                String sessionId = session.getId();
                log.info("Processing webhook for session {}", sessionId);

                // Retrieve full session from Stripe API to ensure we have payment_intent
                Session fullSession = Session.retrieve(sessionId);
                handleSuccessfulPayment(fullSession);

            } catch (StripeException e) {
                log.error("Failed to retrieve session from Stripe API", e);
                throw new RuntimeException("Failed to retrieve session from Stripe API", e);
            }
        }
    }

    /**
     * Process successful payment and grant credits
     */
    @Transactional
    public void handleSuccessfulPayment(Session session) {
        String sessionId = session.getId();
        log.info("Processing successful payment for session {}", sessionId);

        PaymentRecord paymentRecord = paymentRecordRepository.findByStripeSessionId(sessionId)
            .orElseThrow(() -> new RuntimeException("Payment record not found for session: " + sessionId));

        // Prevent duplicate processing
        if (paymentRecord.getStatus() == PaymentRecord.PaymentStatus.COMPLETED) {
            log.warn("Payment already processed for session {}", sessionId);
            return;
        }

        String userId = paymentRecord.getUserId();
        BigDecimal credits = paymentRecord.getCreditsPurchased();

        // Grant credits to user
        userCreditService.grantCredits(
            userId,
            credits,
            String.format("Stripe payment - £%.2f for %s credits",
                paymentRecord.getAmountGbp(), credits)
        );

        // Update payment record
        paymentRecord.setStatus(PaymentRecord.PaymentStatus.COMPLETED);
        paymentRecord.setStripePaymentIntentId(session.getPaymentIntent());
        paymentRecord.setCompletedAt(LocalDateTime.now());
        paymentRecordRepository.save(paymentRecord);

        log.info("Successfully granted {} credits to user {} for payment session {}",
            credits, userId, sessionId);
    }

    /**
     * Verify payment status for a session
     */
    @Transactional(readOnly = true)
    public PaymentRecord getPaymentBySessionId(String sessionId) {
        return paymentRecordRepository.findByStripeSessionId(sessionId)
            .orElseThrow(() -> new RuntimeException("Payment record not found for session: " + sessionId));
    }

    /**
     * Get Stripe Price ID for package
     */
    private String getPriceId(PaymentPackage pkg) {
        return switch (pkg) {
            case SMALL -> priceId200;
            case MEDIUM -> priceId500;
            case LARGE -> priceId1000;
        };
    }
}
