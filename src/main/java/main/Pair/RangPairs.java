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
              System.out.println("���� ������� �����");
              pairMap.forEach((k,v)-> System.out.println("" + v.symbolInfo.getSymbol() + v.lowPrice + v.hightPrice + v.bidPrice + v.askPrice));


              //calculateRank();
           } else {
               pairMap.forEach((k,v)-> System.out.println("" + v.symbolInfo.getSymbol() + v.lowPrice + v.hightPrice + v.bidPrice + v.askPrice));
               // System.out.println("� ��� � ��������� �� �� ���");
           }

        }, 0, 1, TimeUnit.SECONDS);

    }

    /**
     * ������ ����� ��� ����
     */
    public void calculateRank() {
        if (ConfigIndexParams.getPositionIndexActivity())
            pairMap.forEach((k,v) -> calculatePositionIndex(v));

        //TODO ������ ������������� �������� - ���� ������ https://coinmarketcap.com/charts/#dominance-percentage (������� �����) � ������� ��� �������� �����/����
    }



    /**
     * ������ ������� �������������� ��������� �� �������.
     * ��� ����, ��� ������ ����� � 1
     * ��� ����, ��� ������ ����� � 0
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



    // TODO ���������� ���� � ������ �� ������ - ���� ��������� ������ ��� ���������� � �����, ��� ��� ����� ����������� � �.�.
    public static class Comparators {
        static public Comparator<CurrencyPair> RANK = (CurrencyPair o1, CurrencyPair o2) ->
                Double.compare(o2.rank, o1.rank);
    }


}
