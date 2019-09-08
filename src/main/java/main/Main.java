package main;

import main.Listeners.AccountBalanceUpdateer;
import main.Pair.TradeStarter;
import org.apache.log4j.Logger;


public class Main {

    private static final Logger log = Logger.getLogger(Main.class);

    public static void main(String[] args) {

        log.info("Работа начата");

        DataInitialization dataInitialization = new DataInitialization();
        AccountBalanceUpdateer accountBalanceUpdateer = new AccountBalanceUpdateer(dataInitialization);

        log.info("Инициализация баланса, торговых пар и цен завершена, можно приступать к работе");

        TradeStarter tradeStarter = new TradeStarter(dataInitialization, accountBalanceUpdateer);
        tradeStarter.startTrade();

    }
}