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

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static com.binance.api.client.domain.event.UserDataUpdateEvent.UserDataUpdateEventType.ORDER_TRADE_UPDATE;

public class OrderListUpdateer {

    //TODO �������� ������ � ������� �������, ���� ��������� �������� (��������)

    public final BinanceApiClientFactory factory = BinanceApiClientFactory.newInstance(Config.getApiKeyB(), Config.getSecretKeyB());
    public final BinanceApiWebSocketClient webSocketClient = factory.newWebSocketClient();
    public final BinanceApiAsyncRestClient asyncRestClient = factory.newAsyncRestClient();
    public final BinanceApiRestClient apiRestClient = factory.newRestClient();
    public Map<String, CurrencyPair> pricePairHashMap;

    public OrderListUpdateer(Map<String, CurrencyPair> pricePairHashMap) {
        this.pricePairHashMap = pricePairHashMap;
        pricePairHashMap.forEach((k, v) ->
                asyncRestClient.getOpenOrders(new OrderRequest(k), response -> {
                    v.orderList = response;
                })
        );

        startOrderListener();

    }


    public void startOrderListener() {

        ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor();
        AtomicReference<Closeable> atomicReference = new AtomicReference<>();
        service.scheduleAtFixedRate(() -> {
            try {
                Closeable webSocket = atomicReference.get();
                if (Objects.nonNull(webSocket)) {
                    webSocket.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            atomicReference.set(
                    webSocketClient.onUserDataUpdateEvent(apiRestClient.startUserDataStream(), response -> {
                        if (response.getEventType() == ORDER_TRADE_UPDATE) {
                            List<Order> orderListForDelete = new ArrayList<>();
                            OrderTradeUpdateEvent orderTradeUpdateEvent = response.getOrderTradeUpdateEvent();
                            Order newOrder = createOrder(orderTradeUpdateEvent);
                            CurrencyPair currencyPair = pricePairHashMap.get(orderTradeUpdateEvent.getSymbol());


                            if (orderTradeUpdateEvent.getOrderStatus().toString().equals("CANCELED") || orderTradeUpdateEvent.getOrderStatus().toString().equals("REJECTED") || orderTradeUpdateEvent.getOrderStatus().toString().equals("FILLED")) {
                                for (Order order : currencyPair.orderList) {
                                    if (order.getOrderId().equals(newOrder.getOrderId())) {
                                        // TODO ����������� �������� ������ ���������, �� ���� ����������� ������, � ����� ��� ��� ������� �� ������ ������� ����
                                        orderListForDelete.add(order);
                                    }
                                }
                                currencyPair.orderList.removeAll(orderListForDelete);
                            } else
                                currencyPair.orderList.add(newOrder);

                            currencyPair.orderList.forEach(System.out::println);
                        }
                    }));
        }, 0, 10, TimeUnit.MINUTES);


    }


    private Order createOrder(OrderTradeUpdateEvent orderTradeUpdateEvent){
        Order newOrder = new Order();
        newOrder.setOrderId(orderTradeUpdateEvent.getOrderId());
        newOrder.setSymbol(orderTradeUpdateEvent.getSymbol());
        newOrder.setStatus(orderTradeUpdateEvent.getOrderStatus());
        return newOrder;
    }
}