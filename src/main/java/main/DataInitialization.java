package main;

import com.binance.api.client.BinanceApiAsyncRestClient;
import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.BinanceApiWebSocketClient;
import com.binance.api.client.domain.general.ExchangeInfo;
import com.binance.api.client.domain.general.SymbolInfo;
import main.Pair.PairFabric;

import java.util.List;
import java.util.stream.Collectors;

public class DataInitialization {

    private BinanceApiClientFactory binanceApiClientFactory = BinanceApiClientFactory.newInstance(Config.getApiKeyB(), Config.getSecretKeyB());
    public BinanceApiRestClient binanceApiRestClient = binanceApiClientFactory.newRestClient();
    public BinanceApiWebSocketClient binanceApiWebSocketClient = BinanceApiClientFactory.newInstance().newWebSocketClient();
    public BinanceApiAsyncRestClient apiAsyncRestClient = binanceApiClientFactory.newAsyncRestClient();
    public PairFabric pairFabric;

    DataInitialization() {

        List<SymbolInfo> symbolInfoListBTC = takeCorrectPairs();

        // Фабрикой создаю для них сущности и сразу подписываю их на изменения цены, объема, оредеров
        pairFabric = new PairFabric(symbolInfoListBTC);
        pairFabric.addPairListeners();
    }

    private List<SymbolInfo> takeCorrectPairs() {

        // Получаю пары, что торгуются с БТС и исключаю пары долгого хранения
        ExchangeInfo exchangeInfo = binanceApiRestClient.getExchangeInfo();
        List<SymbolInfo> symbolInfoList = exchangeInfo.getSymbols();

        return symbolInfoList.stream()
                .filter((symbolInfo -> symbolInfo.getStatus().name().equals("TRADING")))
                .filter((symbolInfo -> symbolInfo.getQuoteAsset().equals("BTC")))
                .filter((symbolInfo -> !Config.getLongStoragePair().contains(symbolInfo.getSymbol())))
                .collect(Collectors.toList());
    }


}
