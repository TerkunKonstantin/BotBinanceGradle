package main.Listeners;

import main.Pair.CurrencyPair;

import java.io.Closeable;
import java.math.BigDecimal;

public class PriceUpdateer extends AbstractStreamUpdateer {


    public PriceUpdateer(String symbol, CurrencyPair currencyPair) {
        super(symbol, currencyPair);
    }

    @Override
    public void init(String symbol) {
        asyncRestClient.getPrice(symbol, response ->
                currencyPair.price = new BigDecimal(response.getPrice())
        );
    }


    @Override
    public Closeable getCloseable(String symbol) {
        return webSocketClient.onAggTradeEvent(symbol.toLowerCase(), response ->
                currencyPair.price = new BigDecimal(response.getPrice()));

    }


}