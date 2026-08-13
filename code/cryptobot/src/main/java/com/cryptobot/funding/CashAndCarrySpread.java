package com.cryptobot.funding;

import com.cryptobot.marketdata.ExchangeFees;
import com.cryptobot.marketdata.OrderBook;
import com.cryptobot.marketdata.PerpQuote;
import com.cryptobot.marketdata.PriceLevel;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Viabilidad de un cash-and-carry (spot largo + corto en el perpetuo) — a
 * diferencia de {@link com.cryptobot.marketdata.NetSpread}/{@link com.cryptobot.triangular.TriangleSpread}
 * (arbitraje instantáneo, se resuelve en un solo número), acá no hay un
 * "neto" único: es un rendimiento periódico (funding) contra un costo de
 * entrada único. Se reportan por separado — basis, funding por período,
 * funding anualizado, fees de entrada, y cuántos períodos hacen falta para
 * recuperar la entrada — sin colapsar en un solo booleano.
 */
public final class CashAndCarrySpread {

    private static final MathContext MC = new MathContext(20);
    private static final BigDecimal HOURS_PER_YEAR = BigDecimal.valueOf(365L * 24);

    /** Un candidato de spot para la pata larga — un exchange y su order book. */
    public record SpotCandidate(String exchangeName, OrderBook book) {
    }

    public record Result(String asset,
                          String spotExchange, BigDecimal spotAskPrice,
                          String perpExchange, BigDecimal perpBidPrice,
                          BigDecimal basisPct, BigDecimal fundingRatePct, BigDecimal fundingIntervalHours,
                          BigDecimal annualizedFundingPct, BigDecimal entryFeesPct,
                          BigDecimal breakevenPeriods) {
        /** Vacío si el funding actual no es positivo — nunca se recupera la entrada solo con funding. */
        public Optional<BigDecimal> breakevenPeriodsIfPositive() {
            return breakevenPeriods == null ? Optional.empty() : Optional.of(breakevenPeriods);
        }
    }

    private CashAndCarrySpread() {
    }

    /**
     * @return vacío si ningún candidato de spot tiene liquidez suficiente, o
     * si el perpetuo no la tiene en su mejor bid.
     */
    public static Optional<Result> evaluate(String asset, List<SpotCandidate> spotCandidates,
                                             String perpExchange, PerpQuote perp, BigDecimal minNotionalUsdt) {
        SpotCandidate bestSpot = null;
        PriceLevel bestSpotAsk = null;
        BigDecimal bestNetAskCost = null;

        for (SpotCandidate candidate : spotCandidates) {
            PriceLevel ask = candidate.book().bestAskAbove(minNotionalUsdt);
            if (ask == null) {
                continue;
            }
            BigDecimal fee = ExchangeFees.takerFee(candidate.exchangeName(), "USDT");
            BigDecimal netAskCost = ask.price().multiply(BigDecimal.ONE.add(fee), MC);
            if (bestNetAskCost == null || netAskCost.compareTo(bestNetAskCost) < 0) {
                bestSpot = candidate;
                bestSpotAsk = ask;
                bestNetAskCost = netAskCost;
            }
        }
        if (bestSpot == null) {
            return Optional.empty();
        }

        PriceLevel perpBid = perp.bestBid();
        if (perpBid == null || perpBid.price().multiply(perpBid.quantity()).compareTo(minNotionalUsdt) < 0) {
            return Optional.empty();
        }

        BigDecimal spotAskPrice = bestSpotAsk.price();
        BigDecimal basisPct = percent(perpBid.price().subtract(spotAskPrice), spotAskPrice);

        BigDecimal spotFee = ExchangeFees.takerFee(bestSpot.exchangeName(), "USDT");
        BigDecimal perpFee = ExchangeFees.perpTakerFee(perpExchange);
        BigDecimal entryFeesPct = spotFee.add(perpFee).multiply(BigDecimal.valueOf(100));

        Duration interval = perp.fundingInterval();
        BigDecimal intervalHours = BigDecimal.valueOf(interval.toSeconds()).divide(BigDecimal.valueOf(3600), MC);
        BigDecimal periodsPerYear = HOURS_PER_YEAR.divide(intervalHours, MC);
        BigDecimal annualizedFundingPct = perp.fundingRatePct().multiply(periodsPerYear, MC);

        BigDecimal breakevenPeriods = null;
        if (perp.fundingRatePct().signum() > 0) {
            BigDecimal remainingAfterBasis = entryFeesPct.subtract(basisPct).max(BigDecimal.ZERO);
            breakevenPeriods = remainingAfterBasis.divide(perp.fundingRatePct(), MC);
        }

        return Optional.of(new Result(asset, bestSpot.exchangeName(), spotAskPrice,
            perpExchange, perpBid.price(), basisPct, perp.fundingRatePct(), intervalHours,
            annualizedFundingPct, entryFeesPct, breakevenPeriods));
    }

    public static String bookKey(String exchangeName, String symbol) {
        return exchangeName + "|" + symbol;
    }

    private static BigDecimal percent(BigDecimal amount, BigDecimal base) {
        return amount.divide(base, 6, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
    }
}
