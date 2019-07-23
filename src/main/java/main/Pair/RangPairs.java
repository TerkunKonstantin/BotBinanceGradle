package main.Pair;

import main.ConfigIndexParams;
import main.Listeners.AccountBalanceUpdateer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class RangPairs {
    Map<String, CurrencyPair> pairMap;
    AccountBalanceUpdateer accountBalanceUpdateer;

    public RangPairs(Map<String, CurrencyPair> pairMap, AccountBalanceUpdateer accountBalanceUpdateer){
        this.accountBalanceUpdateer = accountBalanceUpdateer;
        this.pairMap = pairMap;
    }

    public void startTrade(){
        ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor();
       service.scheduleAtFixedRate(() -> {
           if(accountBalanceUpdateer.getOrderBuyCount()> 0){
              System.out.println("Пора считать ранги");
              pairMap.forEach((k,v)-> System.out.println("" + v.symbolInfo.getSymbol() + v.lowPrice + v.hightPrice + v.bidPrice + v.askPrice));


              //calculateRank();
           } else {
               pairMap.forEach((k,v)-> System.out.println("" + v.symbolInfo.getSymbol() + v.lowPrice + v.hightPrice + v.bidPrice + v.askPrice));
               // System.out.println("А тут и торговать то не чем");
           }

        }, 0, 1, TimeUnit.SECONDS);

    }

    /**
     * Расчет ранга для пары
     */
    public void calculateRank() {
        if (ConfigIndexParams.getPositionIndexActivity())
            pairMap.forEach((k,v) -> calculatePositionIndex(v));

        //TODO индекс доминирования биткоина - хочу тянуть https://coinmarketcap.com/charts/#dominance-percentage (хорошая штука) и считать его движение вверх/вниз
    }



    /**
     * Расчет индекса относительного положения на графике.
     * Чем ниже, тем индекс ближе к 1
     * Чем выше, тем индекс ближе к 0
     */
    private void calculatePositionIndex(CurrencyPair currencyPair) {
        if (currencyPair.rank <= 0) return;
        BigDecimal high = currencyPair.hightPrice ;
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



    // TODO Реализацию спер и сильно не вникал - надо прочитать статью про сортировку в джава, как еще можно реализовать и т.д.
    public static class Comparators {
        static public Comparator<CurrencyPair> RANK = (CurrencyPair o1, CurrencyPair o2) ->
                Double.compare(o2.rank, o1.rank);
    }


}
