package com.cryptobot.marketdata;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParallelFetchTest {

    @Test
    void separatesSuccessesFromFailuresWithoutOneCancellingTheOther() {
        List<ParallelFetch.FetchTask<String, String>> tasks = List.of(
            new ParallelFetch.FetchTask<>("a", "ExchangeA", () -> "ok-a"),
            new ParallelFetch.FetchTask<>("b", "ExchangeA", () -> { throw new RuntimeException("boom"); }),
            new ParallelFetch.FetchTask<>("c", "ExchangeB", () -> "ok-c")
        );

        ParallelFetch.Outcome<String, String> outcome = ParallelFetch.fetchAll(tasks);

        assertEquals(2, outcome.results().size());
        assertEquals("ok-a", outcome.results().get("a"));
        assertEquals("ok-c", outcome.results().get("c"));
        assertEquals(1, outcome.errors().size());
        assertEquals("boom", outcome.errors().get("b"));
    }

    @Test
    void neverExceedsTheConcurrencyLimitWithinTheSameExchange() {
        int taskCount = 20;
        AtomicInteger inFlight = new AtomicInteger(0);
        AtomicInteger maxObserved = new AtomicInteger(0);

        List<ParallelFetch.FetchTask<Integer, Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < taskCount; i++) {
            int id = i;
            tasks.add(new ParallelFetch.FetchTask<>(id, "SameExchange", () -> {
                int now = inFlight.incrementAndGet();
                maxObserved.updateAndGet(prev -> Math.max(prev, now));
                Thread.sleep(60);
                inFlight.decrementAndGet();
                return id;
            }));
        }

        ParallelFetch.Outcome<Integer, Integer> outcome = ParallelFetch.fetchAll(tasks);

        assertEquals(taskCount, outcome.results().size());
        assertTrue(maxObserved.get() <= 8, "el máximo en vuelo fue " + maxObserved.get() + ", esperaba <= 8");
        assertTrue(maxObserved.get() >= 2, "con 20 tareas y sleep, debería haberse solapado más de una");
    }

    @Test
    void exchangesDoNotBlockEachOther() {
        AtomicInteger inFlightA = new AtomicInteger(0);
        AtomicInteger inFlightB = new AtomicInteger(0);
        AtomicInteger maxCombined = new AtomicInteger(0);

        List<ParallelFetch.FetchTask<String, String>> tasks = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            String key = "a" + i;
            tasks.add(new ParallelFetch.FetchTask<>(key, "ExchangeA", () -> {
                inFlightA.incrementAndGet();
                maxCombined.updateAndGet(prev -> Math.max(prev, inFlightA.get() + inFlightB.get()));
                Thread.sleep(60);
                inFlightA.decrementAndGet();
                return key;
            }));
        }
        for (int i = 0; i < 8; i++) {
            String key = "b" + i;
            tasks.add(new ParallelFetch.FetchTask<>(key, "ExchangeB", () -> {
                inFlightB.incrementAndGet();
                maxCombined.updateAndGet(prev -> Math.max(prev, inFlightA.get() + inFlightB.get()));
                Thread.sleep(60);
                inFlightB.decrementAndGet();
                return key;
            }));
        }

        ParallelFetch.Outcome<String, String> outcome = ParallelFetch.fetchAll(tasks);

        assertEquals(16, outcome.results().size());
        // si se bloquearan entre sí, el combinado nunca pasaría de 8 (el límite de un solo exchange)
        assertTrue(maxCombined.get() > 8,
            "esperaba que ExchangeA y ExchangeB corrieran a la vez, combinado en vuelo fue " + maxCombined.get());
    }
}
