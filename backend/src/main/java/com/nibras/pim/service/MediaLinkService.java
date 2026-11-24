package com.nibras.pim.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * Signed, expiring links to one file on disk, for what the browser fetches on its own — a
 * {@code <video src>} that streams with Range requests, a grid of {@code <img>} thumbnails, a
 * document's slide images. Those requests cannot carry the bearer token, and fetching a video
 * whole as a blob first is not streaming. The link is an HMAC (keyed by the JWT secret) over the
 * kind, the absolute path and the expiry, handed out by an authenticated endpoint after it has
 * checked the path itself; {@code /api/public/media} serves it without a session.
 */
@Service
public class MediaLinkService {

    /** Long enough for a film watched with pauses; a page reload signs afresh. */
    static final Duration TTL = Duration.ofHours(8);

    private final byte[] key;

    public MediaLinkService(@Value("${nibras.security.jwt.secret}") String secret) {
        this.key = ("media-link:" + secret).getBytes(StandardCharsets.UTF_8);
    }

    /** {@code /api/public/media/<kind>?p=…&e=…&s=…}, relative to the app's base (no leading slash). */
    public String link(String kind, Path file) {
        String p = file.toAbsolutePath().normalize().toString();
        long e = Instant.now().plus(TTL).getEpochSecond();
        return "api/public/media/" + kind + "?p=" + enc(p) + "&e=" + e + "&s=" + sign(kind, p, e);
    }

    /** The file a link names, once its signature and expiry check out. */
    public Path verify(String kind, String p, long e, String s) {
        if (p == null || s == null || Instant.now().getEpochSecond() > e) {
            throw new ResponseStatusException(FORBIDDEN, "This link has expired; reload the page.");
        }
        if (!MessageDigest.isEqual(sign(kind, p, e).getBytes(StandardCharsets.US_ASCII), s.getBytes(StandardCharsets.US_ASCII))) {
            throw new ResponseStatusException(FORBIDDEN, "Bad link.");
        }
        Path file = Paths.get(p);
        if (!Files.isRegularFile(file)) throw new ResponseStatusException(NOT_FOUND, "File not found.");
        return file;
    }

    String sign(String kind, String p, long e) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            byte[] h = mac.doFinal((kind + "\n" + p + "\n" + e).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(h);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
