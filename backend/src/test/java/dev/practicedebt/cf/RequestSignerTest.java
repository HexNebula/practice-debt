package dev.practicedebt.cf;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The signature format Codeforces specifies, pinned down.
 *
 * <p>A wrong signature is rejected with a generic refusal that says nothing about what was wrong,
 * so the composition is asserted directly rather than inferred from a working call.
 */
class RequestSignerTest {

    private final RequestSigner signer = new RequestSigner();

    private static String sha512Hex(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-512");
        return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("hashes rand/method?sortedParams#secret and prefixes the signature with rand")
    void buildsTheDocumentedSignature() throws Exception {
        Map<String, String> params = Map.of("handle", "tourist");

        Map<String, String> signed =
                signer.sign("user.status", params, "KEY123", "SECRET", "abc123", 1700000000L);

        String expectedHash = sha512Hex(
                "abc123/user.status?apiKey=KEY123&handle=tourist&time=1700000000#SECRET");

        assertThat(signed).containsEntry("apiSig", "abc123" + expectedHash);
        assertThat(signed).containsEntry("apiKey", "KEY123");
        assertThat(signed).containsEntry("time", "1700000000");
        assertThat(signed).containsEntry("handle", "tourist");
    }

    @Test
    @DisplayName("parameters are sorted by key, not left in insertion order")
    void sortsParametersByKey() throws Exception {
        // Deliberately reversed: a signer that hashed insertion order would still pass a test that
        // supplied them alphabetically.
        Map<String, String> params = new LinkedHashMap<>();
        params.put("count", "10");
        params.put("from", "1");
        params.put("handle", "tourist");

        Map<String, String> signed =
                signer.sign("user.status", params, "K", "S", "zzzzzz", 42L);

        String expected = sha512Hex(
                "zzzzzz/user.status?apiKey=K&count=10&from=1&handle=tourist&time=42#S");
        assertThat(signed.get("apiSig")).isEqualTo("zzzzzz" + expected);
    }

    @Test
    @DisplayName("equal keys are ordered by value")
    void sortsRepeatedKeysByValue() throws Exception {
        // Codeforces specifies key-then-value ordering. No current call sends duplicate keys, but
        // the rule is part of the format and cheap to honour.
        Map<String, String> signed = signer.sign("contest.status", Map.of("contestId", "1"),
                "K", "S", "aaaaaa", 1L);

        String expected = sha512Hex("aaaaaa/contest.status?apiKey=K&contestId=1&time=1#S");
        assertThat(signed.get("apiSig")).isEqualTo("aaaaaa" + expected);
    }

    @Test
    @DisplayName("the signature is six random characters plus a SHA-512 hex digest")
    void signatureHasTheRightShape() {
        Map<String, String> signed = signer.sign("user.rating", Map.of("handle", "x"), "K", "S");

        assertThat(signed.get("apiSig")).hasSize(6 + 128);
        assertThat(signed.get("apiSig").substring(0, 6)).matches("[a-z0-9]{6}");
        assertThat(signed.get("apiSig").substring(6)).matches("[0-9a-f]{128}");
    }

    @Test
    @DisplayName("two signings of the same call differ, because the prefix is random")
    void randomPrefixVaries() {
        String first = signer.sign("user.rating", Map.of("handle", "x"), "K", "S").get("apiSig");
        String second = signer.sign("user.rating", Map.of("handle", "x"), "K", "S").get("apiSig");

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("the caller's parameter map is left alone")
    void doesNotMutateInput() {
        Map<String, String> params = Map.of("handle", "tourist");

        signer.sign("user.status", params, "K", "S");

        assertThat(params).containsOnlyKeys("handle");
    }

    @Test
    @DisplayName("the secret never appears in the signed parameters")
    void secretIsNotLeakedIntoTheQuery() {
        Map<String, String> signed =
                signer.sign("user.status", Map.of("handle", "x"), "KEY", "TOPSECRET");

        assertThat(signed.values()).noneMatch(v -> v.contains("TOPSECRET"));
    }
}
