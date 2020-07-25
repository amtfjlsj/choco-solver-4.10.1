package amtf;

import org.junit.Test;

public class Measurer {
    public static long matchingTime;

    public static long filterTime;

    public static long checkSCCTime;

    public static long numDelValuesP1;

    public static long numDelValuesP2;

    //    public static class propStatistics {
    public static int propStatisticsMask;
    public static long numNone;
    public static long numSkip;
    public static long numP1;
    public static long numP2;
    public static long numP1AndP2;

    //    public static boolean HASNOTNONE;
    private static boolean HASNONE;
    private static boolean HASNOTSKIPPED;
    private static boolean HASNOTP1;
    private static boolean HASNOTP2;
    private static boolean HASNOTP1ANDP2;
//    public static propState curPropState;
//    public static long numNone;
//    public static long numSkip;
//    public static long numP1;
//    public static long numP2;
//    public static long numP1AndP2;

    public void resetMask() {
        propStatisticsMask = 0;
    }

    public void resetPropState() {
//        HASNOTNONE = true;
        HASNONE = false;
        HASNOTSKIPPED = true;
        HASNOTP1 = true;
        HASNOTP2 = true;
        HASNOTP1ANDP2 = true;
    }

    public void enterProp() {
        if (!HASNONE) {
            numNone++;
            HASNONE = true;
        }
    }

    public void enterSkip() {
        if (HASNOTSKIPPED) {
            numSkip++;
            HASNOTSKIPPED = false;
            if (HASNONE) {
                numNone--;
                HASNONE = false;
            }
        }
    }

    public void enterP1() {
        if (HASNOTP1) {
            numP1++;
            HASNOTP1 = false;
            if (HASNONE) {
                numNone--;
                HASNONE = false;
            }
        }
    }

    public void enterP2() {
        if (HASNOTP2) {
            numP2++;
            HASNOTP2 = false;
            if (HASNONE) {
                numNone--;
                HASNONE = false;
            }
        }
    }

    public void enterP1ANDP2() {
        if (HASNOTP1ANDP2) {
            numP1AndP2++;
            if (HASNONE) {
                numNone--;
            }
        }
    }
//    }
//
//    public enum propState {
//        NONE(0),
//        SKIP(1),
//        P1(2),
//        P2(3),
//        P1ANDP2(4);
//
//        private int value = 0;
//
//        propState(int value) {     //必须是private的，否则编译错误
//            this.value = value;
//        }
//
//        public static propState valueOf(int value) {    //手写的从int到enum的转换函数
//            switch (value) {
//                case 0:
//                    return NONE;
//                case 1:
//                    return SKIP;
//                case 2:
//                    return P1;
//                case 3:
//                    return P2;
//                case 4:
//                    return P1ANDP2;
//                default:
//                    return null;
//            }
//        }
//
//    }

    // 传播次数
    public static long numProp;

    public static void initial() {
        matchingTime = 0L;
        filterTime = 0L;
        checkSCCTime = 0L;
        numProp = 0L;
        numDelValuesP1 = 0L;
        numDelValuesP2 = 0L;

        propStatisticsMask = 0;
        numNone = 0;
        numSkip = 0;
        numP1 = 0;
        numP2 = 0;
        numP1AndP2 = 0;

    }

    public void clearPropStat() {

    }
}
