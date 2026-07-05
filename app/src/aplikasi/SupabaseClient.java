/*
 * Aplikasi Pendataan Mahasiswa
 * Universitas Bina Sarana Informatika
 */
package aplikasi;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.swing.JOptionPane;

/**
 * "Mesin" komunikasi ke Supabase lewat REST API (PostgREST), menggantikan
 * koneksi JDBC langsung ke database. Kelebihannya: aplikasi TIDAK PERNAH
 * memegang password database - hanya memegang "anon key" yang aksesnya
 * dibatasi lewat Row Level Security (RLS) di Supabase.
 *
 * Tidak butuh library tambahan sama sekali (tidak butuh driver .jar) karena
 * hanya memakai java.net.http bawaan Java 11+ dan parser JSON kecil buatan
 * sendiri (data di aplikasi ini sederhana, jadi tidak perlu library JSON
 * besar seperti Gson/Jackson).
 */
class SupabaseClient {

    private static String baseUrl;
    private static String apiKey;
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    /**
     * Membaca konfigurasi dari file "db.properties" (harus ada di folder
     * yang sama dengan aplikasi ini dijalankan).
     */
    private static void ensureConfigLoaded() {
        if (baseUrl != null) {
            return;
        }
        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(new File("db.properties"))) {
            props.load(fis);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null,
                    "File db.properties tidak ditemukan!\n"
                    + "Buat file \"db.properties\" di folder yang sama dengan aplikasi ini, isinya:\n\n"
                    + "supabase.url=https://xxxxxxxxxxxxx.supabase.co\n"
                    + "supabase.anonkey=isi_anon_key_dari_dashboard_supabase\n",
                    "Konfigurasi Database Tidak Ditemukan",
                    JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
        String url = props.getProperty("supabase.url", "").trim();
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        baseUrl = url + "/rest/v1";
        apiKey = props.getProperty("supabase.anonkey", "").trim();
    }

    /**
     * URL project Supabase tanpa akhiran "/rest/v1", dipakai RealtimeClient
     * untuk membangun URL koneksi WebSocket (wss://...).
     */
    static String getProjectUrl() {
        ensureConfigLoaded();
        return baseUrl.substring(0, baseUrl.length() - "/rest/v1".length());
    }

    /**
     * Anon key yang sama dipakai untuk otentikasi koneksi WebSocket
     * realtime, tidak beda dengan yang dipakai request REST biasa.
     */
    static String getApiKey() {
        ensureConfigLoaded();
        return apiKey;
    }

    private static HttpRequest.Builder requestTo(String path) {
        ensureConfigLoaded();
        return HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("apikey", apiKey)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json");
    }

    /**
     * SELECT * FROM table, urut berdasarkan orderColumn (ascending).
     */
    static List<Map<String, Object>> select(String table, String orderColumn) throws DbException {
        return select(table, orderColumn, null, null, -1);
    }

    /**
     * SELECT dengan filter kolom (WHERE filterColumn = filterValue),
     * opsional urut & limit (-1 = tanpa limit, desc = true untuk DESC).
     */
    static List<Map<String, Object>> select(String table, String orderColumn,
            String filterColumn, String filterValue, int limit) throws DbException {
        StringBuilder path = new StringBuilder("/" + table + "?select=*");
        if (filterColumn != null) {
            path.append("&").append(filterColumn).append("=eq.").append(urlEncode(filterValue));
        }
        if (orderColumn != null) {
            path.append("&order=").append(orderColumn);
        }
        if (limit > 0) {
            path.append("&limit=").append(limit);
        }

        HttpRequest req = requestTo(path.toString()).GET().build();
        HttpResponse<String> resp = send(req);
        checkOk(resp, "membaca data dari tabel " + table);
        Object parsed = Json.parse(resp.body());
        List<Map<String, Object>> result = new ArrayList<>();
        if (parsed instanceof List) {
            for (Object item : (List<?>) parsed) {
                if (item instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> row = (Map<String, Object>) item;
                    result.add(row);
                }
            }
        }
        return result;
    }

    /**
     * INSERT satu baris ke table.
     */
    static void insert(String table, Map<String, Object> data) throws DbException {
        String body = Json.write(data);
        HttpRequest req = requestTo("/" + table)
                .header("Prefer", "return=minimal")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = send(req);
        checkOk(resp, "menambah data ke tabel " + table);
    }

    /**
     * UPDATE baris yang filterColumn = filterValue.
     */
    static void update(String table, String filterColumn, String filterValue,
            Map<String, Object> data) throws DbException {
        String path = "/" + table + "?" + filterColumn + "=eq." + urlEncode(filterValue);
        String body = Json.write(data);
        HttpRequest req = requestTo(path)
                .header("Prefer", "return=minimal")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = send(req);
        checkOk(resp, "mengubah data di tabel " + table);
    }

    /**
     * DELETE baris yang filterColumn = filterValue.
     */
    static void delete(String table, String filterColumn, String filterValue) throws DbException {
        String path = "/" + table + "?" + filterColumn + "=eq." + urlEncode(filterValue);
        HttpRequest req = requestTo(path)
                .header("Prefer", "return=minimal")
                .DELETE()
                .build();
        HttpResponse<String> resp = send(req);
        checkOk(resp, "menghapus data di tabel " + table);
    }

    private static HttpResponse<String> send(HttpRequest req) throws DbException {
        try {
            return HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            throw new DbException("Tidak bisa terhubung ke server database:\n" + e.getMessage(), false);
        }
    }

    private static void checkOk(HttpResponse<String> resp, String aksi) throws DbException {
        int code = resp.statusCode();
        if (code >= 200 && code < 300) {
            return;
        }
        boolean duplicate = code == 409;
        String pesan = duplicate
                ? "Data duplikat (sudah ada sebelumnya)."
                : "Gagal " + aksi + " (HTTP " + code + "):\n" + resp.body();
        throw new DbException(pesan, duplicate);
    }

    private static String urlEncode(String value) {
        try {
            return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
        } catch (Exception e) {
            return value;
        }
    }

    /**
     * Parser & writer JSON minimalis, cukup untuk objek/array datar
     * (string, angka, boolean, null) seperti yang dipakai aplikasi ini.
     * Tidak butuh library eksternal.
     */
    static class Json {

        static String write(Map<String, Object> data) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                if (!first) {
                    sb.append(",");
                }
                first = false;
                sb.append(quote(entry.getKey())).append(":").append(writeValue(entry.getValue()));
            }
            sb.append("}");
            return sb.toString();
        }

        private static String writeValue(Object value) {
            if (value == null) {
                return "null";
            }
            if (value instanceof Number || value instanceof Boolean) {
                return value.toString();
            }
            return quote(value.toString());
        }

        private static String quote(String s) {
            StringBuilder sb = new StringBuilder("\"");
            for (char c : s.toCharArray()) {
                switch (c) {
                    case '"':
                        sb.append("\\\"");
                        break;
                    case '\\':
                        sb.append("\\\\");
                        break;
                    case '\n':
                        sb.append("\\n");
                        break;
                    case '\r':
                        sb.append("\\r");
                        break;
                    case '\t':
                        sb.append("\\t");
                        break;
                    default:
                        sb.append(c);
                }
            }
            sb.append("\"");
            return sb.toString();
        }

        static Object parse(String text) {
            Parser p = new Parser(text);
            p.skipWs();
            Object result = p.parseValue();
            return result;
        }

        private static class Parser {

            private final String s;
            private int i = 0;

            Parser(String s) {
                this.s = s;
            }

            void skipWs() {
                while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                    i++;
                }
            }

            Object parseValue() {
                skipWs();
                if (i >= s.length()) {
                    return null;
                }
                char c = s.charAt(i);
                if (c == '{') {
                    return parseObject();
                } else if (c == '[') {
                    return parseArray();
                } else if (c == '"') {
                    return parseString();
                } else if (c == 't' || c == 'f') {
                    return parseBoolean();
                } else if (c == 'n') {
                    i += 4; // null
                    return null;
                } else {
                    return parseNumber();
                }
            }

            Map<String, Object> parseObject() {
                Map<String, Object> map = new LinkedHashMap<>();
                i++; // {
                skipWs();
                if (i < s.length() && s.charAt(i) == '}') {
                    i++;
                    return map;
                }
                while (true) {
                    skipWs();
                    String key = parseString();
                    skipWs();
                    i++; // :
                    Object value = parseValue();
                    map.put(key, value);
                    skipWs();
                    if (i < s.length() && s.charAt(i) == ',') {
                        i++;
                    } else {
                        break;
                    }
                }
                skipWs();
                if (i < s.length() && s.charAt(i) == '}') {
                    i++;
                }
                return map;
            }

            List<Object> parseArray() {
                List<Object> list = new ArrayList<>();
                i++; // [
                skipWs();
                if (i < s.length() && s.charAt(i) == ']') {
                    i++;
                    return list;
                }
                while (true) {
                    Object value = parseValue();
                    list.add(value);
                    skipWs();
                    if (i < s.length() && s.charAt(i) == ',') {
                        i++;
                    } else {
                        break;
                    }
                }
                skipWs();
                if (i < s.length() && s.charAt(i) == ']') {
                    i++;
                }
                return list;
            }

            String parseString() {
                StringBuilder sb = new StringBuilder();
                i++; // "
                while (i < s.length() && s.charAt(i) != '"') {
                    char c = s.charAt(i);
                    if (c == '\\' && i + 1 < s.length()) {
                        char next = s.charAt(i + 1);
                        switch (next) {
                            case 'n':
                                sb.append('\n');
                                break;
                            case 'r':
                                sb.append('\r');
                                break;
                            case 't':
                                sb.append('\t');
                                break;
                            case '"':
                                sb.append('"');
                                break;
                            case '\\':
                                sb.append('\\');
                                break;
                            case '/':
                                sb.append('/');
                                break;
                            case 'u':
                                String hex = s.substring(i + 2, i + 6);
                                sb.append((char) Integer.parseInt(hex, 16));
                                i += 4;
                                break;
                            default:
                                sb.append(next);
                        }
                        i += 2;
                    } else {
                        sb.append(c);
                        i++;
                    }
                }
                i++; // closing "
                return sb.toString();
            }

            Boolean parseBoolean() {
                if (s.startsWith("true", i)) {
                    i += 4;
                    return Boolean.TRUE;
                } else {
                    i += 5;
                    return Boolean.FALSE;
                }
            }

            Object parseNumber() {
                int start = i;
                while (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '-'
                        || s.charAt(i) == '+' || s.charAt(i) == '.' || s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
                    i++;
                }
                String numStr = s.substring(start, i);
                if (numStr.contains(".") || numStr.contains("e") || numStr.contains("E")) {
                    return Double.parseDouble(numStr);
                }
                return Long.parseLong(numStr);
            }
        }
    }
}
