package main.Pair;

import com.binance.api.client.domain.account.Order;
import com.binance.api.client.domain.general.SymbolInfo;
import com.binance.api.client.domain.market.TickerStatistics;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;

public class CurrencyPair {

    public Map<String, NavigableMap<BigDecimal, BigDecimal>> depthCache;
    public SymbolInfo symbolInfo;
    public BigDecimal price = new BigDecimal("0");
    public List<Order> orderList;
    public Double rank = 0.0;
    public BigDecimal hightPrice = new BigDecimal("0");
    public BigDecimal lowPrice = new BigDecimal("0");
    public BigDecimal askPrice = new BigDecimal("0");
    public BigDecimal bidPrice = new BigDecimal("0");
    public double volumeIndex;
    public double askBidDifferenceIndex;
    public double positionIndex;


    public CurrencyPair(SymbolInfo symbolInfoPair) {
        symbolInfo = symbolInfoPair;
    }

}
