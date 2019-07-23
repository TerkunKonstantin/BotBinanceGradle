package main.Listeners;

import com.binance.api.client.BinanceApiAsyncRestClient;
import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.BinanceApiWebSocketClient;
import main.Config;
import main.Pair.CurrencyPair;

import java.io.Closeable;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public abstract class AbstractStreamUpdateer {
    public static final ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor();

    public final BinanceApiClientFactory factory = BinanceApiClientFactory.newInstance(Config.getApiKeyB(), Config.getSecretKeyB());
    public final BinanceApiWebSocketClient webSocketClient = factory.newWebSocketClient();
    public final BinanceApiAsyncRestClient asyncRestClient = factory.newAsyncRestClient();


    public CurrencyPair currencyPair;

    public AbstractStreamUpdateer(String symbol, CurrencyPair currencyPair) {
        this.currencyPair = currencyPair;
        init(symbol);

        AtomicReference<Closeable> atomicReference = new AtomicReference<>();
        service.scheduleAtFixedRate(() -> {
            try {
                Closeable webSocket = atomicReference.get();
                if (Objects.nonNull(webSocket)){
                    webSocket.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            atomicReference.set(getCloseable(symbol));
        }, 0, 20, TimeUnit.HOURS);

    }


    public abstract void init(String symbol);

    public abstract Closeable getCloseable(String symbol);


}
