package com.cryptobot.marketdata;

import com.cryptobot.marketdata.buda.BudaConnector;
import com.cryptobot.marketdata.coinex.CoinExConnector;
import com.cryptobot.marketdata.notbank.NotBankConnector;
import com.cryptobot.marketdata.poloniex.PoloniexConnector;
import com.cryptobot.marketdata.yobit.YobitConnector;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.cryptobot.marketdata.TrackedAsset.Venue;

/**
 * Registro único de qué activo cotiza en qué exchange, con qué símbolo —
 * usado tanto por {@code OverlapCheck} (foto única) como por
 * {@code SpreadWatcher} (corrida continua). Antes esta información era una
 * lista fija de 11 activos elegidos a mano (Sprint 0007) — desde el Sprint
 * 0017 se descubre en vivo contra las 4 APIs, mismo principio ya aplicado a
 * lo triangular ({@code TriangleFinder}/{@code CrossTriangleFinder}).
 */
public final class TrackedAssets {

    // Monedas de cotización soportadas — las mismas para las que
    // MinNotional ya tiene un umbral verificado. Se excluyen a propósito
    // COP/PEN/ARS/BRL (existen en NotBank/Buda): no hay un umbral de
    // nocional confirmado para ellas, y usar el default de USDT asumiría
    // que valen lo mismo, que no es cierto (Sprint 0017).
    private static final Set<String> SUPPORTED_QUOTES = Set.of("USDT", "USDC", "CLP", "BTC");

    private TrackedAssets() {
    }

    public static List<TrackedAsset> all(PoloniexConnector poloniex, NotBankConnector notbank,
                                          BudaConnector buda, YobitConnector yobit, CoinExConnector coinex) {
        List<CrossVenue> venues = new ArrayList<>();
        for (Market m : poloniex.fetchMarkets()) {
            venues.add(new CrossVenue(poloniex, m));
        }
        for (Market m : notbank.fetchMarkets()) {
            venues.add(new CrossVenue(notbank, m));
        }
        for (Market m : buda.fetchMarkets()) {
            venues.add(new CrossVenue(buda, m));
        }
        for (Market m : yobit.fetchMarkets()) {
            venues.add(new CrossVenue(yobit, m));
        }
        for (Market m : coinex.fetchMarkets()) {
            venues.add(new CrossVenue(coinex, m));
        }
        return discover(venues);
    }

    /**
     * Agrupa por "{base}/{quote}" exacto — a diferencia de
     * {@code TriangleFinder}, acá el orden importa, no es una pata de
     * triángulo intercambiable. Un activo entra a la lista final solo si
     * aparece en 2 o más exchanges: con uno solo no hay nada que comparar.
     */
    static List<TrackedAsset> discover(List<CrossVenue> venues) {
        Map<String, List<CrossVenue>> byLabel = new LinkedHashMap<>();
        for (CrossVenue v : venues) {
            Market m = v.market();
            if (!SUPPORTED_QUOTES.contains(m.quote())) {
                continue;
            }
            String label = m.base() + "/" + m.quote();
            byLabel.computeIfAbsent(label, k -> new ArrayList<>()).add(v);
        }

        List<TrackedAsset> result = new ArrayList<>();
        for (Map.Entry<String, List<CrossVenue>> entry : byLabel.entrySet()) {
            List<CrossVenue> group = entry.getValue();
            long distinctExchanges = group.stream().map(CrossVenue::exchangeName).distinct().count();
            if (distinctExchanges < 2) {
                continue;
            }
            String label = entry.getKey();
            String quote = label.substring(label.indexOf('/') + 1);
            List<Venue> venueList = new ArrayList<>();
            for (CrossVenue v : group) {
                venueList.add(new Venue(v.connector(), v.market().symbol()));
            }
            result.add(new TrackedAsset(label, MinNotional.forCurrency(quote), venueList));
        }
        return result;
    }
}
