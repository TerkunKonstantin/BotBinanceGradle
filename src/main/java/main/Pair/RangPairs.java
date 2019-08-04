package main.Pair;

import com.binance.api.client.BinanceApiAsyncRestClient;
import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.TimeInForce;
import com.binance.api.client.domain.account.NewOrder;
import com.binance.api.client.domain.account.NewOrderResponse;
import main.Config;
import main.Listeners.AccountBalanceUpdateer;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static com.binance.api.client.domain.account.NewOrder.limitBuy;

public class RangPairs {

    private Map<String, CurrencyPair> pairMap;
    private AccountBalanceUpdateer accountBalanceUpdateer;
    private final BinanceApiClientFactory factory = BinanceApiClientFactory.newInstance(Config.getApiKeyB(), Config.getSecretKeyB());
    private final BinanceApiAsyncRestClient asyncRestClient = factory.newAsyncRestClient();
    private BinanceApiRestClient binanceApiRestClient = factory.newRestClient();



    public RangPairs(Map<String, CurrencyPair> pairMap, AccountBalanceUpdateer accountBalanceUpdateer) {
        this.accountBalanceUpdateer = accountBalanceUpdateer;
        this.pairMap = pairMap;
        //TODO посмотреть зачем я везде таскаю мапу, пары же можно из куренси паир тянуть

    }

    // TODO перенести расчет в куренси паир
    public void startTrade() {
        ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor();
        service.scheduleAtFixedRate(() -> {
            int orderBuyCount = accountBalanceUpdateer.getOrderBuyCount();
          //  orderBuyCount = 0;
            if (orderBuyCount > 0) {
                try {
                    System.out.println("Пора считать ранги");
                    pairMap.values().forEach(CurrencyPair::calculateRang);
                    List<CurrencyPair> currencyPairList = new ArrayList<>(pairMap.values());
                    currencyPairList.sort(Comparator.comparingDouble(pair -> pair.rank));
                    Collections.reverse(currencyPairList);

                    for (CurrencyPair currencyPair : currencyPairList) {
                        if (currencyPair.isCorrectForSale() && orderBuyCount > 0) {
                            System.out.println("  moneta:" + currencyPair.symbolInfo.getSymbol() + " rang: " + currencyPair.rank);
                           /* asyncRestClient.newOrder(prepareLimitOrder(currencyPair),
                                    (a) -> System.out.println(a));*/
                            NewOrderResponse newOrderResponse = binanceApiRestClient.newOrder(prepareLimitOrder(currencyPair));
                            System.out.println(newOrderResponse);
                            orderBuyCount--;
                        }
                    }
                }catch (Exception e){
                    e.printStackTrace();
                }
            }

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



}
