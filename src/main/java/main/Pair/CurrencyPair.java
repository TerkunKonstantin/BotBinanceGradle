package main.Pair;

import com.binance.api.client.BinanceApiAsyncRestClient;
import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.domain.account.Order;
import com.binance.api.client.domain.account.request.CancelOrderRequest;
import com.binance.api.client.domain.general.SymbolInfo;
import main.Config;
import main.ConfigIndexParams;
import org.apache.log4j.Logger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.stream.Collectors;

public class CurrencyPair {

    private static final Logger log = Logger.getLogger(CurrencyPair.class);
    private final BinanceApiClientFactory factory = BinanceApiClientFactory.newInstance(Config.getApiKeyB(), Config.getSecretKeyB());
    private final BinanceApiAsyncRestClient asyncRestClient = factory.newAsyncRestClient();
    public Map<String, NavigableMap<BigDecimal, BigDecimal>> depthCache;
    public SymbolInfo symbolInfo;
    public BigDecimal price = new BigDecimal("0");
    public List<Order> orderList = new ArrayList<>();
    public BigDecimal hightPrice = new BigDecimal("0");
    public BigDecimal lowPrice = new BigDecimal("0");
    public BigDecimal askPrice = new BigDecimal("0");
    public BigDecimal bidPrice = new BigDecimal("0");
    double rank = 1.0;
    private double rankForOrder = 1.0;
    private double volumeIndex;
    private double askBidDifferenceIndex;
    private double positionIndex;


    CurrencyPair(SymbolInfo symbolInfoPair) {
        symbolInfo = symbolInfoPair;
    }

    /**
     * Расчет индекса относительного положения на графике.
     * Чем ниже, тем индекс ближе к 1
     * Чем выше, тем индекс ближе к 0
     */
    private double calculatePositionIndex() {
        BigDecimal high = hightPrice;
        BigDecimal low = lowPrice;
        BigDecimal last = price;
        double positionIndexPair = high.subtract(last).divide(high.subtract(low), BigDecimal.ROUND_HALF_EVEN).doubleValue();
        positionIndex = positionIndexPair;
        return positionIndexPair;
    }

    /**
     * Расчет индекса разницы цены на покупку и продажу.
     * Чем больше процент разницы, тем выше индекс
     */
    private double calculateAskBidDifferenceIndex() {
        BigDecimal ask = askPrice;
        BigDecimal bid = bidPrice;
        BigDecimal stepPriceSize = new BigDecimal(symbolInfo.getFilters().get(0).getMinPrice());
        int priceScale = stepPriceSize.stripTrailingZeros().scale();
        BigDecimal step = BigDecimal.ONE.movePointLeft(priceScale);
        ask = ask.subtract(step);
        bid = bid.add(step);
        BigDecimal askBidDifferenceIndexBD = ask.divide(bid, RoundingMode.HALF_UP).multiply(new BigDecimal("100")).subtract(new BigDecimal("100"));
        askBidDifferenceIndex = askBidDifferenceIndexBD.doubleValue();
        return askBidDifferenceIndexBD.doubleValue();
    }

    /**
     * Расчет индекса объема ордеров в стаканах.
     * Чем больше у нас объем ордеров на покупку, тем выше индекс
     */
    private double calculateVolumeIndex() {

        BigDecimal sumOriginalAmountAsk = getSumAsk(ConfigIndexParams.getVolumeIndexLimitPercent());
        BigDecimal sumOriginalAmountBid = getSumBid(ConfigIndexParams.getVolumeIndexLimitPercent());

        if (sumOriginalAmountAsk.compareTo(BigDecimal.ZERO) > 0) {
            double volumeIndexPair = sumOriginalAmountBid.divide(sumOriginalAmountAsk, 1, RoundingMode.HALF_UP).doubleValue();
            volumeIndex = volumeIndexPair;
            return volumeIndexPair;
        } else {
            volumeIndex = 0;
            return 0;
        }
    }


    /**
     * Расчет ранга для пары
     */
    synchronized void calculateRang() {
        rank = 1.0;

        if (ConfigIndexParams.getVolumeIndexActivity())
            rank *= calculateVolumeIndex();
        if (ConfigIndexParams.getAskBidDifferenceIndexActivity() && rank > 0)
            rank *= calculateAskBidDifferenceIndex();
        if (ConfigIndexParams.getPositionIndexActivity() && rank > 0)
            rank *= calculatePositionIndex();
        //TODO индекс доминирования биткоина - хочу тянуть https://coinmarketcap.com/charts/#dominance-percentage (хорошая штука) и считать его движение вверх/вниз

    }


    /**
     * Расчет ранга для пары
     */
    private synchronized void calculateRangForOrderControl() {
        rankForOrder = 1.0;

        if (ConfigIndexParams.getVolumeIndexActivity())
            rankForOrder *= calculateVolumeIndex();
        if (ConfigIndexParams.getAskBidDifferenceIndexActivity() && rankForOrder > 0)
            rankForOrder *= calculateAskBidDifferenceIndex();
        if (ConfigIndexParams.getPositionIndexActivity() && rankForOrder > 0)
            rankForOrder *= calculatePositionIndex();
        //TODO индекс доминирования биткоина - хочу тянуть https://coinmarketcap.com/charts/#dominance-percentage (хорошая штука) и считать его движение вверх/вниз

    }


    private BigDecimal getSumAsk(BigDecimal percent) {

        BigDecimal lim = BigDecimal.ONE
                .add(percent.movePointLeft(2))
                .multiply(askPrice);

        List<BigDecimal> listAsk = depthCache.get("ASKS").entrySet()
                .stream()
                .filter(entry -> entry.getKey().compareTo(lim) < 0)
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());

        BigDecimal askSum = BigDecimal.ZERO;

        for (BigDecimal ask : listAsk) {
            askSum = askSum.add(ask);
        }

        return askSum;
    }

    private BigDecimal getSumBid(BigDecimal percent) {
        BigDecimal lim = BigDecimal.ONE
                .subtract(percent.movePointLeft(2))
                .multiply(bidPrice);

        List<BigDecimal> listBid = depthCache.get("BIDS").entrySet()
                .stream()
                .filter(entry -> entry.getKey().compareTo(lim) > 0)
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());

        BigDecimal bidSum = BigDecimal.ZERO;

        for (BigDecimal bid : listBid) {
            bidSum = bidSum.add(bid);
        }

        return bidSum;
    }


    private BigDecimal getStepPercent() {
        return new BigDecimal(symbolInfo.getFilters().get(0).getMinPrice())
                .add(bidPrice)
                .divide(bidPrice, RoundingMode.HALF_UP)
                .subtract(BigDecimal.ONE)
                .movePointRight(2);
    }

    boolean isCorrectForSale() {
        return orderList.isEmpty() && rank > Config.getMinRankForBid()
                && getStepPercent().compareTo(Config.getMaxLostProfitFromOrderStep()) < 0;
    }


    public void checkOrderList(List<Order> buyList) {
        calculateRangForOrderControl();
        if (rankForOrder < Config.getMinRankForBid()) {
            log.info("Rank for CANCEL order = " + rankForOrder + "  " + symbolInfo.getSymbol());
            buyList.forEach(order -> asyncRestClient.cancelOrder(
                    new CancelOrderRequest(symbolInfo.getSymbol(), order.getOrderId()), e ->
                    {
                        log.info(e);
                        System.out.println(e);
                    }
            ));
        } else {
            log.debug("Rank for order = " + rankForOrder + "  " + symbolInfo.getSymbol());
        }

    }

}
