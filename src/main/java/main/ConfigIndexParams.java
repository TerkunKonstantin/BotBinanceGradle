package main;

import java.math.BigDecimal;

public class ConfigIndexParams {
    private static Boolean volumeIndexActivity = true;
    private static Boolean positionIndexActivity = true;
    private static Boolean askBidDifferenceIndexActivity = true;
    private static BigDecimal volumeIndexLimitPercent = new BigDecimal("1.2");

    public static BigDecimal getVolumeIndexLimitPercent() {
        return volumeIndexLimitPercent;
    }

    public static Boolean getVolumeIndexActivity() {
        return volumeIndexActivity;
    }

    public static Boolean getAskBidDifferenceIndexActivity() {
        return askBidDifferenceIndexActivity;
    }

    public static Boolean getPositionIndexActivity() {
        return positionIndexActivity;
    }
}
