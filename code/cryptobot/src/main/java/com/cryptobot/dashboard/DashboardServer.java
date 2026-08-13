package com.cryptobot.dashboard;

import com.cryptobot.report.WatchHealthAnalyzer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Servidor web local de solo lectura sobre los CSV que escriben los 6
 * watchers — Sprint 0026. Sirve el visor en {@code /} y dos endpoints
 * JSON. Cada request relee el archivo desde disco tal cual está en ese
 * momento (los watchers siguen escribiendo mientras el dashboard corre);
 * sin caché, sin autenticación, sin refresco automático — botón manual en
 * la UI, para no sumar carga de lectura repetida sobre archivos que ya
 * van en cientos de miles de filas.
 *
 * La serialización JSON usa el {@code ObjectMapper} de Jackson que ya es
 * dependencia del proyecto, pero contra árboles {@code Map}/{@code List}
 * armados a mano (no serialización automática de los records) para no
 * sumar la dependencia jackson-datatype-jsr310 que haría falta para
 * serializar {@code Instant}/{@code Duration} directamente.
 *
 * Uso: mvn exec:java -Dexec.mainClass=com.cryptobot.dashboard.DashboardServer
 *      Abrir http://localhost:8089 (puerto 8080 puede estar ocupado por
 *      otros proyectos locales — confirmado en la práctica, Sprint 0026)
 */
public class DashboardServer {

    private static final int PORT = 8089;
    private static final Path DATA_DIR = Path.of("data");
    private static final int TOP_N_HEALTH_DETAIL = 15;

    private static final ObjectMapper JSON = new ObjectMapper();

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", PORT), 0);
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.createContext("/", DashboardServer::serveIndex);
        server.createContext("/api/files", DashboardServer::serveFiles);
        server.createContext("/api/dashboard", DashboardServer::serveDashboard);
        server.start();
        System.out.println("Dashboard arriba en http://localhost:" + PORT);
        System.out.println("Leyendo CSV desde: " + DATA_DIR.toAbsolutePath());
    }

    private static void serveIndex(HttpExchange exchange) throws IOException {
        try (InputStream in = DashboardServer.class.getResourceAsStream("/dashboard/index.html")) {
            if (in == null) {
                respondText(exchange, 500, "No se encontró dashboard/index.html en el classpath");
                return;
            }
            byte[] body = in.readAllBytes();
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        }
    }

    private static void serveFiles(HttpExchange exchange) throws IOException {
        Map<String, Object> result = new LinkedHashMap<>();
        for (WatcherFormats.WatcherFormat format : WatcherFormats.all()) {
            result.put(format.filePrefix(), listFiles(format.filePrefix()));
        }
        respondJson(exchange, 200, result);
    }

    private static List<String> listFiles(String prefix) throws IOException {
        if (!Files.isDirectory(DATA_DIR)) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(DATA_DIR, prefix + "*.csv")) {
            for (Path p : stream) {
                names.add(p.getFileName().toString());
            }
        }
        names.sort(Comparator.reverseOrder());
        return names;
    }

    private static void serveDashboard(HttpExchange exchange) throws IOException {
        Map<String, List<String>> query = parseQuery(exchange.getRequestURI());
        List<String> fileParam = query.get("file");
        if (fileParam == null || fileParam.isEmpty() || fileParam.get(0).isBlank()) {
            respondJson(exchange, 400, Map.of("error", "Falta el parámetro file"));
            return;
        }
        String fileName = fileParam.get(0);
        if (fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) {
            respondJson(exchange, 400, Map.of("error", "Nombre de archivo inválido"));
            return;
        }
        Optional<WatcherFormats.WatcherFormat> format = WatcherFormats.forFileName(fileName);
        if (format.isEmpty()) {
            respondJson(exchange, 400, Map.of("error", "No coincide con ningún watcher conocido: " + fileName));
            return;
        }
        Path path = DATA_DIR.resolve(fileName);
        if (!Files.exists(path)) {
            respondJson(exchange, 404, Map.of("error", "No existe: " + fileName));
            return;
        }

        Map<String, Object> body = new LinkedHashMap<>();
        try {
            try (BufferedReader healthReader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                body.put("health", toJson(WatchHealthAnalyzer.analyze(healthReader)));
            }
            try (BufferedReader seriesReader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                body.put("series", toJson(CombinationSeriesAnalyzer.analyze(seriesReader, format.get())));
            }
        } catch (IllegalArgumentException e) {
            respondJson(exchange, 400, Map.of("error", e.getMessage()));
            return;
        }
        respondJson(exchange, 200, body);
    }

    private static Map<String, Object> toJson(WatchHealthAnalyzer.Result r) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("rowCount", r.rowCount());
        json.put("malformedRowCount", r.malformedRowCount());
        json.put("cycleCount", r.cycleCount());
        r.cycleStats().ifPresent(stats -> {
            Map<String, Object> cs = new LinkedHashMap<>();
            cs.put("medianSeconds", stats.median().getSeconds());
            cs.put("minSeconds", stats.min().getSeconds());
            cs.put("maxSeconds", stats.max().getSeconds());
            json.put("cycleStats", cs);
        });
        json.put("gapCount", r.gaps().size());
        json.put("flagCounts", r.flagCounts());
        json.put("errorRowCount", r.errorRowCount());
        json.put("errorMessageCounts", topN(r.errorMessageCounts(), TOP_N_HEALTH_DETAIL));
        json.put("errorMessageOmittedCount", Math.max(0, r.errorMessageCounts().size() - TOP_N_HEALTH_DETAIL));
        json.put("staleTokenCounts", topN(r.staleTokenCounts(), TOP_N_HEALTH_DETAIL));
        json.put("staleTokenOmittedCount", Math.max(0, r.staleTokenCounts().size() - TOP_N_HEALTH_DETAIL));
        return json;
    }

    private static Map<String, Object> toJson(CombinationSeriesAnalyzer.Result r) {
        List<Map<String, Object>> combos = new ArrayList<>();
        for (CombinationSeriesAnalyzer.Combination c : r.combinations()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("label", c.label());
            m.put("appearances", c.appearances());
            m.put("flaggedCount", c.flaggedCount());
            m.put("consistencyPct", c.consistencyPct());
            m.put("possibleTickerCollision", c.possibleTickerCollision());
            List<Map<String, Object>> points = new ArrayList<>();
            for (CombinationSeriesAnalyzer.Point p : c.series()) {
                Map<String, Object> pm = new LinkedHashMap<>();
                pm.put("timestamp", p.timestamp().toString());
                pm.put("value", p.value());
                points.add(pm);
            }
            m.put("series", points);
            combos.add(m);
        }
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("combinations", combos);
        json.put("omittedCount", r.omittedCount());
        return json;
    }

    private static Map<String, Long> topN(Map<String, Long> counts, int limit) {
        return counts.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(limit)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
    }

    private static Map<String, List<String>> parseQuery(URI uri) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        String query = uri.getRawQuery();
        if (query == null || query.isBlank()) {
            return result;
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            String key = eq >= 0 ? pair.substring(0, eq) : pair;
            String value = eq >= 0 ? pair.substring(eq + 1) : "";
            key = URLDecoder.decode(key, StandardCharsets.UTF_8);
            value = URLDecoder.decode(value, StandardCharsets.UTF_8);
            result.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
        }
        return result;
    }

    private static void respondJson(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = JSON.writeValueAsBytes(body);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void respondText(HttpExchange exchange, int status, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
