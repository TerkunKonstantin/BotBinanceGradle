package main.Pair;

import com.binance.api.client.BinanceApiWebSocketClient;
import com.binance.api.client.domain.general.SymbolInfo;
import main.Listeners.DepthCacheUpdateer;
import main.Listeners.OrderListUpdateer;
import main.Listeners.TickerUpdateer;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class PairFabric {

    private Map<String, CurrencyPair> pricePairHashMap;
    private BinanceApiWebSocketClient binanceApiWebSocketClient;

    public PairFabric(List<SymbolInfo> symbolInfoList, BinanceApiWebSocketClient binanceApiWebSocketClient) {

        this.binanceApiWebSocketClient = binanceApiWebSocketClient;
        pricePairHashMap = symbolInfoList.stream().collect(Collectors.toMap(
                symbolInfo -> symbolInfo.getBaseAsset() + "BTC",
                CurrencyPair::new));
    }


    public Map<String, CurrencyPair> getCurrencyPairList() {
        addOrderBookListener();
        addOrderListener();
        addTickerListener();

        return pricePairHashMap;
    }


    private void addOrderBookListener() {
        pricePairHashMap.forEach(DepthCacheUpdateer::new);
    }

    private void addOrderListener() {
        new OrderListUpdateer(pricePairHashMap);
    }

    private void addTickerListener() {
        new TickerUpdateer(pricePairHashMap);
    }

}
