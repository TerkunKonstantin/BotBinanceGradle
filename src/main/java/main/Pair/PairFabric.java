package main.Pair;

import com.binance.api.client.domain.general.SymbolInfo;
import main.Listeners.DepthCacheUpdateer;
import main.Listeners.OrderListUpdateer;
import main.Listeners.TickerUpdateer;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class PairFabric {

    private static final Logger log = Logger.getLogger(TradeStarter.class);
    private List<DepthCacheUpdateer> depthCacheUpdateerList = new ArrayList<>();
    private OrderListUpdateer orderListUpdateer;
    private TickerUpdateer tickerUpdateer;
    public Map<String, CurrencyPair> pricePairHashMap;
    private List<SymbolInfo> symbolInfoList;


    public PairFabric(List<SymbolInfo> symbolInfoList) {
        this.symbolInfoList = symbolInfoList;
        pricePairHashMap = createPairHashMap(symbolInfoList);
    }

    private Map<String, CurrencyPair> createPairHashMap(List<SymbolInfo> symbolInfoList){
        return symbolInfoList.stream().collect(Collectors.toMap(
                symbolInfo -> symbolInfo.getBaseAsset() + "BTC",
                CurrencyPair::new));
    }


    public void addPairListeners() {
        addOrderBookListener(pricePairHashMap);
        addOrderListener(pricePairHashMap);
        addTickerListener(pricePairHashMap);
    }


    private void addOrderBookListener(Map<String, CurrencyPair> pricePairHashMap) {
        for (Map.Entry<String, CurrencyPair> entry : pricePairHashMap.entrySet()) {
            depthCacheUpdateerList.add(new DepthCacheUpdateer(entry.getKey(), entry.getValue()));
        }
    }

    private void addOrderListener(Map<String, CurrencyPair> pricePairHashMap) {
       orderListUpdateer = new OrderListUpdateer(pricePairHashMap);
    }

    private void addTickerListener(Map<String, CurrencyPair> pricePairHashMap) {
        tickerUpdateer = new TickerUpdateer(pricePairHashMap);
    }


    Map<String, CurrencyPair> reInitialization() {

        killOrderBookListener();
        killOrderListener();
        killTickerListener();

        pricePairHashMap = createPairHashMap(symbolInfoList);

        addOrderBookListener(pricePairHashMap);
        addOrderListener(pricePairHashMap);
        addTickerListener(pricePairHashMap);

        return pricePairHashMap;

    }


    private void killOrderBookListener() {
        for (DepthCacheUpdateer depthCacheUpdateer : depthCacheUpdateerList) {
            try {
                depthCacheUpdateer.closeable.close();
                depthCacheUpdateer.scheduledFuture.cancel(true);
            } catch (Exception e) {
                log.error(e.getStackTrace());
                e.printStackTrace();
            }
        }
    }

    private void killOrderListener() {
        try {
            orderListUpdateer.closeable.close();
            orderListUpdateer.scheduledFuture.cancel(true);
        } catch (Exception e) {
            log.error(e.getStackTrace());
            e.printStackTrace();
        }
    }

    private void killTickerListener() {
        try {
            tickerUpdateer.closeable.close();
            tickerUpdateer.scheduledFuture.cancel(true);
        } catch (Exception e) {
            log.error(e.getStackTrace());
            e.printStackTrace();
        }
    }

}
