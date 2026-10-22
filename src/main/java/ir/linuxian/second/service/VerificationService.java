package ir.linuxian.second.service;

import ir.linuxian.second.bot.MessengerService;
import ir.linuxian.second.entities.Role;
import ir.linuxian.second.entities.RoleName;
import ir.linuxian.second.entities.User;
import ir.linuxian.second.entities.auth.VerificationAttempt;
import ir.linuxian.second.repos.RoleRepo;
import ir.linuxian.second.repos.UserRepo;
import ir.linuxian.second.repos.VerificationAttemptRepo;
import ir.linuxian.second.utils.RandomSix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class VerificationService {

    private static final Logger log = LoggerFactory.getLogger(VerificationService.class);

    private static final Duration CODE_TTL = Duration.ofMinutes(10);
    private static final int MAX_ATTEMPTS = 5;
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final VerificationAttemptRepo attemptRepo;
    private final UserRepo userRepo;
    private final RoleRepo roleRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final SettingService settingService;
    private final MessengerService messengerService;
    // Self-proxy: internal calls must go through it, otherwise @Transactional
    // (esp. REQUIRES_NEW) is silently bypassed by direct this-calls.
    private final VerificationService self;

    public VerificationService(VerificationAttemptRepo attemptRepo,
                               UserRepo userRepo,
                               RoleRepo roleRepo,
                               PasswordEncoder passwordEncoder,
                               JwtService jwtService,
                               SettingService settingService,
                               MessengerService messengerService,
                               @org.springframework.context.annotation.Lazy VerificationService self) {
        this.attemptRepo = attemptRepo;
        this.userRepo = userRepo;
        this.roleRepo = roleRepo;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.settingService = settingService;
        this.messengerService = messengerService;
        this.self = self;
    }

    public String defaultDial() {
        String configured = setting("defaultCountry", Countries.FALLBACK_DEFAULT_DIAL).trim();
        if (configured.isEmpty()) {
            return Countries.FALLBACK_DEFAULT_DIAL;
        }
        // Admin may store "+98" or "98".
        if (!configured.startsWith("+")) {
            configured = "+" + configured;
        }
        return configured;
    }

    // ---------- step 1: request a code ----------

    @Transactional
    public Map<String, Object> requestCode(String username, String firstName, String lastName,
                                           String password, String confirmPassword,
                                           String channel, String email, String dial, String phone) {
        String name = username == null ? "" : username.trim();
        if (name.length() < 3) {
            throw new IllegalArgumentException("Username must be at least 3 characters");
        }
        String first = firstName == null ? "" : firstName.trim();
        String last = lastName == null ? "" : lastName.trim();
        if (first.isEmpty() || last.isEmpty()) {
            throw new IllegalArgumentException("First name and last name are required");
        }
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters");
        }
        if (confirmPassword == null || !password.equals(confirmPassword)) {
            throw new IllegalArgumentException("Passwords do not match");
        }
        if (userRepo.findByUsername(name).isPresent()) {
            throw new IllegalStateException("Username already exists");
        }

        String ch = channel == null ? "" : channel.trim().toUpperCase();
        if (!ch.equals("EMAIL") && !ch.equals("PHONE")) {
            throw new IllegalArgumentException("Channel must be email or phone");
        }

        String contact;
        if (ch.equals("EMAIL")) {
            String em = email == null ? "" : email.trim().toLowerCase();
            if (!EMAIL_PATTERN.matcher(em).matches()) {
                throw new IllegalArgumentException("Enter a valid email address");
            }
            contact = em;
        } else {
            String countryDial = dial == null || dial.isBlank() ? defaultDial() : dial.trim();
            if (!countryDial.startsWith("+")) {
                countryDial = "+" + countryDial;
            }
            if (!Countries.isKnownDial(countryDial)) {
                throw new IllegalArgumentException("Choose a country from the list");
            }
            String digits = phone == null ? "" : phone.replaceAll("\\D", "");
            if (digits.length() < 4 || digits.length() > 15) {
                throw new IllegalArgumentException("Enter a valid phone number");
            }
            contact = countryDial + digits;
        }

        String code = String.valueOf(new RandomSix().genRandom());

        VerificationAttempt attempt = new VerificationAttempt();
        attempt.setRequestId(UUID.randomUUID().toString());
        attempt.setChannel(ch);
        attempt.setContact(contact);
        attempt.setUsername(name);
        attempt.setFirstName(first);
        attempt.setLastName(last);
        attempt.setPasswordHash(passwordEncoder.encode(password));
        // The code itself is hashed too: pending codes are never stored raw.
        attempt.setCode(passwordEncoder.encode(code));
        attempt.setExpiresAt(Instant.now().plus(CODE_TTL));
        attemptRepo.save(attempt);

        boolean delivered = ch.equals("EMAIL") ? sendEmailCode(contact, code) : sendTelegramCode(contact, code);
        if (!delivered) {
            // Fail closed: no code leaves the server unless it actually reached the user.
            throw new DeliveryException(ch.equals("EMAIL")
                    ? "Email service is not available. Please try again later."
                    : "Could not reach that number on Telegram. Check the number or try again later.");
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("requestId", attempt.getRequestId());
        out.put("channel", ch.toLowerCase());
        out.put("contact", maskContact(ch, contact));
        out.put("expiresInSeconds", CODE_TTL.toSeconds());
        return out;
    }

    // ---------- step 2: verify the code ----------

    public Map<String, Object> verifyCode(String requestId, String code) {
        VerificationAttempt attempt = attemptRepo.findByRequestId(requestId == null ? "" : requestId.trim())
                .orElseThrow(() -> new IllegalArgumentException("Verification request not found"));
        if (attempt.isConsumed()) {
            throw new IllegalStateException("This code has already been used");
        }
        if (Instant.now().isAfter(attempt.getExpiresAt())) {
            throw new IllegalStateException("The code has expired. Request a new one.");
        }
        if (attempt.getAttempts() >= MAX_ATTEMPTS) {
            throw new IllegalStateException("Too many wrong attempts. Request a new code.");
        }
        String given = code == null ? "" : code.trim();
        if (!passwordEncoder.matches(given, attempt.getCode())) {
            // Separate transaction: the mismatch below throws (which would roll
            // back this increment if it shared the transaction).
            self.recordFailedAttempt(attempt.getId());
            throw new IllegalArgumentException("Wrong code. Try again.");
        }
        return self.completeVerification(attempt.getId());
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void recordFailedAttempt(Long attemptId) {
        attemptRepo.findById(attemptId).ifPresent(a -> {
            a.setAttempts(a.getAttempts() + 1);
            attemptRepo.save(a);
        });
    }

    @Transactional
    public Map<String, Object> completeVerification(Long attemptId) {
        VerificationAttempt attempt = attemptRepo.findById(attemptId)
                .orElseThrow(() -> new IllegalArgumentException("Verification request not found"));
        if (attempt.isConsumed()) {
            throw new IllegalStateException("This code has already been used");
        }
        if (Instant.now().isAfter(attempt.getExpiresAt())) {
            throw new IllegalStateException("The code has expired. Request a new one.");
        }
        if (userRepo.findByUsername(attempt.getUsername()).isPresent()) {
            throw new IllegalStateException("Username already exists");
        }

        Role userRole = null;
        for (Role r : roleRepo.findAll()) {
            if (r.getRoleName() == RoleName.ROLE_USER) {
                userRole = r;
                break;
            }
        }
        if (userRole == null) {
            userRole = roleRepo.save(new Role(RoleName.ROLE_USER));
        }

        User user = new User(attempt.getUsername(), attempt.getPasswordHash(), "user", List.of(userRole));
        user.setFirstName(attempt.getFirstName() == null ? "" : attempt.getFirstName());
        user.setLastName(attempt.getLastName() == null ? "" : attempt.getLastName());
        if (attempt.getChannel().equals("EMAIL")) {
            user.setEmail(attempt.getContact());
        } else {
            user.setPhone(attempt.getContact());
        }
        userRepo.save(user);

        attempt.setConsumed(true);
        attemptRepo.save(attempt);

        String token = jwtService.getToken(user.getUsername(), JwtService.REMEMBER_EXPIRATION_TIME);
        return Map.of("token", token, "username", user.getUsername(), "role", "user",
                "remember", true, "expiresInSeconds", JwtService.REMEMBER_EXPIRATION_TIME / 1000);
    }

    // ---------- sensitive admin operations (backup / restore) ----------

    /**
     * Step 1: send a one-time code to the admin's OWN registered contact.
     * purpose is "BACKUP" or "RESTORE" and binds the code to that operation.
     */
    @Transactional
    public Map<String, Object> requestAdminCode(String username, String purpose, String channel) {
        String kind = purpose == null ? "" : purpose.trim().toUpperCase();
        if (!kind.equals("BACKUP") && !kind.equals("RESTORE")) {
            throw new IllegalArgumentException("Operation must be backup or restore");
        }
        User user = userRepo.findByUsername(username == null ? "" : username.trim())
                .orElseThrow(() -> new IllegalArgumentException("Login required"));
        String ch = channel == null ? "" : channel.trim().toUpperCase();
        if (!ch.equals("EMAIL") && !ch.equals("PHONE")) {
            throw new IllegalArgumentException("Channel must be email or phone");
        }
        String contact = ch.equals("EMAIL") ? user.getEmail() : user.getPhone();
        contact = contact == null ? "" : contact.trim();
        if (contact.isEmpty()) {
            throw new IllegalStateException(ch.equals("EMAIL")
                    ? "Your profile has no email address. Set it in My account first."
                    : "Your profile has no phone number. Set it in My account first.");
        }

        String code = String.valueOf(new RandomSix().genRandom());

        VerificationAttempt attempt = new VerificationAttempt();
        attempt.setRequestId(UUID.randomUUID().toString());
        attempt.setChannel(ch);
        attempt.setContact(ch.equals("EMAIL") ? contact.toLowerCase() : contact);
        attempt.setUsername(user.getUsername());
        attempt.setPasswordHash("");
        attempt.setPurpose("ADMIN_" + kind);
        attempt.setCode(passwordEncoder.encode(code));
        attempt.setExpiresAt(Instant.now().plus(CODE_TTL));
        attemptRepo.save(attempt);

        boolean delivered = ch.equals("EMAIL") ? sendEmailCode(attempt.getContact(), code) : sendTelegramCode(contact, code);
        if (!delivered) {
            throw new DeliveryException(ch.equals("EMAIL")
                    ? "Email service is not available. Please try again later."
                    : "Could not reach that number on Telegram. Check the number or try again later.");
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("requestId", attempt.getRequestId());
        out.put("channel", ch.toLowerCase());
        out.put("contact", maskContact(ch, contact));
        out.put("expiresInSeconds", CODE_TTL.toSeconds());
        return out;
    }

    /** Step 2: check the code and burn it (single use). */
    public void verifyAdminCode(String username, String purpose, String requestId, String code) {
        VerificationAttempt attempt = attemptRepo.findByRequestId(requestId == null ? "" : requestId.trim())
                .orElseThrow(() -> new IllegalArgumentException("Verification request not found"));
        String kind = purpose == null ? "" : purpose.trim().toUpperCase();
        if (username == null || !username.equals(attempt.getUsername())
                || !("ADMIN_" + kind).equals(attempt.getPurpose())) {
            throw new IllegalArgumentException("Verification request not found");
        }
        if (attempt.isConsumed()) {
            throw new IllegalStateException("This code has already been used");
        }
        if (Instant.now().isAfter(attempt.getExpiresAt())) {
            throw new IllegalStateException("The code has expired. Request a new one.");
        }
        if (attempt.getAttempts() >= MAX_ATTEMPTS) {
            throw new IllegalStateException("Too many wrong attempts. Request a new code.");
        }
        String given = code == null ? "" : code.trim();
        if (!passwordEncoder.matches(given, attempt.getCode())) {
            self.recordFailedAttempt(attempt.getId());
            throw new IllegalArgumentException("Wrong code. Try again.");
        }
        self.completeAdminCode(attempt.getId(), username, "ADMIN_" + kind);
    }

    @Transactional
    public void completeAdminCode(Long attemptId, String username, String purpose) {
        VerificationAttempt attempt = attemptRepo.findById(attemptId)
                .orElseThrow(() -> new IllegalArgumentException("Verification request not found"));
        if (attempt.isConsumed() || Instant.now().isAfter(attempt.getExpiresAt())
                || !username.equals(attempt.getUsername()) || !purpose.equals(attempt.getPurpose())) {
            throw new IllegalStateException("This code is no longer valid. Request a new one.");
        }
        attempt.setConsumed(true);
        attemptRepo.save(attempt);
    }

    // ---------- contact change (authenticated profile flow) ----------

    @Transactional
    public Map<String, Object> requestContactChange(String username, String channel,
                                                    String email, String dial, String phone) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Login required");
        }
        if (userRepo.findByUsername(username).isEmpty()) {
            throw new IllegalArgumentException("User not found");
        }
        String ch = channel == null ? "" : channel.trim().toUpperCase();
        if (!ch.equals("EMAIL") && !ch.equals("PHONE")) {
            throw new IllegalArgumentException("Channel must be email or phone");
        }
        String contact;
        if (ch.equals("EMAIL")) {
            String em = email == null ? "" : email.trim().toLowerCase();
            if (!EMAIL_PATTERN.matcher(em).matches()) {
                throw new IllegalArgumentException("Enter a valid email address");
            }
            contact = em;
        } else {
            String countryDial = dial == null || dial.isBlank() ? defaultDial() : dial.trim();
            if (!countryDial.startsWith("+")) {
                countryDial = "+" + countryDial;
            }
            if (!Countries.isKnownDial(countryDial)) {
                throw new IllegalArgumentException("Choose a country from the list");
            }
            String digits = phone == null ? "" : phone.replaceAll("\\D", "");
            if (digits.length() < 4 || digits.length() > 15) {
                throw new IllegalArgumentException("Enter a valid phone number");
            }
            contact = countryDial + digits;
        }

        String code = String.valueOf(new RandomSix().genRandom());

        VerificationAttempt attempt = new VerificationAttempt();
        attempt.setRequestId(UUID.randomUUID().toString());
        attempt.setChannel(ch);
        attempt.setContact(contact);
        attempt.setUsername(username);
        attempt.setPasswordHash("");
        attempt.setPurpose("CONTACT_CHANGE");
        attempt.setCode(passwordEncoder.encode(code));
        attempt.setExpiresAt(Instant.now().plus(CODE_TTL));
        attemptRepo.save(attempt);

        boolean delivered = ch.equals("EMAIL") ? sendEmailCode(contact, code) : sendTelegramCode(contact, code);
        if (!delivered) {
            throw new DeliveryException(ch.equals("EMAIL")
                    ? "Email service is not available. Please try again later."
                    : "Could not reach that number on Telegram. Check the number or try again later.");
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("requestId", attempt.getRequestId());
        out.put("channel", ch.toLowerCase());
        out.put("contact", maskContact(ch, contact));
        out.put("expiresInSeconds", CODE_TTL.toSeconds());
        return out;
    }

    public Map<String, Object> verifyContactChange(String username, String requestId, String code) {
        VerificationAttempt attempt = attemptRepo.findByRequestId(requestId == null ? "" : requestId.trim())
                .orElseThrow(() -> new IllegalArgumentException("Verification request not found"));
        if (username == null || !username.equals(attempt.getUsername())
                || !"CONTACT_CHANGE".equals(attempt.getPurpose())) {
            throw new IllegalArgumentException("Verification request not found");
        }
        if (attempt.isConsumed()) {
            throw new IllegalStateException("This code has already been used");
        }
        if (Instant.now().isAfter(attempt.getExpiresAt())) {
            throw new IllegalStateException("The code has expired. Request a new one.");
        }
        if (attempt.getAttempts() >= MAX_ATTEMPTS) {
            throw new IllegalStateException("Too many wrong attempts. Request a new code.");
        }
        String given = code == null ? "" : code.trim();
        if (!passwordEncoder.matches(given, attempt.getCode())) {
            self.recordFailedAttempt(attempt.getId());
            throw new IllegalArgumentException("Wrong code. Try again.");
        }
        return self.completeContactChange(attempt.getId());
    }

    @Transactional
    public Map<String, Object> completeContactChange(Long attemptId) {
        VerificationAttempt attempt = attemptRepo.findById(attemptId)
                .orElseThrow(() -> new IllegalArgumentException("Verification request not found"));
        if (attempt.isConsumed() || Instant.now().isAfter(attempt.getExpiresAt())) {
            throw new IllegalStateException("This code is no longer valid. Request a new one.");
        }
        User user = userRepo.findByUsername(attempt.getUsername())
                .orElseThrow(() -> new IllegalStateException("User not found"));
        if (attempt.getChannel().equals("EMAIL")) {
            user.setEmail(attempt.getContact());
        } else {
            user.setPhone(attempt.getContact());
        }
        userRepo.save(user);
        attempt.setConsumed(true);
        attemptRepo.save(attempt);
        return profileOf(user);
    }

    private Map<String, Object> profileOf(User user) {
        return Map.of(
                "username", user.getUsername(),
                "role", String.valueOf(user.getRole()),
                "roles", user.getRoles().stream().map(Role::toString).toList(),
                "firstName", user.getFirstName() == null ? "" : user.getFirstName(),
                "lastName", user.getLastName() == null ? "" : user.getLastName(),
                "email", user.getEmail() == null ? "" : user.getEmail(),
                "phone", user.getPhone() == null ? "" : user.getPhone());
    }

    // ---------- password reset (anonymous flow) ----------

    /**
     * Step 1 of the forgot-password flow. A code is sent to the email address
     * or phone number the user registered.
     *
     * <p>Unknown contacts get the same response as known ones: a dummy attempt
     * is stored and nothing is delivered. This keeps the endpoint from
     * revealing whether an email/phone belongs to an account.
     */
    @Transactional
    public Map<String, Object> requestPasswordReset(String channel,
                                                    String email, String dial, String phone) {
        String ch = passwordChannel(channel);
        String contact = passwordContact(ch, email, dial, phone);
        Optional<User> user = findUserByContact(ch, contact);
        String code = String.valueOf(new RandomSix().genRandom());

        VerificationAttempt attempt = new VerificationAttempt();
        attempt.setRequestId(UUID.randomUUID().toString());
        attempt.setChannel(ch);
        attempt.setContact(contact);
        attempt.setUsername(user.map(User::getUsername).orElse(""));
        attempt.setPasswordHash("");
        attempt.setPurpose("PASSWORD_RESET");
        attempt.setCode(passwordEncoder.encode(code));
        attempt.setExpiresAt(Instant.now().plus(CODE_TTL));
        attemptRepo.save(attempt);

        if (user.isPresent()) {
            boolean delivered = ch.equals("EMAIL")
                    ? sendEmailCode(contact, code)
                    : sendTelegramCode(contact, code);
            if (!delivered) {
                // Fail closed: never pretend a code was sent when delivery failed.
                throw new DeliveryException(ch.equals("EMAIL")
                        ? "Email service is not available. Please try again later."
                        : "Could not reach that number on Telegram. Check the number or try again later.");
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("requestId", attempt.getRequestId());
        out.put("channel", ch.toLowerCase(Locale.ROOT));
        out.put("contact", maskContact(ch, contact));
        out.put("expiresInSeconds", CODE_TTL.toSeconds());
        return out;
    }

    /** Step 2 of the forgot-password flow: verify the code and set a new password. */
    public void resetPassword(String requestId, String code,
                              String newPassword, String confirmPassword) {
        validateNewPassword(newPassword, confirmPassword);
        VerificationAttempt attempt = attemptRepo.findByRequestId(requestId == null ? "" : requestId.trim())
                .orElseThrow(() -> new IllegalArgumentException("Verification request not found"));
        if (!"PASSWORD_RESET".equals(attempt.getPurpose())) {
            throw new IllegalArgumentException("Verification request not found");
        }
        if (attempt.isConsumed()) {
            throw new IllegalStateException("This code has already been used");
        }
        if (Instant.now().isAfter(attempt.getExpiresAt())) {
            throw new IllegalStateException("The code has expired. Request a new one.");
        }
        if (attempt.getAttempts() >= MAX_ATTEMPTS) {
            throw new IllegalStateException("Too many wrong attempts. Request a new code.");
        }
        String given = code == null ? "" : code.trim();
        if (!passwordEncoder.matches(given, attempt.getCode())) {
            // Same pattern as the other code flows: increment in a separate
            // transaction so the failure survives the exception below.
            self.recordFailedAttempt(attempt.getId());
            throw new IllegalArgumentException("Wrong code. Try again.");
        }
        self.completePasswordReset(attempt.getId(), newPassword);
    }

    @Transactional
    public void completePasswordReset(Long attemptId, String newPassword) {
        VerificationAttempt attempt = attemptRepo.findById(attemptId)
                .orElseThrow(() -> new IllegalArgumentException("Verification request not found"));
        if (!"PASSWORD_RESET".equals(attempt.getPurpose()) || attempt.isConsumed()
                || Instant.now().isAfter(attempt.getExpiresAt())) {
            throw new IllegalStateException("This code is no longer valid. Request a new one.");
        }
        User user = userRepo.findByUsername(attempt.getUsername())
                .orElseThrow(() -> new IllegalStateException(
                        "This reset request is no longer valid. Request a new code."));
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepo.save(user);
        consumePasswordResetAttempts(user.getUsername());
    }

    /** Change the password of a signed-in user after checking the current one. */
    @Transactional
    public void changePassword(String username, String currentPassword,
                               String newPassword, String confirmPassword) {
        User user = userRepo.findByUsername(username == null ? "" : username.trim())
                .orElseThrow(() -> new IllegalStateException("User not found"));
        if (user.getPassword() == null
                || !passwordEncoder.matches(currentPassword == null ? "" : currentPassword, user.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        validateNewPassword(newPassword, confirmPassword);
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new IllegalArgumentException("New password must be different from the current one");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepo.save(user);
        consumePasswordResetAttempts(user.getUsername());
    }

    private void consumePasswordResetAttempts(String username) {
        if (username == null || username.isBlank()) {
            return;
        }
        for (VerificationAttempt other
                : attemptRepo.findByUsernameAndConsumedFalseOrderByCreatedAtDesc(username)) {
            if ("PASSWORD_RESET".equals(other.getPurpose()) && !other.isConsumed()) {
                other.setConsumed(true);
                attemptRepo.save(other);
            }
        }
    }

    private Optional<User> findUserByContact(String channel, String contact) {
        boolean byEmail = channel.equals("EMAIL");
        String wanted = byEmail ? emailKey(contact) : phoneKey(contact);
        if (wanted.isEmpty()) {
            return Optional.empty();
        }
        for (User user : userRepo.findAll()) {
            String stored = byEmail ? user.getEmail() : user.getPhone();
            String key = byEmail ? emailKey(stored) : phoneKey(stored);
            if (!key.isEmpty() && wanted.equals(key)) {
                return Optional.of(user);
            }
        }
        return Optional.empty();
    }

    private static String passwordChannel(String channel) {
        String ch = channel == null ? "" : channel.trim().toUpperCase(Locale.ROOT);
        if (!ch.equals("EMAIL") && !ch.equals("PHONE")) {
            throw new IllegalArgumentException("Channel must be email or phone");
        }
        return ch;
    }

    private String passwordContact(String ch, String email, String dial, String phone) {
        if (ch.equals("EMAIL")) {
            String em = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
            if (!EMAIL_PATTERN.matcher(em).matches()) {
                throw new IllegalArgumentException("Enter a valid email address");
            }
            return em;
        }
        String countryDial = dial == null || dial.isBlank() ? defaultDial() : dial.trim();
        if (!countryDial.startsWith("+")) {
            countryDial = "+" + countryDial;
        }
        if (!Countries.isKnownDial(countryDial)) {
            throw new IllegalArgumentException("Choose a country from the list");
        }
        String digits = phone == null ? "" : phone.replaceAll("\\D", "");
        if (digits.length() < 4 || digits.length() > 15) {
            throw new IllegalArgumentException("Enter a valid phone number");
        }
        return countryDial + digits;
    }

    private static void validateNewPassword(String newPassword, String confirmPassword) {
        if (newPassword == null || newPassword.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters");
        }
        if (confirmPassword == null || !newPassword.equals(confirmPassword)) {
            throw new IllegalArgumentException("Passwords do not match");
        }
    }

    private static String emailKey(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String phoneKey(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    // ---------- delivery ----------

    private boolean sendEmailCode(String to, String code) {
        String host = setting("smtp.host", "").trim();
        if (host.isEmpty()) {
            log.warn("SMTP is not configured (Admin → Settings → Registration). "
                    + "Verification code for {} was NOT emailed.", to);
            return false;
        }
        try {
            JavaMailSenderImpl sender = new JavaMailSenderImpl();
            sender.setHost(host);
            sender.setPort(Integer.parseInt(setting("smtp.port", "587").trim()));
            sender.setUsername(setting("smtp.username", "").trim());
            sender.setPassword(setting("smtp.password", ""));
            Properties props = sender.getJavaMailProperties();
            props.put("mail.transport.protocol", "smtp");
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", setting("smtp.tls", "true").trim());
            props.put("mail.smtp.connectiontimeout", "8000");
            props.put("mail.smtp.timeout", "8000");

            var message = sender.createMimeMessage();
            var helper = new MimeMessageHelper(message, false, "UTF-8");
            String from = setting("smtp.from", "").trim();
            helper.setFrom(from.isEmpty() ? setting("smtp.username", "").trim() : from);
            helper.setTo(to);
            helper.setSubject("Your verification code");
            helper.setText("Your verification code is: " + code
                    + "\nIt expires in 10 minutes.\nIf you did not request this, ignore this email.", false);
            sender.send(message);
            return true;
        } catch (Exception e) {
            log.warn("Could not email verification code to {}: {}", to, e.getMessage());
            return false;
        }
    }

    private boolean sendTelegramCode(String fullPhone, String code) {
        try {
            Map<String, Object> chat = messengerService.resolveTarget(fullPhone);
            Object rawId = chat.get("id");
            long chatId = rawId instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(rawId));
            messengerService.sendText(chatId,
                    "Your verification code is: " + code + "\nIt expires in 10 minutes.", 0);
            return true;
        } catch (Exception e) {
            log.warn("Could not Telegram verification code to {}: {}. "
                    + "The bot must be running/logged in and the number must have a Telegram account.",
                    fullPhone, e.getMessage());
            return false;
        }
    }

    private String setting(String key, String fallback) {
        try {
            String v = settingService.getAllAsMap().get(key);
            return v == null ? fallback : v;
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String maskContact(String channel, String contact) {
        if (channel.equals("EMAIL")) {
            int at = contact.indexOf('@');
            if (at <= 1) {
                return "***" + contact.substring(Math.max(0, at));
            }
            return contact.charAt(0) + "***" + contact.substring(at);
        }
        if (contact.length() <= 6) {
            return contact;
        }
        return contact.substring(0, 3) + " *** ***" + contact.substring(contact.length() - 4);
    }
}
