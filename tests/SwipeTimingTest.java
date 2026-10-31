package com.example.slideassist;

public final class SwipeTimingTest {
    public static void main(String[] args) {
        int[] input = {50, 60, 70, 80, 90, 100, 150, 200, 250, 450, 700, 0, -1, 49, 51, 99, 151, 251, 701, Integer.MAX_VALUE};
        int[] expected = {50, 60, 70, 80, 90, 100, 150, 200, 250, 450, 700, 250, 250, 250, 250, 250, 250, 250, 250, 250};
        for (int i = 0; i < input.length; i++) {
            int actual = SwipeTiming.normalize(input[i]);
            if (actual != expected[i]) throw new AssertionError("duration " + input[i] + ": " + actual);
        }
        System.out.println("SwipeTimingTest passed");
    }
}
