package com.fpcmapper.network;

import com.fpcmapper.FPCMapperMod;
import com.fpcmapper.compat.Compat;
import com.fpcmapper.util.CollectorConfig;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class ChunkSender {

    private static final int QUEUE_WARN_DEPTH = 4096;
    private static final long BASE_BACKOFF_MS = 500L;
    private static final long MAX_BACKOFF_MS = 15_000L;
    private static final long CHUNK_COOLDOWN_MS = 5_000L;
    private static final int MAX_TRACKED_CHUNKS = 32768;

    private final CollectorConfig config;
    private final ExecutorService pool;
    private final BlockingQueue<ChunkTask> queue = new LinkedBlockingQueue<>();
    private final CloseableHttpClient httpClient;

    private final Map<Long, Long> recentlySent = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Long> eldest) {
            return size() > MAX_TRACKED_CHUNKS;
        }
    };

    private final AtomicLong successCount = new AtomicLong();
    private final AtomicLong failCount = new AtomicLong();
    private final AtomicLong sessionSuccess = new AtomicLong();
    private final AtomicLong sessionFail = new AtomicLong();

    public ChunkSender(CollectorConfig config) {
        this.config = config;
        this.httpClient = buildHttpClient();

        this.pool = Executors.newFixedThreadPool(config.senderThreads, r -> {
            Thread t = new Thread(r, "FPCMapper-Sender");
            t.setDaemon(true);
            return t;
        });
        for (int i = 0; i < config.senderThreads; i++) {
            pool.submit(this::workerLoop);
        }
    }

    private CloseableHttpClient buildHttpClient() {
        var connManager = PoolingHttpClientConnectionManagerBuilder.create()
                .setMaxConnTotal(config.senderThreads + 4)
                .setMaxConnPerRoute(config.senderThreads + 4)
                .build();

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofSeconds(15))
                .setResponseTimeout(Timeout.ofSeconds(45))
                .build();

        return HttpClients.custom()
                .setConnectionManager(connManager)
                .setDefaultRequestConfig(requestConfig)
                .setKeepAliveStrategy((response, context) -> TimeValue.ofSeconds(30))
                .setUserAgent("FPCMapper/" + FPCMapperMod.VERSION)
                .build();
    }

    public void enqueue(int chunkX, int chunkZ, byte[] nbtBytes, String playerName, String dimension) {
        long key = packKey(chunkX, chunkZ);
        long now = System.currentTimeMillis();

        synchronized (recentlySent) {
            Long lastSent = recentlySent.get(key);
            if (lastSent != null && now - lastSent < CHUNK_COOLDOWN_MS) return;
            recentlySent.entrySet().removeIf(e -> now - e.getValue() >= CHUNK_COOLDOWN_MS);
            recentlySent.put(key, now);
        }

        queue.offer(new ChunkTask(chunkX, chunkZ, nbtBytes, playerName, dimension, now));
        int depth = queue.size();
        if (depth > 0 && depth % QUEUE_WARN_DEPTH == 0) {
            FPCMapperMod.LOGGER.warn("Upload queue at {} chunks, consider raising senderThreads", depth);
        }
    }

    public long getTotalSuccess() { return successCount.get(); }
    public long getTotalFail() { return failCount.get(); }
    public long getSessionSuccess() { return sessionSuccess.get(); }
    public long getSessionFail() { return sessionFail.get(); }
    public int getQueueDepth() { return queue.size(); }

    public void onDisconnect() {
        long sent = sessionSuccess.getAndSet(0);
        long failed = sessionFail.getAndSet(0);
        FPCMapperMod.LOGGER.info("Session ended: {} chunks uploaded, {} failed", sent, failed);
    }

    private void workerLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                uploadWithRetry(queue.take());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void uploadWithRetry(ChunkTask task) {
        for (int attempt = 1; attempt <= config.maxRetries; attempt++) {
            try {
                upload(task);
                long total = successCount.incrementAndGet();
                long session = sessionSuccess.incrementAndGet();
                if (total % 100 == 0) {
                    FPCMapperMod.LOGGER.info("{} chunks uploaded this session ({} total, {} failed)",
                            session, total, failCount.get());
                }
                return;
            } catch (IOException e) {
                if (attempt == config.maxRetries) {
                    failCount.incrementAndGet();
                    sessionFail.incrementAndGet();
                    FPCMapperMod.LOGGER.warn("Gave up on chunk ({},{}) after {} attempts: {}",
                            task.chunkX, task.chunkZ, config.maxRetries, e.getMessage());
                    return;
                }
                long backoff = Math.min(BASE_BACKOFF_MS * (1L << (attempt - 1)), MAX_BACKOFF_MS);
                long sleep = backoff + (long) (Math.random() * backoff * 0.3);
                try {
                    Thread.sleep(sleep);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void upload(ChunkTask task) throws IOException {
        String meta = String.format(
                "{\"chunkX\":%d,\"chunkZ\":%d,\"timestamp\":%d,\"player\":%s,\"dimension\":%s,\"protocolVersion\":%d}",
                task.chunkX, task.chunkZ, task.timestamp,
                jsonString(task.playerName), jsonString(task.dimension),
                Compat.PROTOCOL_VERSION
        );

        var entity = MultipartEntityBuilder.create()
                .addBinaryBody("meta", meta.getBytes(StandardCharsets.UTF_8),
                        ContentType.APPLICATION_JSON, "meta.json")
                .addBinaryBody("chunk", task.nbtBytes,
                        ContentType.APPLICATION_OCTET_STREAM, "chunk.bin")
                .build();

        HttpPost post = new HttpPost(config.serverUrl + "/chunk");
        post.setEntity(entity);
        post.setHeader("X-Auth-Token", config.authToken);
        post.setHeader("X-Client-Version", FPCMapperMod.VERSION);
        post.setHeader("ngrok-skip-browser-warning", "true");

        httpClient.execute(post, response -> {
            int code = response.getCode();
            if (code < 200 || code >= 300) {
                String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                throw new IOException("Server returned " + code + ": "
                        + body.substring(0, Math.min(200, body.length())));
            }
            EntityUtils.consume(response.getEntity());
            return null;
        });
    }

    private static String jsonString(String s) {
        if (s == null) return "null";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static long packKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    public void shutdown() {
        pool.shutdown();
        try {
            if (!pool.awaitTermination(15, TimeUnit.SECONDS)) pool.shutdownNow();
        } catch (InterruptedException e) {
            pool.shutdownNow();
        }
        try {
            httpClient.close();
        } catch (IOException ignored) {
        }
        FPCMapperMod.LOGGER.info("Sender stopped: {} uploaded, {} failed", successCount.get(), failCount.get());
    }

    private record ChunkTask(int chunkX, int chunkZ, byte[] nbtBytes,
                             String playerName, String dimension, long timestamp) {}
}
