/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.boot.plugins.security.selfservice;

import ch.plaintext.SystemMailSender;
import ch.plaintext.boot.plugins.log.Log;
import ch.plaintext.boot.plugins.security.model.MyUserEntity;
import ch.plaintext.boot.plugins.security.persistence.MyUserRepository;
import ch.plaintext.mailtemplate.IMailTemplateProvider;
import ch.plaintext.mailtemplate.IMailTemplateProvider.RenderedMail;
import ch.plaintext.menuesteuerung.model.MandateMenuConfig;
import ch.plaintext.menuesteuerung.persistence.MandateMenuConfigRepository;
import ch.plaintext.settings.ISetupConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

/**
 * Drives the e-mail-verified self-registration flow.
 *
 * <p>Two phases:
 * <ol>
 *   <li>{@link #startRegistration(String, String, String)} — create or refresh
 *       a {@link RegistrationToken} for the address and queue a verification
 *       e-mail. We do not create the {@code MyUserEntity} yet; doing so would
 *       expose username-existence to anyone who hits the form.</li>
 *   <li>{@link #completeRegistration(String, String)} — consume a token and
 *       persist a {@code MyUserEntity} with the chosen password and the
 *       per-mandant default role set.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RegistrationService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RegistrationTokenRepository tokenRepository;
    private final MyUserRepository userRepository;
    private final ISetupConfigService setupConfigService;
    private final ObjectProvider<SystemMailSender> systemMailSenderProvider;
    private final PasswordEncoder passwordEncoder;
    private final SelfServiceProperties properties;
    private final IMailTemplateProvider mailTemplateProvider;
    private final MandateMenuConfigRepository mandateMenuConfigRepository;

    /**
     * Initiate registration for {@code email} on {@code mandat}.
     *
     * <p>Returns silently if the mandant has self-registration disabled or if
     * a {@link MyUserEntity} with this username already exists, so the caller
     * cannot probe for existing accounts. The verification e-mail is sent via
     * the configured GLOBAL system mail account (Mailbox); delivery is out-of-band.
     */
    @Transactional
    public RegistrationOutcome startRegistration(String email, String mandat, String publicBaseUrl) {
        if (email == null || email.isBlank() || mandat == null || mandat.isBlank()) {
            return RegistrationOutcome.INVALID_INPUT;
        }
        if (!setupConfigService.isSelfRegistrationEnabled(mandat)) {
            return RegistrationOutcome.DISABLED;
        }
        String normalised = email.trim().toLowerCase();
        if (userRepository.findByUsername(normalised) != null) {
            log.info("Self-registration rejected: user already exists (mandat={})", mandat);
            return RegistrationOutcome.ACCEPTED;
        }

        // SECURITY (card 307, K2.3): the clear-text token only in the link, in the DB only the SHA-256 hash.
        String rawToken = generateToken();
        RegistrationToken token = new RegistrationToken();
        token.setTokenHash(SelfServiceTokenHash.sha256Hex(rawToken));
        token.setEmail(normalised);
        token.setMandat(mandat);
        Instant now = Instant.now();
        token.setIssuedAt(now);
        token.setExpiresAt(now.plus(properties.getRegistrationTokenTtl()));
        tokenRepository.save(token);

        String link = buildLink(publicBaseUrl, "/register/verify", rawToken);
        // Karte 2554: a failed send is reported, otherwise the customer waits for a mail that never comes.
        // Only reachable for addresses WITHOUT an account (existing users return above), so it does not reveal accounts.
        return sendVerificationEmail(mandat, normalised, link)
                ? RegistrationOutcome.ACCEPTED
                : RegistrationOutcome.VERSAND_FEHLER;
    }

    /**
     * Card 2410: whether the link can still be used — without consuming it. Same condition as {@code consumeToken}
     * (exists, not consumed, {@code expiresAt > now}), so the GET shows the form only when the POST would accept it.
     */
    @Transactional(readOnly = true)
    public boolean tokenGueltig(String tokenValue) {
        if (tokenValue == null || tokenValue.isBlank()) {
            return false;
        }
        Instant now = Instant.now();
        return tokenRepository.findByTokenHash(SelfServiceTokenHash.sha256Hex(tokenValue))
                .filter(t -> !t.isConsumed() && t.getExpiresAt() != null && t.getExpiresAt().isAfter(now))
                .isPresent();
    }

    /**
     * Validate a token and create the {@link MyUserEntity}. The token is
     * marked consumed in the same transaction.
     */
    @Transactional
    public RegistrationResult completeRegistration(String tokenValue, String password) {
        if (tokenValue == null || tokenValue.isBlank()
                || password == null || password.length() < 8) {
            return RegistrationResult.invalid();
        }
        String hash = SelfServiceTokenHash.sha256Hex(tokenValue);
        Optional<RegistrationToken> tokenOpt = tokenRepository.findByTokenHash(hash);
        if (tokenOpt.isEmpty()) {
            return RegistrationResult.invalid();
        }
        RegistrationToken token = tokenOpt.get();
        Instant now = Instant.now();
        // SECURITY (card 307, K2.3): redeem ATOMICALLY (conditional UPDATE) instead of check-then-set —
        // prevents TOCTOU/replay on parallel calls. n==1 => token consumed exclusively.
        if (tokenRepository.consumeToken(hash, now) != 1) {
            return RegistrationResult.invalid();
        }
        if (userRepository.findByUsername(token.getEmail()) != null) {
            // The token has already been consumed (atomically); no duplicate account arises.
            return RegistrationResult.invalid();
        }
        MyUserEntity user = new MyUserEntity();
        user.setUsername(token.getEmail());
        user.setPassword(passwordEncoder.encode(password));
        // Convention: the BARE role name is stored; MyUserDetailsService prefixes it with
        // "ROLE_" at login. Previously "ROLE_USER" stood here by mistake -> the authority became
        // "ROLE_ROLE_USER" (ineffective). Correct is "user" (card 306, existing data migrated via Flyway).
        user.addRole("user");
        String mandat = properties.isMandantJeRegistrierung() ? neuerMandant() : token.getMandat().toLowerCase();
        user.addRole("PROPERTY_MANDAT_" + mandat);
        properties.getStandardRollen().stream()
                .filter(r -> r != null && !r.isBlank())
                .map(String::trim)
                .forEach(user::addRole);
        userRepository.save(user);

        log.info("Self-registration completed for {} on mandat={}", Log.mail(token.getEmail()), mandat);
        return RegistrationResult.success(token.getEmail());
    }

    /**
     * Karte 2554: a mandant of its own for one self-registered customer. The key is random (no
     * personal data in role names or logs); the {@link MandateMenuConfig} row makes it a regular
     * mandant, exactly as «Mandate › Neu» does.
     */
    private String neuerMandant() {
        for (int versuch = 0; versuch < 5; versuch++) {
            String kandidat = "k" + zufall(10);
            if (!mandateMenuConfigRepository.existsByMandateName(kandidat)) {
                MandateMenuConfig config = new MandateMenuConfig();
                config.setMandateName(kandidat);
                mandateMenuConfigRepository.save(config);
                return kandidat;
            }
        }
        throw new IllegalStateException("Kein freier Mandantenname gefunden");
    }

    private static String zufall(int laenge) {
        String zeichen = "abcdefghijkmnpqrstuvwxyz23456789";
        StringBuilder sb = new StringBuilder(laenge);
        for (int i = 0; i < laenge; i++) {
            sb.append(zeichen.charAt(RANDOM.nextInt(zeichen.length())));
        }
        return sb.toString();
    }

    private boolean sendVerificationEmail(String mandat, String to, String link) {
        SystemMailSender sender = systemMailSenderProvider.getIfAvailable();
        if (sender == null) {
            log.warn("Verification e-mail not sent (no SystemMailSender / Mailbox unavailable), mandat={}", mandat);
            return false;
        }
        Long accountId = setupConfigService.getSystemMailAccountId();
        if (accountId == null) {
            log.warn("Verification e-mail not sent: no GLOBAL system mail account configured (mandat={})", mandat);
            return false;
        }
        String defaultBetreff = "Bitte bestätigen Sie Ihre E-Mail-Adresse";
        String defaultBody = """
                Hallo,

                jemand hat mit dieser E-Mail-Adresse ein Konto angefragt.
                Bestätigen Sie die Adresse über den folgenden Link
                (gültig für {stunden} Stunden):

                {link}

                Falls Sie das nicht waren, ignorieren Sie diese Nachricht.
                """;
        RenderedMail mail = mailTemplateProvider.render(mandat, "auth.registration", defaultBetreff, defaultBody,
                Map.of("stunden", String.valueOf(properties.getRegistrationTokenTtl().toHours()), "link", link));
        try {
            if (!sender.sendSystemMail(accountId, to, mail.betreff(), mail.body(), false)) {
                log.warn("Verification e-mail could not be sent for mandat={}", mandat);
                return false;
            }
            return true;
        } catch (Exception e) {
            log.warn("Could not send verification e-mail for mandat={}: {}", mandat, e.getMessage());
            return false;
        }
    }

    private String buildLink(String requestBaseUrl, String path, String token) {
        String base = properties.getPublicBaseUrl().isBlank()
                ? requestBaseUrl
                : properties.getPublicBaseUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + path + "?token=" + token;
    }

    private static String generateToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public enum RegistrationOutcome {
        ACCEPTED, DISABLED, INVALID_INPUT, VERSAND_FEHLER
    }

    public record RegistrationResult(boolean ok, String username) {
        public static RegistrationResult success(String username) {
            return new RegistrationResult(true, username);
        }
        public static RegistrationResult invalid() {
            return new RegistrationResult(false, null);
        }
    }
}
