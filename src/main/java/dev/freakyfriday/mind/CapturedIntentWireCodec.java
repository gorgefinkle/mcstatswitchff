package dev.freakyfriday.mind;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Bounded authenticated BINARY DATA ONLY envelope for a future out-of-process
 * AI worker. No Java deserialization, class loading or callback execution.
 *
 * Authenticate (HMAC-SHA256) before parsing the payload, fail closed on
 * malformed/trailing bytes, session mismatch, excessive sizes or wrong tick.
 * The secret must arrive at the worker over a protected channel not yet
 * implemented. This codec does not create an isolated worker or sandbox AI.
 */
public final class CapturedIntentWireCodec {
    private CapturedIntentWireCodec() {}
    public static final int KEY_BYTES = 32;
    public static final int SIGNATURE_BYTES = 32;
    public static final int MAX_WIRE_BYTES = 65536;
    public static final int MAX_STRING_BYTES = 2048;
    private static final int MAGIC = 0x46464946; // ASCII FFIF
    private static final int VERSION = 1;
    private static final SecureRandom RANDOM = new SecureRandom();

    public record Envelope(UUID session, CapturedIntentBridge.Frame frame) {
        public Envelope {
            Objects.requireNonNull(session, "session");
            Objects.requireNonNull(frame, "frame");
        }
    }

    public static byte[] generateKey() {
        byte[] key = new byte[KEY_BYTES];
        RANDOM.nextBytes(key);
        return key;
    }

    public static byte[] encode(Envelope envelope, byte[] secret) {
        Objects.requireNonNull(envelope, "envelope");
        keyCheck(secret);
        try {
            ByteArrayOutputStream memory = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(memory);
            output.writeInt(MAGIC);
            output.writeByte(VERSION);
            uuid(output, envelope.session());
            var frame = envelope.frame();
            uuid(output, frame.bodyId());
            uuid(output, frame.mindId());
            output.writeLong(frame.tick());
            output.writeByte(frame.actions().size());
            for (var action : frame.actions()) {
                writeString(output, action.actionId());
                output.writeByte(action.arguments().size());
                var sorted = new ArrayList<>(action.arguments().entrySet());
                sorted.sort(Comparator.comparing(Map.Entry::getKey));
                for (var entry : sorted) {
                    writeString(output, entry.getKey());
                    writeString(output, entry.getValue());
                }
            }
            output.flush();
            byte[] payload = memory.toByteArray();
            if (payload.length + SIGNATURE_BYTES > MAX_WIRE_BYTES)
                throw new IllegalArgumentException("Intent payload exceeds wire byte limit");
            byte[] signature = hmac(payload, secret);
            byte[] packed = Arrays.copyOf(payload, payload.length + signature.length);
            System.arraycopy(signature, 0, packed, payload.length, signature.length);
            return packed;
        } catch (IOException impossibleInMemory) {
            throw new IllegalStateException("Unexpected memory writer error", impossibleInMemory);
        }
    }

    public static Envelope decode(byte[] message, byte[] secret,
                                  UUID expectedSession, long expectedTick) {
        keyCheck(secret);
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(expectedSession, "expected session");
        if (expectedTick < 0)
            throw new IllegalArgumentException("Negative expected tick");
        if (message.length <= SIGNATURE_BYTES + 4 || message.length > MAX_WIRE_BYTES)
            throw new IllegalArgumentException("Invalid authenticated message size");

        int payloadLength = message.length - SIGNATURE_BYTES;
        byte[] payload = Arrays.copyOf(message, payloadLength);
        byte[] signature = Arrays.copyOfRange(message, payloadLength, message.length);
        if (!MessageDigest.isEqual(hmac(payload, secret), signature))
            throw new IllegalArgumentException("Unauthenticated intent frame");

        ByteBuffer input = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN);
        if (input.remaining() < 4 + 1 + 16 * 3 + 8 + 1)
            throw new IllegalArgumentException("Truncated intent frame");
        if (input.getInt() != MAGIC || Byte.toUnsignedInt(input.get()) != VERSION)
            throw new IllegalArgumentException("Unknown intent message version");
        UUID session = readUuid(input);
        if (!expectedSession.equals(session))
            throw new IllegalArgumentException("Intent frame belongs to another session");
        UUID body = readUuid(input);
        UUID mind = readUuid(input);
        long tick = input.getLong();
        if (tick != expectedTick)
            throw new IllegalArgumentException("Stale or future intent frame");
        int n = Byte.toUnsignedInt(input.get());
        if (n > CapturedIntentBridge.MAX_ACTIONS)
            throw new IllegalArgumentException("Action count exceeds bound");

        List<BodyActionGate.Intent> actions = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String actionId = readString(input);
            if (!input.hasRemaining())
                throw new IllegalArgumentException("Truncated argument count");
            int argc = Byte.toUnsignedInt(input.get());
            if (argc > 64)
                throw new IllegalArgumentException("Argument count exceeds bound");
            Map<String,String> args = new HashMap<>();
            for (int k = 0; k < argc; k++) {
                String name = readString(input);
                String value = readString(input);
                if (args.putIfAbsent(name, value) != null)
                    throw new IllegalArgumentException("Duplicate action argument");
            }
            actions.add(new BodyActionGate.Intent(actionId, args));
        }
        if (input.hasRemaining())
            throw new IllegalArgumentException("Trailing bytes after intent frame");
        return new Envelope(session,
            new CapturedIntentBridge.Frame(body, mind, tick, actions));
    }

    private static void uuid(DataOutputStream output, UUID id) throws IOException {
        output.writeLong(id.getMostSignificantBits());
        output.writeLong(id.getLeastSignificantBits());
    }

    private static UUID readUuid(ByteBuffer input) {
        return new UUID(input.getLong(), input.getLong());
    }

    private static void writeString(DataOutputStream output, String value) throws IOException {
        byte[] text = value.getBytes(StandardCharsets.UTF_8);
        if (text.length > MAX_STRING_BYTES)
            throw new IllegalArgumentException("String exceeds wire limit");
        output.writeShort(text.length);
        output.write(text);
    }

    private static String readString(ByteBuffer input) {
        if (input.remaining() < Short.BYTES)
            throw new IllegalArgumentException("Truncated string length");
        int length = Short.toUnsignedInt(input.getShort());
        if (length > MAX_STRING_BYTES || length > input.remaining())
            throw new IllegalArgumentException("Invalid UTF-8 length");
        byte[] bytes = new byte[length];
        input.get(bytes);
        try {
            return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException badUtf8) {
            throw new IllegalArgumentException("Malformed UTF-8", badUtf8);
        }
    }

    private static void keyCheck(byte[] secret) {
        if (secret == null || secret.length != KEY_BYTES)
            throw new IllegalArgumentException("Expected a 256-bit session secret");
    }

    private static byte[] hmac(byte[] bytes, byte[] secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(bytes);
        } catch (java.security.GeneralSecurityException unavailable) {
            throw new IllegalStateException("Mandatory JDK HMAC-SHA256 unavailable",
                unavailable);
        }
    }
}
