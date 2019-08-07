package main;

import com.binance.api.client.BinanceApiAsyncRestClient;
import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.BinanceApiWebSocketClient;
import com.binance.api.client.domain.general.ExchangeInfo;
import com.binance.api.client.domain.general.SymbolInfo;
import main.Listeners.AccountBalanceUpdateer;
import main.Pair.CurrencyPair;
import main.Pair.PairFabric;
import main.Pair.RangPairs;
import org.apache.log4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Illustrates how to use the user data event stream to create a local cache for the balance of an account.
 */
public class Main {

    private static final Logger log = Logger.getLogger(Main.class);

    public static void main(String[] args) {

        log.info("������ ������");

        BinanceApiClientFactory binanceApiClientFactory = BinanceApiClientFactory.newInstance(Config.getApiKeyB(), Config.getSecretKeyB());
        BinanceApiRestClient binanceApiRestClient = binanceApiClientFactory.newRestClient();
        BinanceApiWebSocketClient binanceApiWebSocketClient = BinanceApiClientFactory.newInstance().newWebSocketClient();
        BinanceApiAsyncRestClient apiAsyncRestClient = binanceApiClientFactory.newAsyncRestClient();


        // ������� ����, ��� ��������� � ���
        ExchangeInfo exchangeInfo = binanceApiRestClient.getExchangeInfo();
        List<SymbolInfo> symbolInfoList = exchangeInfo.getSymbols();

        List<SymbolInfo> symbolInfoListBTC = symbolInfoList.stream()
                .filter((symbolInfo -> symbolInfo.getStatus().name().equals("TRADING")))
                .filter((symbolInfo -> symbolInfo.getQuoteAsset().equals("BTC")))
                .collect(Collectors.toList());


        // �������� ������ ��� ��� �������� � ����� ���������� �� �� ��������� ����, ������, ��������
        PairFabric pairFabric = new PairFabric(symbolInfoListBTC, binanceApiWebSocketClient);
        Map<String, CurrencyPair> pairMap = pairFabric.getCurrencyPairList();


        // ����� ������ � ������� ������ + ��������� ������� � �������� ��� ���������� �������
        AccountBalanceUpdateer accountBalanceUpdateer = new AccountBalanceUpdateer(binanceApiRestClient, binanceApiWebSocketClient, apiAsyncRestClient, pairMap);

        log.info("������������� ���������, ����� ���������� � ������");

        //TODO ���� ����� ������� �� ����� �����, �� ���� �� ���������� ������ ������, ��������
        RangPairs rangPairs = new RangPairs(pairMap, accountBalanceUpdateer);

        rangPairs.startTrade();


    }
}