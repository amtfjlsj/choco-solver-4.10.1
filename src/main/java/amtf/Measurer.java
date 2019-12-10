package amtf;

public class Measurer {
    public static long matchingTime;

    public static long filterTime;

    public static long checkSCCTime;

    // 传播次数
    public static long propNum;

    public static void initial() {
        matchingTime = 0L;
        filterTime = 0L;
        checkSCCTime = 0L;
        propNum = 0L;
    }
}
