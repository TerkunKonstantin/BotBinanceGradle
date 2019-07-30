package main.Pair;

import com.binance.api.client.BinanceApiAsyncRestClient;
import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.domain.account.Order;
import com.binance.api.client.domain.account.request.CancelOrderRequest;
import com.binance.api.client.domain.general.SymbolInfo;
import main.Config;
import main.ConfigIndexParams;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;

public class CurrencyPair {

    public Map<String, NavigableMap<BigDecimal, BigDecimal>> depthCache;
    public SymbolInfo symbolInfo;
    public BigDecimal price = new BigDecimal("0");
    public List<Order> orderList = new ArrayList<>();
    public BigDecimal hightPrice = new BigDecimal("0");
    public BigDecimal lowPrice = new BigDecimal("0");
    public BigDecimal askPrice = new BigDecimal("0");
    public BigDecimal bidPrice = new BigDecimal("0");
    double rank = 1.0;
    private double volumeIndex;
    private double askBidDifferenceIndex;
    private double positionIndex;
    private final BinanceApiClientFactory factory = BinanceApiClientFactory.newInstance(Config.getApiKeyB(), Config.getSecretKeyB());
    private final BinanceApiAsyncRestClient asyncRestClient = factory.newAsyncRestClient();



    public CurrencyPair(SymbolInfo symbolInfoPair) {
        symbolInfo = symbolInfoPair;
    }

    /**
     * Расчет индекса относительного положения на графике.
     * Чем ниже, тем индекс ближе к 1
     * Чем выше, тем индекс ближе к 0
     */
    private void calculatePositionIndex() {
        if (rank <= 0) return;
        BigDecimal high = hightPrice;
        BigDecimal low = lowPrice;
        BigDecimal last = price;
        double positionIndexPair = high.subtract(last).divide(high.subtract(low), BigDecimal.ROUND_HALF_EVEN).doubleValue();
        rank = rank * positionIndexPair;
        positionIndex = positionIndexPair;

    }

    /**
     * Расчет индекса разницы цены на покупку и продажу.
     * Чем больше процент разницы, тем выше индекс
     */
    private void calculateAskBidDifferenceIndex() {
        if (rank <= 0) return;
        BigDecimal ask = askPrice;
        BigDecimal bid = bidPrice;
        BigDecimal stepPriceSize = new BigDecimal(symbolInfo.getFilters().get(0).getMinPrice());
        int priceScale = stepPriceSize.stripTrailingZeros().scale();
        BigDecimal step = BigDecimal.ONE.movePointLeft(priceScale);
        ask = ask.subtract(step);
        bid = bid.add(step);
        BigDecimal askBidDifferenceIndexBD = ask.divide(bid, RoundingMode.HALF_UP).multiply(new BigDecimal("100")).subtract(new BigDecimal("100"));
        askBidDifferenceIndex = askBidDifferenceIndexBD.doubleValue();
        rank = rank * askBidDifferenceIndex;
    }

    /**
     * Расчет индекса объема ордеров в стаканах.
     * Чем больше у нас объем ордеров на покупку, тем выше индекс
     */
    private void calculateVolumeIndex() {
        if (rank <= 0) return;

        BigDecimal sumOriginalAmountAsk = getSumAsk(ConfigIndexParams.getVolumeIndexLimitPercent());
        BigDecimal sumOriginalAmountBid = getSumBid(ConfigIndexParams.getVolumeIndexLimitPercent());

        if (sumOriginalAmountAsk.compareTo(BigDecimal.ZERO) > 0) {
            double volumeIndexPair = sumOriginalAmountBid.divide(sumOriginalAmountAsk, 1, RoundingMode.HALF_UP).doubleValue();
            rank *= volumeIndexPair;
            volumeIndex = volumeIndexPair;
        } else {
            rank = 0;
            volumeIndex = 0;
        }
    }


    /**
     * Расчет ранга для пары
     */
     void calculateRang() {
        if (ConfigIndexParams.getVolumeIndexActivity())
            calculateVolumeIndex();
        if (ConfigIndexParams.getAskBidDifferenceIndexActivity())
            calculateAskBidDifferenceIndex();
        if (ConfigIndexParams.getPositionIndexActivity())
            calculatePositionIndex();
        //TODO индекс доминирования биткоина - хочу тянуть https://coinmarketcap.com/charts/#dominance-percentage (хорошая штука) и считать его движение вверх/вниз

    }


    private BigDecimal getSumAsk(BigDecimal percent) {
        BigDecimal lim = BigDecimal.ONE
                .add(percent.movePointLeft(2))
                .multiply(askPrice);

        return depthCache.get("ASKS").entrySet()
                .stream()
                .filter(entry -> entry.getKey().compareTo(lim) > 0)
                .map(Map.Entry::getValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal getSumBid(BigDecimal percent) {
        BigDecimal lim = BigDecimal.ONE
                .subtract(percent.movePointLeft(2))
                .multiply(bidPrice);

        return depthCache.get("BIDS").entrySet()
                .stream()
                .filter(entry -> entry.getKey().compareTo(lim) < 0)
                .map(Map.Entry::getValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }


    private BigDecimal getStepPercent() {
        return new BigDecimal(symbolInfo.getFilters().get(0).getMinPrice())
                .add(bidPrice)
                .divide(bidPrice, RoundingMode.HALF_UP)
                .subtract(BigDecimal.ONE)
                .movePointRight(2);
    }
    boolean isCorrectForSale(){
        return orderList.isEmpty() && rank > Config.getMinRankForBid()
                && getStepPercent().compareTo(Config.getMaxLostProfitFromOrderStep()) < 0;
    }


    public void checkOrderList(List<Order> buyList){
        calculateRang();
        if(rank < Config.getMinRankForBid()){
            buyList.forEach(order -> asyncRestClient.cancelOrder(
                    new CancelOrderRequest(symbolInfo.getSymbol(), order.getOrderId()), e-> System.out.println()));
        }

    }

}
