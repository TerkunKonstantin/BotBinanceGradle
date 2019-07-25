package main.Pair;

import com.binance.api.client.domain.account.Order;
import com.binance.api.client.domain.general.SymbolInfo;
import com.binance.api.client.domain.market.TickerStatistics;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;

public class CurrencyPair {

    public Map<String, NavigableMap<BigDecimal, BigDecimal>> depthCache;
    public SymbolInfo symbolInfo;
    public BigDecimal price = new BigDecimal("0");
    public List<Order> orderList;
    Double rank = 1.0;
    public BigDecimal hightPrice = new BigDecimal("0");
    public BigDecimal lowPrice = new BigDecimal("0");
    public BigDecimal askPrice = new BigDecimal("0");
    public BigDecimal bidPrice = new BigDecimal("0");
    double volumeIndex;
    double askBidDifferenceIndex;
    double positionIndex;


    public CurrencyPair(SymbolInfo symbolInfoPair) {
        symbolInfo = symbolInfoPair;
    }

    // TODO Реализацию спер и сильно не вникал - надо прочитать статью про сортировку в джава, как еще можно реализовать и т.д.
    static class Comparators {
        static Comparator<CurrencyPair> RANK = (CurrencyPair o1, CurrencyPair o2) ->
                Double.compare(o2.rank, o1.rank);
    }


}
