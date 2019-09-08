package main.Listeners;


import com.binance.api.client.BinanceApiAsyncRestClient;
import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.BinanceApiWebSocketClient;
import com.binance.api.client.domain.account.Order;
import com.binance.api.client.domain.account.request.OrderRequest;
import com.binance.api.client.domain.event.OrderTradeUpdateEvent;
import main.Config;
import main.Pair.CurrencyPair;
import org.apache.log4j.Logger;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static com.binance.api.client.domain.event.UserDataUpdateEvent.UserDataUpdateEventType.ORDER_TRADE_UPDATE;

public class OrderListUpdateer {

    //TODO прокинул сокеты и фабрики вручную, Надо прокинуть красивее (возможно)

    private static final Logger log = Logger.getLogger(OrderListUpdateer.class);
    private final BinanceApiClientFactory factory = BinanceApiClientFactory.newInstance(Config.getApiKeyB(), Config.getSecretKeyB());
    private final BinanceApiWebSocketClient webSocketClient = factory.newWebSocketClient();
    private final BinanceApiAsyncRestClient asyncRestClient = factory.newAsyncRestClient();
    private final BinanceApiRestClient apiRestClient = factory.newRestClient();
    private Map<String, CurrencyPair> pricePairHashMap;
    public Closeable closeable;
    public ScheduledFuture<?> scheduledFuture;

    public OrderListUpdateer(Map<String, CurrencyPair> pricePairHashMap) {
        this.pricePairHashMap = pricePairHashMap;
        pricePairHashMap.forEach((k, v) ->
                asyncRestClient.getOpenOrders(new OrderRequest(k), response -> v.orderList = response)
        );

        startOrderListener();

    }


    private void startOrderListener() {

        ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor();
        AtomicReference<Closeable> atomicReference = new AtomicReference<>();
        scheduledFuture = service.scheduleAtFixedRate(() -> {
            try {
                Closeable webSocket = atomicReference.get();
                if (Objects.nonNull(webSocket)) {
                    webSocket.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            closeable = webSocketClient.onUserDataUpdateEvent(apiRestClient.startUserDataStream(), response -> {
                try {
                    if (response.getEventType() == ORDER_TRADE_UPDATE) {
                        List<Order> orderListForDelete = new ArrayList<>();
                        OrderTradeUpdateEvent orderTradeUpdateEvent = response.getOrderTradeUpdateEvent();
                        Order newOrder = createOrder(orderTradeUpdateEvent);
                        CurrencyPair currencyPair = pricePairHashMap.get(orderTradeUpdateEvent.getSymbol());


                        if (orderTradeUpdateEvent.getOrderStatus().toString().equals("CANCELED") || orderTradeUpdateEvent.getOrderStatus().toString().equals("REJECTED") || orderTradeUpdateEvent.getOrderStatus().toString().equals("FILLED")) {
                            log.info(orderTradeUpdateEvent);
                            for (Order order : currencyPair.orderList) {
                                if (order.getOrderId().equals(newOrder.getOrderId())) {
                                    // TODO реализовать удаление ордера нормально, не хочу формировать список, а после уже его удалять из списка оредров пары
                                    orderListForDelete.add(order);
                                }
                            }

                            currencyPair.orderList.removeAll(orderListForDelete);
                        } else
                            currencyPair.orderList.add(newOrder);


                    }
                } catch (Exception e){
                    log.error(e.getStackTrace());
                    e.printStackTrace();
                }
            });

            atomicReference.set(closeable);
        }, 0, 10, TimeUnit.MINUTES);


    }


    private Order createOrder(OrderTradeUpdateEvent orderTradeUpdateEvent){
        Order newOrder = new Order();
        newOrder.setOrderId(orderTradeUpdateEvent.getOrderId());
        newOrder.setSymbol(orderTradeUpdateEvent.getSymbol());
        newOrder.setStatus(orderTradeUpdateEvent.getOrderStatus());
        newOrder.setSide(orderTradeUpdateEvent.getSide());
        return newOrder;
    }
}
