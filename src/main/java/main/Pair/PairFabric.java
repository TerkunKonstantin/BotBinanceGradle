package main.Pair;

import com.binance.api.client.BinanceApiWebSocketClient;
import com.binance.api.client.domain.general.SymbolInfo;
import main.Listeners.DepthCacheUpdateer;
import main.Listeners.OrderListUpdateer;
import main.Listeners.PriceUpdateer;
import main.Listeners.TickerUpdateer;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class PairFabric {

    Map<String, CurrencyPair> pricePairHashMap;
    BinanceApiWebSocketClient binanceApiWebSocketClient;

    public PairFabric(List<SymbolInfo> symbolInfoList, BinanceApiWebSocketClient binanceApiWebSocketClient) {

        this.binanceApiWebSocketClient = binanceApiWebSocketClient;
        pricePairHashMap = symbolInfoList.stream().collect(Collectors.toMap(
                symbolInfo -> symbolInfo.getBaseAsset() + "BTC",
                CurrencyPair::new));
    }


    public Map<String, CurrencyPair> getCurrencyPairList() {
        addPriceListener();
        addOrderBookListener();
        addOrderListener();
        addTickerListener();

        return pricePairHashMap;
    }

    public void addPriceListener() {
        pricePairHashMap.forEach(PriceUpdateer::new);
    }

    public void addOrderBookListener() {
        pricePairHashMap.forEach(DepthCacheUpdateer::new);
    }

    public void addOrderListener() {
        new OrderListUpdateer(pricePairHashMap);
    }

    public void addTickerListener() {
        new TickerUpdateer(pricePairHashMap);
    }

}
