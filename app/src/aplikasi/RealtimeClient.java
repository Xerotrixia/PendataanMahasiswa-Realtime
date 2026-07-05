/*
 * Aplikasi Pendataan Mahasiswa
 * Universitas Bina Sarana Informatika
 */
package aplikasi;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;

/**
 * Klien realtime ke Supabase lewat WebSocket, memakai protokol "Phoenix
 * Channels" yang dipakai fitur Supabase Realtime. Menggantikan cara lama
 * (Timer polling tiap 5 detik) dengan notifikasi langsung begitu ada
 * perubahan (tambah/ubah/hapus) di tabel, dari komputer manapun.
 *
 * Tidak butuh library tambahan sama sekali karena hanya memakai
 * java.net.http.WebSocket bawaan Java 11+ (paket yang sama yang dipakai
 * SupabaseClient untuk request REST biasa).
 *
 * Kalau koneksi realtime gagal/putus (mis. internet sempat bermasalah),
 * aplikasi TETAP JALAN NORMAL: dianggap sebagai jaring pengaman, Timer
 * polling yang sudah ada di MainForm/AdminForm tetap dibiarkan aktif
 * (dengan interval yang lebih longgar) supaya data tetap ter-refresh
 * walau realtime sedang tidak tersambung.
 */
class RealtimeClient {

    private static final RealtimeClient INSTANCE = new RealtimeClient();

    static RealtimeClient getInstance() {
        return INSTANCE;
    }

    private static final String SCHEMA = "public";
    private static final long HEARTBEAT_SECONDS = 25;
    private static final long RECONNECT_DELAY_SECONDS = 5;

    private final Map<String, Runnable> subscribers = new ConcurrentHashMap<>();
    private final AtomicInteger refCounter = new AtomicInteger(1);

    private WebSocket webSocket;
    private ScheduledExecutorService heartbeatExecutor;
    private ScheduledExecutorService reconnectExecutor;
    private volatile boolean shuttingDown = false;
    private volatile boolean connecting = false;

    private RealtimeClient() {
    }

    /**
     * Daftarkan callback yang akan dipanggil (lewat SwingUtilities, jadi
     * aman dipanggil dari thread manapun) setiap ada perubahan data di
     * "table" tertentu (INSERT/UPDATE/DELETE), datang dari klien manapun
     * termasuk aplikasi ini sendiri di komputer lain.
     */
    synchronized void subscribe(String table, Runnable onChange) {
        boolean isNewTable = !subscribers.containsKey(table);
        subscribers.put(table, onChange);
        if (webSocket == null) {
            connect();
        } else if (isNewTable) {
            joinChannel(table);
        }
    }

    /**
     * Tutup koneksi realtime, dipanggil saat logout supaya tidak ada
     * callback "nyasar" ke form yang sudah di-dispose, dan supaya sesi
     * berikutnya (setelah login lagi) mulai dari koneksi yang bersih.
     */
    synchronized void shutdown() {
        shuttingDown = true;
        subscribers.clear();
        if (heartbeatExecutor != null) {
            heartbeatExecutor.shutdownNow();
            heartbeatExecutor = null;
        }
        if (reconnectExecutor != null) {
            reconnectExecutor.shutdownNow();
            reconnectExecutor = null;
        }
        if (webSocket != null) {
            try {
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "logout");
            } catch (Exception ignored) {
                // koneksi mungkin sudah putus duluan, tidak masalah
            }
            webSocket = null;
        }
        shuttingDown = false;
    }

    private synchronized void connect() {
        if (connecting || shuttingDown) {
            return;
        }
        connecting = true;
        String url;
        try {
            String projectUrl = SupabaseClient.getProjectUrl(); // https://xxxxx.supabase.co
            String wsBase = projectUrl.replaceFirst("^http", "ws"); // https->wss, http->ws
            url = wsBase + "/realtime/v1/websocket?apikey=" + SupabaseClient.getApiKey() + "&vsn=1.0.0";
        } catch (Exception e) {
            // db.properties belum siap dsb - realtime opsional, jangan
            // ganggu jalannya aplikasi, cukup andalkan polling sebagai
            // jaring pengaman.
            connecting = false;
            return;
        }

        HttpClient.newHttpClient().newWebSocketBuilder()
                .buildAsync(URI.create(url), new RealtimeListener())
                .thenAccept(ws -> {
                    synchronized (RealtimeClient.this) {
                        webSocket = ws;
                        connecting = false;
                        startHeartbeat();
                        for (String table : subscribers.keySet()) {
                            joinChannel(table);
                        }
                    }
                })
                .exceptionally(ex -> {
                    connecting = false;
                    scheduleReconnect();
                    return null;
                });
    }

    private void joinChannel(String table) {
        if (webSocket == null) {
            return;
        }
        String topic = "realtime:" + SCHEMA + ":" + table;
        String ref = String.valueOf(refCounter.getAndIncrement());
        String json = "{\"topic\":\"" + topic + "\",\"event\":\"phx_join\","
                + "\"payload\":{\"config\":{\"postgres_changes\":[{\"event\":\"*\","
                + "\"schema\":\"" + SCHEMA + "\",\"table\":\"" + table + "\"}]}},"
                + "\"ref\":\"" + ref + "\",\"join_ref\":\"" + ref + "\"}";
        webSocket.sendText(json, true);
    }

    private void startHeartbeat() {
        if (heartbeatExecutor != null) {
            return;
        }
        heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "supabase-realtime-heartbeat");
            t.setDaemon(true);
            return t;
        });
        heartbeatExecutor.scheduleAtFixedRate(() -> {
            WebSocket ws = webSocket;
            if (ws != null) {
                String ref = String.valueOf(refCounter.getAndIncrement());
                ws.sendText("{\"topic\":\"phoenix\",\"event\":\"heartbeat\",\"payload\":{},\"ref\":\"" + ref + "\"}", true);
            }
        }, HEARTBEAT_SECONDS, HEARTBEAT_SECONDS, TimeUnit.SECONDS);
    }

    private synchronized void scheduleReconnect() {
        if (shuttingDown) {
            return;
        }
        webSocket = null;
        if (heartbeatExecutor != null) {
            heartbeatExecutor.shutdownNow();
            heartbeatExecutor = null;
        }
        if (reconnectExecutor != null) {
            return; // sudah ada percobaan reconnect yang dijadwalkan
        }
        reconnectExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "supabase-realtime-reconnect");
            t.setDaemon(true);
            return t;
        });
        reconnectExecutor.schedule(() -> {
            synchronized (RealtimeClient.this) {
                reconnectExecutor = null;
            }
            connect();
        }, RECONNECT_DELAY_SECONDS, TimeUnit.SECONDS);
    }

    private void handleMessage(String text) {
        try {
            Object parsed = SupabaseClient.Json.parse(text);
            if (!(parsed instanceof Map)) {
                return;
            }
            Map<?, ?> map = (Map<?, ?>) parsed;
            Object event = map.get("event");
            Object topic = map.get("topic");
            if (event == null || topic == null) {
                return;
            }
            if ("postgres_changes".equals(event.toString())) {
                String prefix = "realtime:" + SCHEMA + ":";
                String topicStr = topic.toString();
                if (topicStr.startsWith(prefix)) {
                    String table = topicStr.substring(prefix.length());
                    Runnable callback = subscribers.get(table);
                    if (callback != null) {
                        SwingUtilities.invokeLater(callback);
                    }
                }
            }
        } catch (Exception e) {
            // pesan heartbeat reply / phx_reply dan semacamnya sengaja
            // diabaikan - yang kita perlukan cuma event postgres_changes
        }
    }

    /**
     * Listener WebSocket. Pesan teks bisa datang terpotong-potong (frame),
     * jadi ditampung dulu ke buffer sampai frame terakhir (last=true)
     * baru diproses sebagai satu pesan JSON utuh.
     */
    private class RealtimeListener implements WebSocket.Listener {

        private final StringBuilder buffer = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buffer.append(data);
            webSocket.request(1);
            if (last) {
                String message = buffer.toString();
                buffer.setLength(0);
                handleMessage(message);
            }
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            scheduleReconnect();
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            scheduleReconnect();
        }
    }
}
