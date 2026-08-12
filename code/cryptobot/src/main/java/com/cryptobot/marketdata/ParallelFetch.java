package com.cryptobot.marketdata;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

/**
 * Ejecuta varias tareas de fetch en paralelo — pensada para pedir muchos
 * order books a la vez en vez de uno a la vez (Sprint 0014, nació al ver
 * que {@code CrossTriangleWatcher} tardaba notoriamente más con sus 168
 * books secuenciales que los otros watchers).
 *
 * Sin límite entre exchanges distintos (APIs y rate limits independientes,
 * no hay riesgo real). Acotado dentro de un mismo exchange con un
 * semáforo — ninguno de los 4 exchanges tiene su rate limit real
 * confirmado todavía (ver docs/entorno.md), así que el límite es un
 * supuesto conservador, no un dato medido.
 *
 * Los conectores siguen exactamente como están (HTTP síncrono, bloqueante)
 * — virtual threads (JDK 21) hacen que correr muchas llamadas bloqueantes
 * en paralelo no necesite reescribirlos a async.
 */
public final class ParallelFetch {

    private static final int MAX_CONCURRENT_PER_EXCHANGE = 8;

    public record FetchTask<K, V>(K key, String exchangeName, Callable<V> fetch) {
    }

    public record Outcome<K, V>(Map<K, V> results, Map<K, String> errors) {
    }

    private ParallelFetch() {
    }

    public static <K, V> Outcome<K, V> fetchAll(List<FetchTask<K, V>> tasks) {
        Map<String, Semaphore> semaphoresByExchange = new ConcurrentHashMap<>();
        Map<K, V> results = new ConcurrentHashMap<>();
        Map<K, String> errors = new ConcurrentHashMap<>();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>();
            for (FetchTask<K, V> task : tasks) {
                Semaphore semaphore = semaphoresByExchange.computeIfAbsent(
                    task.exchangeName(), name -> new Semaphore(MAX_CONCURRENT_PER_EXCHANGE));
                futures.add(executor.submit(() -> runOne(task, semaphore, results, errors)));
            }
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (Exception ignored) {
                    // el error real de la tarea ya quedó en `errors` — acá solo esperamos que termine
                }
            }
        }

        return new Outcome<>(results, errors);
    }

    private static <K, V> void runOne(FetchTask<K, V> task, Semaphore semaphore,
                                       Map<K, V> results, Map<K, String> errors) {
        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            errors.put(task.key(), "interrumpido esperando turno para " + task.exchangeName());
            return;
        }
        try {
            results.put(task.key(), task.fetch().call());
        } catch (Exception e) {
            errors.put(task.key(), String.valueOf(e.getMessage()));
        } finally {
            semaphore.release();
        }
    }
}
