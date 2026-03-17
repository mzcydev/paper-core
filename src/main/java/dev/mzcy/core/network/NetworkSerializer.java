package dev.mzcy.core.network;

import dev.mzcy.core.exception.CoreException;
import org.jetbrains.annotations.NotNull;

import java.io.*;

/**
 * Serializes and deserializes network message objects to/from byte arrays.
 *
 * <p>Uses Java object serialization — message classes must implement
 * {@link Serializable} and have a no-arg constructor.
 *
 * <p>The format written to the plugin message channel is:
 * <pre>
 * [2 bytes: class name length][N bytes: class name][M bytes: serialized object]
 * </pre>
 *
 * <p>The class name prefix allows the receiver to instantiate the correct
 * type without prior knowledge of which message was sent.
 */
public final class NetworkSerializer {

    private NetworkSerializer() {}

    /**
     * Serializes a message object to a byte array suitable for
     * sending over a plugin messaging channel.
     *
     * @param message the message to serialize
     * @return the serialized bytes
     * @throws CoreException if serialization fails
     */
    @NotNull
    public static byte[] serialize(@NotNull Object message) {
        try (final ByteArrayOutputStream baos = new ByteArrayOutputStream();
             final DataOutputStream dos = new DataOutputStream(baos)) {

            final String className = message.getClass().getName();
            dos.writeUTF(className);

            try (final ObjectOutputStream oos = new ObjectOutputStream(baos)) {
                oos.writeObject(message);
            }

            return baos.toByteArray();

        } catch (IOException ex) {
            throw new CoreException("Failed to serialize network message: "
                    + message.getClass().getSimpleName(), ex);
        }
    }

    /**
     * Deserializes a message object from a raw plugin message byte array.
     *
     * @param data        the raw bytes from the plugin message channel
     * @param classLoader the class loader to use for type resolution
     * @return the deserialized message
     * @throws CoreException if deserialization fails
     */
    @NotNull
    public static Object deserialize(
            byte[] data,
            @NotNull ClassLoader classLoader
    ) {
        try (final ByteArrayInputStream bais = new ByteArrayInputStream(data);
             final DataInputStream dis = new DataInputStream(bais)) {

            final String className = dis.readUTF();

            try (final ObjectInputStream ois = new ObjectInputStream(bais) {
                @Override
                protected Class<?> resolveClass(ObjectStreamClass desc)
                        throws IOException, ClassNotFoundException {
                    try {
                        return Class.forName(desc.getName(), false, classLoader);
                    } catch (ClassNotFoundException ex) {
                        return super.resolveClass(desc);
                    }
                }
            }) {
                return ois.readObject();
            }

        } catch (IOException | ClassNotFoundException ex) {
            throw new CoreException("Failed to deserialize network message", ex);
        }
    }
}