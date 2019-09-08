package main.Listeners;

import com.binance.api.client.BinanceApiAsyncRestClient;
import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiWebSocketClient;
import com.binance.api.client.domain.OrderSide;
import com.binance.api.client.domain.account.Order;
import com.binance.api.client.domain.event.AllMarketTickersEvent;
import main.Config;
import main.Pair.CurrencyPair;
import org.apache.log4j.Logger;

import java.io.Closeable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;


public class TickerUpdateer {

    private static final Logger log = Logger.getLogger(TickerUpdateer.class);

    private final BinanceApiClientFactory factory = BinanceApiClientFactory.newInstance(Config.getApiKeyB(), Config.getSecretKeyB());
    private final BinanceApiWebSocketClient webSocketClient = factory.newWebSocketClient();
    private final BinanceApiAsyncRestClient asyncRestClient = factory.newAsyncRestClient();
    private Map<String, CurrencyPair> pricePairHashMap;
    public Closeable closeable;
    public ScheduledFuture<?> scheduledFuture;

    public TickerUpdateer(Map<String, CurrencyPair> pricePairHashMap) {
        this.pricePairHashMap = pricePairHashMap;
        pricePairHashMap.forEach((k, v) ->
                asyncRestClient.get24HrPriceStatistics(k, response ->
                        {
                            v.price = new BigDecimal(response.getLastPrice());
                            v.askPrice = new BigDecimal(response.getAskPrice());
                            v.bidPrice = new BigDecimal(response.getBidPrice());
                            v.lowPrice = new BigDecimal(response.getLowPrice());
                            v.hightPrice = new BigDecimal(response.getHighPrice());
                        }
                )
        );
        startTickerListener();

    }


    private void startTickerListener() {
        ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor();
        AtomicReference<Closeable> atomicReference = new AtomicReference<>();
        scheduledFuture = service.scheduleAtFixedRate(() -> {
            try {
                Closeable webSocket = atomicReference.get();
                if (Objects.nonNull(webSocket)) {
                    webSocket.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            closeable = webSocketClient.onAllMarketTickersEvent(response -> {
                try {
                    for (AllMarketTickersEvent allMarketTickersEvent : response) {
                        String symbol = allMarketTickersEvent.getSymbol();
                        CurrencyPair currencyPair = pricePairHashMap.get(symbol);
                        if (currencyPair != null) {
                            currencyPair.price = new BigDecimal(allMarketTickersEvent.getCurrentDaysClosePrice());
                            currencyPair.hightPrice = new BigDecimal(allMarketTickersEvent.getHighPrice());
                            currencyPair.lowPrice = new BigDecimal(allMarketTickersEvent.getLowPrice());
                            currencyPair.askPrice = new BigDecimal(allMarketTickersEvent.getBestAskPrice());
                            currencyPair.bidPrice = new BigDecimal(allMarketTickersEvent.getBestBidPrice());

                            List<Order> buyList = currencyPair.orderList
                                    .stream()
                                    .filter(e -> e.getSide() == OrderSide.BUY)
                                    .collect(Collectors.toList());
                            if (!buyList.isEmpty())
                                currencyPair.checkOrderList(buyList);


                        }
                    }
                }catch(Exception e){
                    log.error(e.getStackTrace());
                    e.printStackTrace();
                }
            });
            atomicReference.set(closeable);
        }, 0, 10, TimeUnit.MINUTES);
    }
}
