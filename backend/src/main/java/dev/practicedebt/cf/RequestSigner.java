package dev.practicedebt.cf;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Signs Codeforces API requests.
 *
 * <p>Codeforces authorises a call with three extra parameters: {@code apiKey}, {@code time}, and
 * {@code apiSig}. The signature is six random characters followed by the SHA-512 of
 *
 * <pre>{@code <rand>/<methodName>?<params sorted by key then value>#<secret>}</pre>
 *
 * <p>where the sorted parameters include {@code apiKey} and {@code time}. The random prefix is
 * repeated at the front of {@code apiSig} so the server can reproduce the hash.
 *
 * <p>Two details are easy to get wrong and are the usual cause of a rejected signature: the sort is
 * over key <em>and then value</em>, not insertion order, and the parameters hashed must be exactly
 * the parameters sent — including the two this class adds.
 */
public class RequestSigner {

    private static final int RANDOM_PREFIX_LENGTH = 6;
    private static final String PREFIX_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";

    private final SecureRandom random = new SecureRandom();

    /**
     * Returns the parameters to send, with the authorisation parameters added.
     *
     * @param methodName e.g. {@code user.status}, with no leading slash
     * @param params     the call's own parameters
     * @return a new map; the input is not modified
     */
    public Map<String, String> sign(String methodName, Map<String, String> params, String apiKey,
            String apiSecret) {
        return sign(methodName, params, apiKey, apiSecret, randomPrefix(),
                System.currentTimeMillis() / 1000);
    }

    /** Seam for tests: the random prefix and clock are the only non-deterministic inputs. */
    Map<String, String> sign(String methodName, Map<String, String> params, String apiKey,
            String apiSecret, String randomPrefix, long unixTime) {

        Map<String, String> signed = new TreeMap<>(params);
        signed.put("apiKey", apiKey);
        signed.put("time", String.valueOf(unixTime));

        String toHash = randomPrefix + "/" + methodName + "?" + canonicalQuery(signed)
                + "#" + apiSecret;
        signed.put("apiSig", randomPrefix + sha512Hex(toHash));
        return signed;
    }

    /**
     * Parameters ordered by key, then by value where keys collide, joined as a query string.
     *
     * <p>Codeforces specifies this ordering explicitly; using insertion order produces a signature
     * the server will reject without saying why.
     */
    private static String canonicalQuery(Map<String, String> params) {
        List<Map.Entry<String, String>> entries = new ArrayList<>(params.entrySet());
        entries.sort(Comparator
                .comparing((Map.Entry<String, String> e) -> e.getKey())
                .thenComparing(Map.Entry::getValue));

        StringBuilder query = new StringBuilder();
        for (Map.Entry<String, String> entry : entries) {
            if (!query.isEmpty()) {
                query.append('&');
            }
            query.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return query.toString();
    }

    private String randomPrefix() {
        StringBuilder prefix = new StringBuilder(RANDOM_PREFIX_LENGTH);
        for (int i = 0; i < RANDOM_PREFIX_LENGTH; i++) {
            prefix.append(PREFIX_ALPHABET.charAt(random.nextInt(PREFIX_ALPHABET.length())));
        }
        return prefix.toString();
    }

    private static String sha512Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-512");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-512 is required by the JDK and must exist", e);
        }
    }
}
