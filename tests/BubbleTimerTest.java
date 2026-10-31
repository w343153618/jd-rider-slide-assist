package com.example.slideassist;

public final class BubbleTimerTest {
    public static void main(String[] args) {
        check("00:00".equals(BubbleTimer.formatElapsedMillis(0)), "timer starts at zero");
        check("00:00".equals(BubbleTimer.formatElapsedMillis(-1)), "negative duration cannot become a clock");
        check("00:00".equals(BubbleTimer.formatElapsedMillis(999)), "subsecond duration stays zero seconds");
        check("00:01".equals(BubbleTimer.formatElapsedMillis(1000)), "one elapsed second");
        check("01:05".equals(BubbleTimer.formatElapsedMillis(65000)), "minutes and seconds");
        check("59:59".equals(BubbleTimer.formatElapsedMillis(3599999)), "last minute before hour");
        check("1:00:00".equals(BubbleTimer.formatElapsedMillis(3600000)), "hours do not wrap to zero");
        check("27:04:09".equals(BubbleTimer.formatElapsedMillis(97449000)), "timer continues past one day");
        System.out.println("BubbleTimerTest passed");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
