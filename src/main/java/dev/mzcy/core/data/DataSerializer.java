package dev.mzcy.core.data;

import org.jetbrains.annotations.NotNull;

/**
 * Strategy interface for serializing and deserializing data store entries.
 *
 * <p>Implementations decide the wire format — binary, encrypted binary, etc.
 * The default implementation is {@link BinaryDataSerializer} which uses
 * Java serialization + XOR obfuscation to prevent casual editing.
 *
 * @param <V> the value type to serialize
 */
public interface DataSerializer<V> {

    /**
     * Serializes a value to a byte array.
     *
     * @param value the value to serialize (never null)
     * @return the serialized bytes
     * @throws Exception if serialization fails
     */
    byte[] serialize(@NotNull V value) throws Exception;

    /**
     * Deserializes a value from a byte array.
     *
     * @param bytes the bytes to deserialize (never null)
     * @return the deserialized value
     * @throws Exception if deserialization fails
     */
    @NotNull
    V deserialize(byte[] bytes) throws Exception;
}