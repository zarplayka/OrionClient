package ru.orion.client.discord;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.orion.client.OrionClient;
import ru.orion.client.config.OrionConfig;
import ru.orion.client.config.OrionConfigManager;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.management.ManagementFactory;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;

public final class OrionDiscordPresenceService {
    private static final Logger LOGGER = LoggerFactory.getLogger(OrionClient.MOD_ID);
    private static final Gson GSON = new Gson();
    private static final int IPC_VERSION = 1;
    private static final int OPCODE_HANDSHAKE = 0;
    private static final int OPCODE_FRAME = 1;
    private static final long UPDATE_INTERVAL_MS = 5_000L;

    private final OrionConfigManager configManager;

    private volatile boolean running;
    private volatile String username = "Player";
    private volatile long startedAt;
    private Thread workerThread;
    private int ticksUntilStartRetry;

    public OrionDiscordPresenceService(OrionConfigManager configManager) {
        this.configManager = configManager;
    }

    public void register() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> stop());
        start();
    }

    public void applyConfigNow() {
        OrionConfig config = configManager.getConfig();

        stop();
        ticksUntilStartRetry = 0;

        if (!config.isDiscordRpcEnabled()) {
            return;
        }

        start();
    }

    private void tick(Minecraft client) {
        if (client != null && client.getUser() != null) {
            username = client.getUser().getName();
        }

        OrionConfig config = configManager.getConfig();

        if (!config.isDiscordRpcEnabled()) {
            stop();
            ticksUntilStartRetry = 0;
            return;
        }

        if (running) {
            return;
        }

        if (ticksUntilStartRetry > 0) {
            ticksUntilStartRetry--;
            return;
        }

        ticksUntilStartRetry = 20 * 30;
        start();
    }

    private void start() {
        if (running || workerThread != null && workerThread.isAlive()) {
            return;
        }

        OrionConfig config = configManager.getConfig();
        String applicationId = config.getDiscordApplicationId().trim();

        if (!config.isDiscordRpcEnabled()) {
            return;
        }

        running = true;
        startedAt = System.currentTimeMillis() / 1000L;
        workerThread = new Thread(() -> runDiscordLoop(applicationId), "Orion Client Discord RPC");
        workerThread.setDaemon(true);
        workerThread.start();
    }

    private void runDiscordLoop(String applicationId) {
        try (DiscordIpcConnection connection = DiscordIpcConnection.open()) {
            sendHandshake(connection, applicationId);

            while (running) {
                sendPresence(connection);
                sleepQuietly(UPDATE_INTERVAL_MS);
            }
        } catch (IOException exception) {
            if (running) {
                LOGGER.warn("Discord Rich Presence is unavailable. Start Discord and check discordApplicationId.", exception);
            }
        } catch (RuntimeException exception) {
            LOGGER.warn("Discord Rich Presence stopped because of an unexpected error.", exception);
        } finally {
            running = false;
        }
    }

    private void sendHandshake(DiscordIpcConnection connection, String applicationId) throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("v", IPC_VERSION);
        payload.addProperty("client_id", applicationId);
        connection.write(OPCODE_HANDSHAKE, payload);
        connection.read();
    }

    private void sendPresence(DiscordIpcConnection connection) throws IOException {
        OrionConfig config = configManager.getConfig();

        JsonObject timestamps = new JsonObject();
        timestamps.addProperty("start", startedAt);

        JsonObject assets = new JsonObject();
        assets.addProperty("large_image", config.getDiscordLargeImageKey());
        assets.addProperty("large_text", config.getDiscordLargeImageText());

        JsonObject activity = new JsonObject();
        activity.addProperty("details", OrionClient.VERSION_TEXT);
        activity.addProperty("state", "Nick: " + username);
        activity.add("timestamps", timestamps);
        activity.add("assets", assets);

        JsonObject args = new JsonObject();
        args.addProperty("pid", currentProcessId());
        args.add("activity", activity);

        JsonObject payload = new JsonObject();
        payload.addProperty("cmd", "SET_ACTIVITY");
        payload.add("args", args);
        payload.addProperty("nonce", UUID.randomUUID().toString());

        connection.write(OPCODE_FRAME, payload);
    }

    private void stop() {
        running = false;

        if (workerThread != null) {
            workerThread.interrupt();
            workerThread = null;
        }
    }

    private static int currentProcessId() {
        String runtimeName = ManagementFactory.getRuntimeMXBean().getName();
        int separatorIndex = runtimeName.indexOf('@');

        if (separatorIndex <= 0) {
            return 0;
        }

        try {
            return Integer.parseInt(runtimeName.substring(0, separatorIndex));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class DiscordIpcConnection implements Closeable {
        private final Closeable closeable;
        private final ReadableByteChannel reader;
        private final WritableByteChannel writer;

        private DiscordIpcConnection(Closeable closeable, ReadableByteChannel reader, WritableByteChannel writer) {
            this.closeable = closeable;
            this.reader = reader;
            this.writer = writer;
        }

        static DiscordIpcConnection open() throws IOException {
            IOException lastException = null;

            for (int i = 0; i < 10; i++) {
                try {
                    return open(i);
                } catch (IOException exception) {
                    lastException = exception;
                }
            }

            throw lastException == null ? new IOException("Discord IPC pipe was not found.") : lastException;
        }

        private static DiscordIpcConnection open(int index) throws IOException {
            String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);

            if (osName.contains("win")) {
                RandomAccessFile pipe = new RandomAccessFile("\\\\.\\pipe\\discord-ipc-" + index, "rw");
                return new DiscordIpcConnection(pipe, pipe.getChannel(), pipe.getChannel());
            }

            SocketChannel socket = SocketChannel.open(StandardProtocolFamily.UNIX);
            socket.connect(UnixDomainSocketAddress.of(unixSocketPath(index)));
            return new DiscordIpcConnection(socket, socket, socket);
        }

        private static Path unixSocketPath(int index) {
            String runtimeDir = System.getenv("XDG_RUNTIME_DIR");
            String tempDir = System.getProperty("java.io.tmpdir");

            Path baseDir = runtimeDir == null || runtimeDir.isBlank() ? Path.of(tempDir) : Path.of(runtimeDir);
            Path socketPath = baseDir.resolve("discord-ipc-" + index);

            if (Files.exists(socketPath)) {
                return socketPath;
            }

            return Path.of(tempDir).resolve("discord-ipc-" + index);
        }

        void write(int opcode, JsonObject payload) throws IOException {
            byte[] payloadBytes = GSON.toJson(payload).getBytes(StandardCharsets.UTF_8);
            ByteBuffer frame = ByteBuffer.allocate(8 + payloadBytes.length).order(ByteOrder.LITTLE_ENDIAN);
            frame.putInt(opcode);
            frame.putInt(payloadBytes.length);
            frame.put(payloadBytes);
            frame.flip();

            while (frame.hasRemaining()) {
                writer.write(frame);
            }
        }

        JsonObject read() throws IOException {
            ByteBuffer header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
            readFully(header);
            header.flip();

            header.getInt();
            int length = header.getInt();
            ByteBuffer payload = ByteBuffer.allocate(length);
            readFully(payload);
            payload.flip();

            String json = StandardCharsets.UTF_8.decode(payload).toString();
            return GSON.fromJson(json, JsonObject.class);
        }

        private void readFully(ByteBuffer buffer) throws IOException {
            while (buffer.hasRemaining()) {
                int read = reader.read(buffer);

                if (read < 0) {
                    throw new EOFException("Discord IPC connection was closed.");
                }
            }
        }

        @Override
        public void close() throws IOException {
            Channels.newInputStream(reader).close();
            closeable.close();
        }
    }
}
