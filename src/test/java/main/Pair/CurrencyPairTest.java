package main.Pair;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;

import static org.junit.Assert.*;

public class CurrencyPairTest {

    @Test
    public void calculateRang() {
    }

    @Test
    public void isCorrectForSale() {
        CurrencyPair pair = new CurrencyPair(null);
        pair.orderList = new ArrayList<>();
        assertFalse(pair.isCorrectForSale());
    }
}