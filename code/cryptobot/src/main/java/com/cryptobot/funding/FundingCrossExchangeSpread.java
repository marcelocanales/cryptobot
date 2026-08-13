package com.cryptobot.funding;

import com.cryptobot.marketdata.ExchangeFees;
import com.cryptobot.marketdata.PerpQuote;
import com.cryptobot.marketdata.PriceLevel;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Viabilidad de un funding rate cross-exchange (hipótesis 05) — corto en
 * el perpetuo con funding más alto, largo en el de funding más bajo,
 * delta-neutral, cobrando el diferencial. A diferencia de
 * {@link CashAndCarrySpread} (una pata spot, una perpetuo), acá **las dos
 * patas son perpetuos**, cada una en un exchange distinto — ver
 * docs/estrategias/05-funding-rate-cross-exchange.md.
 *
 * Diseño "mejor de N candidatos" desde el arranque (no un bolt-on de 2
 * exchanges), mismo principio ya aplicado a {@code CashAndCarrySpread}
 * para el lado spot: entre todos los candidatos con liquidez suficiente,
 * el de mayor funding anualizado es el corto, el de menor (excluyendo el
 * mismo exchange del corto) es el largo — óptimo por construcción.
 */
public final class FundingCrossExchangeSpread {

    private static final MathContext MC = new MathContext(20);
    private static final BigDecimal HOURS_PER_YEAR = BigDecimal.valueOf(365L * 24);

    /** Un candidato de perpetuo — un exchange y su cotización. */
    public record PerpCandidate(String exchangeName, PerpQuote quote) {
    }

    public record Result(String asset,
                          String shortExchange, BigDecimal shortAnnualizedFundingPct,
                          String longExchange, BigDecimal longAnnualizedFundingPct,
                          BigDecimal annualizedDifferentialPct,
                          BigDecimal entryFeesPct,
                          BigDecimal breakevenHours) {
        /** Vacío si el diferencial anualizado no es positivo — nunca se recupera la entrada solo con el diferencial. */
        public Optional<BigDecimal> breakevenHoursIfPositive() {
            return breakevenHours == null ? Optional.empty() : Optional.of(breakevenHours);
        }
    }

    private record AnnualizedCandidate(PerpCandidate candidate, BigDecimal annualizedPct) {
    }

    private FundingCrossExchangeSpread() {
    }

    /**
     * @return vacío si no hay 2 exchanges distintos con liquidez suficiente
     * (bid para el lado corto, ask para el largo) para este activo.
     */
    public static Optional<Result> evaluate(String asset, List<PerpCandidate> candidates, BigDecimal minNotional) {
        List<AnnualizedCandidate> shortEligible = new ArrayList<>();
        List<AnnualizedCandidate> longEligible = new ArrayList<>();

        for (PerpCandidate c : candidates) {
            PerpQuote q = c.quote();
            BigDecimal annualized = annualize(q);

            PriceLevel bid = q.bestBid();
            if (bid != null && bid.price().multiply(bid.quantity()).compareTo(minNotional) >= 0) {
                shortEligible.add(new AnnualizedCandidate(c, annualized));
            }
            PriceLevel ask = q.bestAsk();
            if (ask != null && ask.price().multiply(ask.quantity()).compareTo(minNotional) >= 0) {
                longEligible.add(new AnnualizedCandidate(c, annualized));
            }
        }
        if (shortEligible.isEmpty() || longEligible.isEmpty()) {
            return Optional.empty();
        }

        AnnualizedCandidate best = null; // corto: cobra el funding más alto
        for (AnnualizedCandidate c : shortEligible) {
            if (best == null || c.annualizedPct().compareTo(best.annualizedPct()) > 0) {
                best = c;
            }
        }

        AnnualizedCandidate worst = null; // largo: paga poco o cobra también, en OTRO exchange
        for (AnnualizedCandidate c : longEligible) {
            if (c.candidate().exchangeName().equals(best.candidate().exchangeName())) {
                continue; // cubrirse consigo mismo no es cross-exchange
            }
            if (worst == null || c.annualizedPct().compareTo(worst.annualizedPct()) < 0) {
                worst = c;
            }
        }
        if (worst == null) {
            return Optional.empty(); // solo un exchange tenía liquidez suficiente en ambas patas
        }

        BigDecimal differential = best.annualizedPct().subtract(worst.annualizedPct(), MC);

        BigDecimal shortFee = ExchangeFees.perpTakerFee(best.candidate().exchangeName());
        BigDecimal longFee = ExchangeFees.perpTakerFee(worst.candidate().exchangeName());
        BigDecimal entryFeesPct = shortFee.add(longFee).multiply(BigDecimal.valueOf(100));

        BigDecimal breakevenHours = null;
        if (differential.signum() > 0) {
            BigDecimal hourlyDifferentialPct = differential.divide(HOURS_PER_YEAR, MC);
            breakevenHours = entryFeesPct.divide(hourlyDifferentialPct, MC);
        }

        return Optional.of(new Result(asset,
            best.candidate().exchangeName(), best.annualizedPct(),
            worst.candidate().exchangeName(), worst.annualizedPct(),
            differential, entryFeesPct, breakevenHours));
    }

    /** Anualiza con el intervalo REAL de cada candidato — no asume que coinciden entre exchanges. */
    private static BigDecimal annualize(PerpQuote quote) {
        Duration interval = quote.fundingInterval();
        BigDecimal intervalHours = BigDecimal.valueOf(interval.toSeconds()).divide(BigDecimal.valueOf(3600), MC);
        BigDecimal periodsPerYear = HOURS_PER_YEAR.divide(intervalHours, MC);
        return quote.fundingRatePct().multiply(periodsPerYear, MC);
    }
}
