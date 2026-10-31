package com.example.slideassist;

public final class SwipeTimingTest {
    public static void main(String[] args) {
        int[] input = {250, 450, 700, 0, -1, 251, 701, Integer.MAX_VALUE};
        int[] expected = {250, 450, 700, 250, 250, 250, 250, 250};
        for (int i = 0; i < input.length; i++) {
            int actual = SwipeTiming.normalize(input[i]);
            if (actual != expected[i]) throw new AssertionError("duration " + input[i] + ": " + actual);
        }
        System.out.println("SwipeTimingTest passed");
    }
}
