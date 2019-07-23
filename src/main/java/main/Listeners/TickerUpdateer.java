package main.Listeners;

import com.binance.api.client.BinanceApiAsyncRestClient;
import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiWebSocketClient;
import com.binance.api.client.domain.event.AllMarketTickersEvent;
import main.Config;
import main.Pair.CurrencyPair;

import java.io.Closeable;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;


public class TickerUpdateer {

    public final BinanceApiClientFactory factory = BinanceApiClientFactory.newInstance(Config.getApiKeyB(), Config.getSecretKeyB());
    public final BinanceApiWebSocketClient webSocketClient = factory.newWebSocketClient();
    public final BinanceApiAsyncRestClient asyncRestClient = factory.newAsyncRestClient();
    public Map<String, CurrencyPair> pricePairHashMap;

    public TickerUpdateer(Map<String, CurrencyPair> pricePairHashMap) {
        this.pricePairHashMap = pricePairHashMap;
        pricePairHashMap.forEach((k, v) ->
                asyncRestClient.get24HrPriceStatistics(k, response ->
                        {
                            v.askPrice = new BigDecimal(response.getAskPrice());
                            v.bidPrice = new BigDecimal(response.getBidPrice());
                            v.lowPrice = new BigDecimal(response.getLowPrice());
                            v.hightPrice = new BigDecimal(response.getHighPrice());
                        }
                )
        );
        startTickerListener();

    }


    public void startTickerListener() {
        ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor();
        AtomicReference<Closeable> atomicReference = new AtomicReference<>();
        service.scheduleAtFixedRate(() -> {
            try {
                Closeable webSocket = atomicReference.get();
                if (Objects.nonNull(webSocket)) {
                    webSocket.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            atomicReference.set(
                    webSocketClient.onAllMarketTickersEvent(response -> {
                        for (AllMarketTickersEvent allMarketTickersEvent : response) {
                            String symbol = allMarketTickersEvent.getSymbol();
                            pricePairHashMap.get(symbol).hightPrice = new BigDecimal(allMarketTickersEvent.getHighPrice());
                            pricePairHashMap.get(symbol).lowPrice = new BigDecimal(allMarketTickersEvent.getLowPrice());
                            pricePairHashMap.get(symbol).askPrice = new BigDecimal(allMarketTickersEvent.getBestAskPrice());
                            pricePairHashMap.get(symbol).bidPrice = new BigDecimal(allMarketTickersEvent.getBestBidPrice());
                        }
                    }));
        }, 0, 10, TimeUnit.MINUTES);
    }
}
