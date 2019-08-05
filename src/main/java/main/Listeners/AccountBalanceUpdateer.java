package main.Listeners;

import com.binance.api.client.BinanceApiAsyncRestClient;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.BinanceApiWebSocketClient;
import com.binance.api.client.domain.TimeInForce;
import com.binance.api.client.domain.account.Account;
import com.binance.api.client.domain.account.AssetBalance;
import com.binance.api.client.domain.account.Trade;
import main.Config;
import main.Main;
import main.Pair.CurrencyPair;
import org.apache.log4j.Logger;

import java.io.Closeable;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static com.binance.api.client.domain.account.NewOrder.limitSell;
import static com.binance.api.client.domain.event.UserDataUpdateEvent.UserDataUpdateEventType.ACCOUNT_UPDATE;

public class AccountBalanceUpdateer {

    private static final Logger log = Logger.getLogger(AccountBalanceUpdateer.class);

    private final BinanceApiRestClient apiRestClient;
    private final BinanceApiWebSocketClient apiWebSocketClient;
    private final BinanceApiAsyncRestClient apiAsyncRestClient;

    /**
     * Listen key used to interact with the user data streaming API.
     */
    private final String listenKey;
    private Map<String, CurrencyPair> pairMap;
    /**
     * Key is the symbol, and the value is the balance of that symbol on the account.
     */
    private Map<String, AssetBalance> accountBalanceCache;

    public AccountBalanceUpdateer(BinanceApiRestClient binanceApiRestClient, BinanceApiWebSocketClient binanceApiWebSocketClient, BinanceApiAsyncRestClient apiAsyncRestClient, Map<String, CurrencyPair> pairMap) {
        this.pairMap = pairMap;
        this.apiRestClient = binanceApiRestClient;
        this.apiWebSocketClient = binanceApiWebSocketClient;
        this.apiAsyncRestClient = apiAsyncRestClient;
        this.listenKey = initializeAssetBalanceCacheAndStreamSession();
        startAccountBalanceEventStreaming(listenKey);
    }

    /**
     * Initializes the asset balance cache by using the REST API and starts a new user data streaming session.
     *
     * @return a listenKey that can be used with the user data streaming API.
     */
    private String initializeAssetBalanceCacheAndStreamSession() {
        Account account = apiRestClient.getAccount();
        accountBalanceCache = new TreeMap<>();
        for (AssetBalance assetBalance : account.getBalances()) {
            accountBalanceCache.put(assetBalance.getAsset(), assetBalance);
            CurrencyPair currencyPair = pairMap.get(assetBalance.getAsset() + "BTC");
            if (currencyPair != null)
                if (!Config.getLongStoragePair().contains(currencyPair.symbolInfo.getSymbol())) {
                    saleCurrency(assetBalance, currencyPair);
                }
        }

        return apiRestClient.startUserDataStream();
    }

    /**
     * Begins streaming of agg trades events.
     */
    //TODO сделать реализацию через абстрактный класс AbstractStreamUpdateer (пока что не подходят параметры функций и конструктора). Логика продублирована.
    private void startAccountBalanceEventStreaming(String listenKey) {

        ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor();
        AtomicReference<Closeable> atomicReference = new AtomicReference<>();
        service.scheduleAtFixedRate(() -> {
            try {
                Closeable webSocket = atomicReference.get();
                if (Objects.nonNull(webSocket)){
                    webSocket.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            atomicReference.set(
                    apiWebSocketClient.onUserDataUpdateEvent(listenKey, response -> {
                        if (response.getEventType() == ACCOUNT_UPDATE) {

                            for (AssetBalance assetBalance : response.getAccountUpdateEvent().getBalances()) {
                                accountBalanceCache.put(assetBalance.getAsset(), assetBalance);
                                CurrencyPair currencyPair = pairMap.get(assetBalance.getAsset() + "BTC");
                                if (currencyPair != null)
                                    // TODO тут есть проблема при отмене сразу многих ордеров. Приходят данные об изменниеиии баланса, по каждому ордеру. По списку монет выставляю ордера. А на следущем обновлении баланса по следующему ордеру уже нет балансов. Падаем в ошибку. Сможем продолжить выставлять продажи через 5 минут только. Надо бы сделать выставление ордеров из другого места.
                                    if (!Config.getLongStoragePair().contains(currencyPair.symbolInfo.getSymbol())) {
                                        saleCurrency(assetBalance, currencyPair);
                                  }
                            }
                        }

                    }));
        }, 0, 5, TimeUnit.MINUTES);




    }


    private void saleCurrency(AssetBalance assetBalance, CurrencyPair currencyPair) {
        BigDecimal assetBalanceFree = new BigDecimal(assetBalance.getFree());
        assetBalanceFree = assetBalanceFree.multiply(currencyPair.price);
        if (assetBalanceFree.compareTo(Config.getMinSaleBalance()) >= 0) {
            log.info("AccountBalanceUpdateer " + currencyPair);
            System.out.println("AccountBalanceUpdateer " + currencyPair);
            // TODO сделать реализацию обновления списка трейдов по всем монетам стримами - увеличим быстродействие. В списке можно будет хранить только последнюю покупку.
            List<Trade> myTrades = apiRestClient.getMyTrades(currencyPair.symbolInfo.getSymbol());
            Collections.reverse(myTrades);
            for (Trade trade : myTrades) {
                if (trade.isBuyer()) {

                    BigDecimal tradePrice = new BigDecimal(trade.getPrice());
                    BigDecimal salePrice = Config.getProfitForSale().multiply(tradePrice.scaleByPowerOfTen(-2)).add(new BigDecimal(trade.getPrice()));
                    BigDecimal stepPriceSize = new BigDecimal(currencyPair.symbolInfo.getFilters().get(0).getMinPrice());
                    int scalePrice = stepPriceSize.stripTrailingZeros().scale();
                    salePrice = salePrice.setScale(scalePrice, BigDecimal.ROUND_UP);
                    BigDecimal pairBalance = new BigDecimal(assetBalance.getFree());
                    BigDecimal stepBalanceSize = new BigDecimal(currencyPair.symbolInfo.getFilters().get(2).getStepSize());
                    int scaleBalance = stepBalanceSize.stripTrailingZeros().scale();
                    BigDecimal pairQuantity = pairBalance.setScale(scaleBalance, BigDecimal.ROUND_DOWN);

                    apiAsyncRestClient.newOrder(limitSell(currencyPair.symbolInfo.getSymbol(), TimeInForce.GTC, pairQuantity.toPlainString(), salePrice.toPlainString()),
                            System.out::println);
                    break;
                }
            }
        }
    }

    /**
     * @return an account balance cache, containing the balance for every asset in this account.
     */
    public Map<String, AssetBalance> getAccountBalanceCache() {
        return accountBalanceCache;
    }


    public int getOrderBuyCount() {
        String asset = "BTC";
        AssetBalance assetBalance = accountBalanceCache.get(asset);
        String freeBTC = assetBalance.getFree();
        BigDecimal BTC = new BigDecimal(freeBTC);
        BigDecimal minRate = Config.getMinRate();
        return BTC.divide(minRate, BigDecimal.ROUND_DOWN).intValue();
    }



}
