package main.Pair;

import com.binance.api.client.BinanceApiAsyncRestClient;
import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.domain.TimeInForce;
import main.Config;
import main.ConfigIndexParams;
import main.Listeners.AccountBalanceUpdateer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.binance.api.client.domain.account.NewOrder.limitBuy;

public class RangPairs {
    private final BinanceApiClientFactory factory = BinanceApiClientFactory.newInstance(Config.getApiKeyB(), Config.getSecretKeyB());
    private final BinanceApiAsyncRestClient asyncRestClient = factory.newAsyncRestClient();
    private Map<String, CurrencyPair> pairMap;
    private AccountBalanceUpdateer accountBalanceUpdateer;


    public RangPairs(Map<String, CurrencyPair> pairMap, AccountBalanceUpdateer accountBalanceUpdateer) {
        this.accountBalanceUpdateer = accountBalanceUpdateer;
        this.pairMap = pairMap;
        //TODO посмотреть зачем я везде таскаю мапу, пары же можно из куренси паир тянуть

    }

    public void startTrade() {
        ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor();
        service.scheduleAtFixedRate(() -> {
            int orderBuyCount = accountBalanceUpdateer.getOrderBuyCount();
            if (orderBuyCount > 0) {
                System.out.println("Пора считать ранги");
                calculateRank();
                List<CurrencyPair> currencyPairList = new ArrayList<>(pairMap.values());
                currencyPairList.sort(CurrencyPair.Comparators.RANK);
                for (CurrencyPair currencyPair : currencyPairList) {
                    if (currencyPair.rank > Config.getMinRankForBid() && currencyPair.orderList.isEmpty()) {
                        orderPlaceBid(currencyPair);
                        orderBuyCount--;
                        currencyPair.rank = 1.0;
                    } else {
                        currencyPair.rank = 1.0;
                    }
                }
            } else {
                System.out.println("А тут и торговать то не чем");
            }

        }, 0, 10, TimeUnit.SECONDS);

    }

    /**
     * Расчет ранга для пары
     */
    private void calculateRank() {
        if (ConfigIndexParams.getVolumeIndexActivity())
            pairMap.forEach((k, v) -> calculateVolumeIndex(v));
        if (ConfigIndexParams.getAskBidDifferenceIndexActivity())
            pairMap.forEach((k, v) -> calculateAskBidDifferenceIndex(v));
        if (ConfigIndexParams.getPositionIndexActivity())
            pairMap.forEach((k, v) -> calculatePositionIndex(v));

        //TODO индекс доминирования биткоина - хочу тянуть https://coinmarketcap.com/charts/#dominance-percentage (хорошая штука) и считать его движение вверх/вниз
    }


    /**
     * Расчет индекса относительного положения на графике.
     * Чем ниже, тем индекс ближе к 1
     * Чем выше, тем индекс ближе к 0
     */
    private void calculatePositionIndex(CurrencyPair currencyPair) {
        if (currencyPair.rank <= 0) return;
        BigDecimal high = currencyPair.hightPrice;
        BigDecimal low = currencyPair.lowPrice;
        BigDecimal last = currencyPair.price;
        if ((low.compareTo(BigDecimal.ZERO) > 0)) {
            double positionIndexPair = high.subtract(last).divide(high.subtract(low), BigDecimal.ROUND_HALF_EVEN).doubleValue();
            currencyPair.rank = currencyPair.rank * positionIndexPair;
            currencyPair.positionIndex = positionIndexPair;
        } else {
            double PositionIndexPair = 0;
            currencyPair.rank = currencyPair.rank * PositionIndexPair;
            currencyPair.positionIndex = PositionIndexPair;
        }
    }

    /**
     * Расчет индекса разницы цены на покупку и продажу.
     * Чем больше процент разницы, тем выше индекс
     */
    private void calculateAskBidDifferenceIndex(CurrencyPair currencyPair) {
        if (currencyPair.rank <= 0) return;
        BigDecimal ask = currencyPair.askPrice;
        BigDecimal bid = currencyPair.bidPrice;
        BigDecimal stepPriceSize = new BigDecimal(currencyPair.symbolInfo.getFilters().get(0).getMinPrice());
        int priceScale = stepPriceSize.stripTrailingZeros().scale();
        BigDecimal step = BigDecimal.ONE.movePointLeft(priceScale);
        ask = ask.subtract(step);
        bid = bid.add(step);
        BigDecimal askBidDifferenceIndexBD = ask.divide(bid, RoundingMode.HALF_UP).multiply(new BigDecimal("100")).subtract(new BigDecimal("100"));
        currencyPair.askBidDifferenceIndex = askBidDifferenceIndexBD.doubleValue();
        currencyPair.rank = currencyPair.rank * currencyPair.askBidDifferenceIndex;
    }

    /**
     * Расчет индекса объема ордеров в стаканах.
     * Чем больше у нас объем ордеров на покупку, тем выше индекс
     */
    private void calculateVolumeIndex(CurrencyPair currencyPair) {
        if (currencyPair.rank <= 0) return;
        BigDecimal limitPercentAsk = ConfigIndexParams.getVolumeIndexLimitPercent().movePointLeft(2).add(BigDecimal.ONE);
        BigDecimal limitPercentBid = BigDecimal.ONE.subtract(ConfigIndexParams.getVolumeIndexLimitPercent().movePointLeft(2));
        NavigableMap<BigDecimal, BigDecimal> asks = currencyPair.depthCache.get("ASKS").descendingMap();
        BigDecimal ask = currencyPair.askPrice;
        BigDecimal limitAskPrice = ask.multiply(limitPercentAsk);
        NavigableMap<BigDecimal, BigDecimal> bids = currencyPair.depthCache.get("BIDS");
        BigDecimal bid = currencyPair.bidPrice;
        BigDecimal limitBidPrice = bid.multiply(limitPercentBid);
        BigDecimal sumOriginalAmountAsk = BigDecimal.ZERO;
        BigDecimal sumOriginalAmountBid = BigDecimal.ZERO;

        for (Map.Entry<BigDecimal, BigDecimal> limitOrder : asks.entrySet()) {
            BigDecimal limitPrice = limitOrder.getKey();
            if (limitPrice.compareTo(limitAskPrice) > 0) break;
            BigDecimal originalAmountAsk = limitOrder.getValue();
            sumOriginalAmountAsk = sumOriginalAmountAsk.add(originalAmountAsk);
        }

        for (Map.Entry<BigDecimal, BigDecimal> limitOrder : bids.entrySet()) {
            BigDecimal limitPrice = limitOrder.getKey();
            if (limitPrice.compareTo(limitBidPrice) < 0) break;
            BigDecimal originalAmountBid = limitOrder.getValue();
            sumOriginalAmountBid = sumOriginalAmountBid.add(originalAmountBid);
        }

        if (sumOriginalAmountAsk.compareTo(BigDecimal.ZERO) > 0) {
            double volumeIndexPair = sumOriginalAmountBid.divide(sumOriginalAmountAsk, 1, RoundingMode.HALF_UP).doubleValue();
            currencyPair.rank = currencyPair.rank * volumeIndexPair;
            currencyPair.volumeIndex = volumeIndexPair;
        } else {
            double volumeIndexPair = 0;
            currencyPair.rank = currencyPair.rank * volumeIndexPair;
            currencyPair.volumeIndex = volumeIndexPair;
        }
    }


    /**
     * Расстановка ордеров на покупку, на основании рангов пар и доступного баланса
     */
    private void orderPlaceBid(CurrencyPair currencyPair) {

        BigDecimal priceForBuy;
        BigDecimal bidPrice = currencyPair.bidPrice;
        BigDecimal stepPriceSize = new BigDecimal(currencyPair.symbolInfo.getFilters().get(0).getMinPrice());
        int priceScale = stepPriceSize.stripTrailingZeros().scale();
        BigDecimal step = BigDecimal.ONE.movePointLeft(priceScale);
        BigDecimal bidWithStep = bidPrice.add(step);
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
