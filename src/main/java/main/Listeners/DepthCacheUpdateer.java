package main.Listeners;

import com.binance.api.client.domain.market.OrderBookEntry;
import main.Pair.CurrencyPair;

import java.io.Closeable;
import java.math.BigDecimal;
import java.util.*;

public class DepthCacheUpdateer extends AbstractStreamUpdateer {
    private long lastUpdateId;
    private static final String BIDS = "BIDS";
    private static final String ASKS = "ASKS";


    private Map<String, NavigableMap<BigDecimal, BigDecimal>> depthCache;

    public DepthCacheUpdateer(String symbol, CurrencyPair currencyPair) {
        super(symbol, currencyPair);
    }

    @Override
    public void init(String symbol) {
        asyncRestClient.getOrderBook(symbol.toUpperCase(), 20,orderBook ->{
            this.depthCache = new HashMap<>();
            currencyPair.depthCache = new HashMap<>();
            this.lastUpdateId = orderBook.getLastUpdateId();

            NavigableMap<BigDecimal, BigDecimal> asks = new TreeMap<>(Comparator.reverseOrder());
            for (OrderBookEntry ask : orderBook.getAsks()) {
                asks.put(new BigDecimal(ask.getPrice()), new BigDecimal(ask.getQty()));
            }
            depthCache.put(ASKS, asks);
            currencyPair.depthCache.put(ASKS, asks);

            NavigableMap<BigDecimal, BigDecimal> bids = new TreeMap<>(Comparator.reverseOrder());
            for (OrderBookEntry bid : orderBook.getBids()) {
                bids.put(new BigDecimal(bid.getPrice()), new BigDecimal(bid.getQty()));
            }
            depthCache.put(BIDS, bids);
            currencyPair.depthCache.put(BIDS, bids);
        });
    }

    @Override
    public Closeable getCloseable(String symbol) {
        return webSocketClient.onDepthEvent(symbol.toLowerCase(), response -> {
            if (response.getFinalUpdateId() > lastUpdateId) {
                lastUpdateId = response.getFinalUpdateId();
                updateOrderBook(getAsks(), response.getAsks());
                updateOrderBook(getBids(), response.getBids());
                updateOrderBook(currencyPair.depthCache.get(ASKS), response.getAsks());
                updateOrderBook(currencyPair.depthCache.get(BIDS), response.getBids());

            }
        });

    }

    private void updateOrderBook(NavigableMap<BigDecimal, BigDecimal> lastOrderBookEntries, List<OrderBookEntry> orderBookDeltas) {
        for (OrderBookEntry orderBookDelta : orderBookDeltas) {
            BigDecimal price = new BigDecimal(orderBookDelta.getPrice());
            BigDecimal qty = new BigDecimal(orderBookDelta.getQty());
            if (qty.compareTo(BigDecimal.ZERO) == 0) {
                // qty=0 means remove this level
                lastOrderBookEntries.remove(price);
                lastOrderBookEntries.put(price, qty);
            }
        }
    }

    public NavigableMap<BigDecimal, BigDecimal> getAsks() {
        return depthCache.get(ASKS);
    }

    public NavigableMap<BigDecimal, BigDecimal> getBids() {
        return depthCache.get(BIDS);
    }
}
