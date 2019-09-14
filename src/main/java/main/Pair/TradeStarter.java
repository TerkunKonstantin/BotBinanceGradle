package main.Pair;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.TimeInForce;
import com.binance.api.client.domain.account.NewOrder;
import com.binance.api.client.domain.account.NewOrderResponse;
import main.Config;
import main.DataInitialization;
import main.Listeners.AccountBalanceUpdateer;
import org.apache.log4j.Logger;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.binance.api.client.domain.account.NewOrder.limitBuy;

public class TradeStarter {

    private static final Logger log = Logger.getLogger(TradeStarter.class);
    private final BinanceApiClientFactory factory = BinanceApiClientFactory.newInstance(Config.getApiKeyB(), Config.getSecretKeyB());
    private Map<String, CurrencyPair> pairMap;
    private AccountBalanceUpdateer accountBalanceUpdateer;
    private BinanceApiRestClient binanceApiRestClient = factory.newRestClient();
    private boolean pingSuccess = true;
    private DataInitialization dataInitialization;

    public TradeStarter(DataInitialization dataInitialization, AccountBalanceUpdateer accountBalanceUpdateer) {
        this.dataInitialization = dataInitialization;
        this.accountBalanceUpdateer = accountBalanceUpdateer;
        this.pairMap = dataInitialization.pairFabric.pricePairHashMap;
        //TODO посмотреть зачем я везде таскаю мапу, пары же можно из куренси паир тянуть

    }

    public void startTrade() {
        ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor();
        service.scheduleAtFixedRate(() -> {
            pingAndReinitialization();
            trade();
        }, 30, 10, TimeUnit.SECONDS);

    }

    private NewOrder prepareLimitOrder(CurrencyPair currencyPair) {
        BigDecimal stepPriceSize = new BigDecimal(currencyPair.symbolInfo.getFilters().get(0).getMinPrice());
        BigDecimal priceForBuy = currencyPair.bidPrice.add(stepPriceSize);
        BigDecimal stepBalanceSize = new BigDecimal(currencyPair.symbolInfo.getFilters().get(2).getStepSize());
        int scaleBalance = stepBalanceSize.stripTrailingZeros().scale();
        BigDecimal amount = Config.getMinRate().divide(priceForBuy, scaleBalance, BigDecimal.ROUND_DOWN);
        return limitBuy(currencyPair.symbolInfo.getSymbol(), TimeInForce.GTC, amount.toPlainString(), priceForBuy.toPlainString());
    }

    private void trade() {
        int orderBuyCount = accountBalanceUpdateer.getOrderBuyCount();
        if (orderBuyCount > 0 && pingSuccess) {
            try {
                log.info("Появился свободный баланс, проводится ранжирование пар");
                pairMap.values().forEach(CurrencyPair::calculateRang);
                List<CurrencyPair> currencyPairList = new ArrayList<>(pairMap.values());
                currencyPairList.sort(Comparator.comparingDouble(pair -> pair.rank));
                Collections.reverse(currencyPairList);

                for (CurrencyPair currencyPair : currencyPairList) {
                    if (currencyPair.isCorrectForSale() && orderBuyCount > 0) {
                        log.info("  moneta:" + currencyPair.symbolInfo.getSymbol() + " rang: " + currencyPair.rank);
                        NewOrderResponse newOrderResponse = binanceApiRestClient.newOrder(prepareLimitOrder(currencyPair));
                        log.info(newOrderResponse);
                        orderBuyCount--;
                    }
                }
            } catch (Exception e) {
                log.error(e.getStackTrace());
                e.printStackTrace();
            }
        }
    }

    private void pingAndReinitialization() {
        try {
            binanceApiRestClient.ping();
            if (!pingSuccess) {
                log.info("Реинициализация");
                dataInitialization.pairFabric.reInitialization();
                pairMap = dataInitialization.pairFabric.pricePairHashMap;
                accountBalanceUpdateer.stopUpdater();
                accountBalanceUpdateer = new AccountBalanceUpdateer(dataInitialization);
                pingSuccess = true;
            }
        } catch (Exception e) {
            log.info("Пинга нет");
            pingSuccess = false;
        }
    }

}
