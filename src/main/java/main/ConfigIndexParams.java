package main;

import java.math.BigDecimal;

public class ConfigIndexParams {
    private static boolean volumeIndexActivity = true;
    private static boolean positionIndexActivity = true;
    private static boolean askBidDifferenceIndexActivity = true;
    private static BigDecimal volumeIndexLimitPercent = new BigDecimal("1.2");

    public static BigDecimal getVolumeIndexLimitPercent() {
        return volumeIndexLimitPercent;
    }

    public static boolean getVolumeIndexActivity() {
        return volumeIndexActivity;
    }

    public static boolean getAskBidDifferenceIndexActivity() {
        return askBidDifferenceIndexActivity;
    }

    public static boolean getPositionIndexActivity() {
        return positionIndexActivity;
    }
}
