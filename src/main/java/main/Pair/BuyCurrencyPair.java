package main.Pair;

import com.binance.api.client.BinanceApiAsyncRestClient;
import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.domain.TimeInForce;
import main.Config;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.binance.api.client.domain.account.NewOrder.limitBuy;

public class BuyCurrencyPair {

    private final BinanceApiClientFactory factory = BinanceApiClientFactory.newInstance(Config.getApiKeyB(), Config.getSecretKeyB());
    private final BinanceApiAsyncRestClient asyncRestClient = factory.newAsyncRestClient();

    public BuyCurrencyPair(CurrencyPair currencyPair){
        BigDecimal priceForBuy;
        BigDecimal bidPrice = currencyPair.bidPrice;
        BigDecimal stepPriceSize = new BigDecimal(currencyPair.symbolInfo.getFilters().get(0).getMinPrice());
        int priceScale = stepPriceSize.stripTrailingZeros().scale();
        BigDecimal bidWithStep = bidPrice.add(BigDecimal.ONE.movePointLeft(priceScale));
        BigDecimal askBidDifferenceIndexBD = bidWithStep.divide(bidPrice, RoundingMode.HALF_UP).multiply(new BigDecimal("100")).subtract(new BigDecimal("100"));
        if (askBidDifferenceIndexBD.compareTo(Config.getMaxLostProfitFromOrderStep()) > 0) {
            priceForBuy = bidPrice;
        } else {
            priceForBuy = bidWithStep;
        }
        priceForBuy = priceForBuy.setScale(priceScale, BigDecimal.ROUND_UP);
        BigDecimal stepBalanceSize = new BigDecimal(currencyPair.symbolInfo.getFilters().get(2).getStepSize());
        int scaleBalance = stepBalanceSize.stripTrailingZeros().scale();
        BigDecimal amount = Config.getMinRate().divide(priceForBuy, scaleBalance, BigDecimal.ROUND_DOWN);

        asyncRestClient.newOrder(limitBuy(currencyPair.symbolInfo.getSymbol(), TimeInForce.GTC, amount.toString(), priceForBuy.toString()),
                System.out::println);

    }






}
