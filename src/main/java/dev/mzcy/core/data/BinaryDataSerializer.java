package dev.mzcy.core.data;

import dev.mzcy.core.exception.DataStoreException;
import org.jetbrains.annotations.NotNull;

import java.io.*;

/**
 * Default {@link DataSerializer} using Java object serialization
 * combined with XOR-based obfuscation.
 *
 * <p>The goal is not cryptographic security — it is to produce files
 * that are unreadable in a text editor and discourage manual editing.
 * For sensitive data, replace this with an AES-based implementation.
 *
 * <p>Values must implement {@link Serializable}.
 *
 * @param <V> the value type — must implement {@link Serializable}
 */
public final class BinaryDataSerializer<V extends Serializable> implements DataSerializer<V> {

    /**
     * XOR key applied byte-by-byte to the serialized data.
     * Change this per-project for basic uniqueness.
     */
    private static final byte[] XOR_KEY = {
            0x4D, 0x5A, 0x43, 0x59, 0x43, 0x4F, 0x52, 0x45
    };

    // =========================================================================
    // Serialization
    // =========================================================================

    @Override
    public byte[] serialize(@NotNull V value) throws Exception {
        final byte[] raw = toBytes(value);
        return xor(raw);
    }

    @Override
    @NotNull
    @SuppressWarnings("unchecked")
    public V deserialize(byte[] bytes) throws Exception {
        final byte[] raw = xor(bytes); // XOR is its own inverse
        return (V) fromBytes(raw);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    @NotNull
    private byte[] toBytes(@NotNull Object value) throws IOException {
        try (final ByteArrayOutputStream baos = new ByteArrayOutputStream();
             final ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(value);
            oos.flush();
            return baos.toByteArray();
        }
    }

    @NotNull
    private Object fromBytes(byte[] bytes) throws IOException, ClassNotFoundException {
        try (final ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
             final ObjectInputStream ois = new ObjectInputStream(bais)) {
            return ois.readObject();
        }
    }

    /**
     * Applies XOR obfuscation. Since XOR is symmetric, the same method
     * is used for both obfuscation and de-obfuscation.
     */
    private byte @NotNull [] xor(byte[] data) {
        final byte[] result = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            result[i] = (byte) (data[i] ^ XOR_KEY[i % XOR_KEY.length]);
        }
        return result;
    }
}