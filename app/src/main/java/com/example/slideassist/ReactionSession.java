package com.example.slideassist;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Bounded, process-local reaction trials. All supplied times use Android uptime milliseconds. */
public final class ReactionSession {
    public static final int MAX_ROUNDS = 50, MAX_ATTEMPTS = 200;
    private final List<MutableRound> rounds = new ArrayList<>();
    private MutableRound current;
    private int totalAttempts;
    private long droppedRounds, droppedAttempts;

    public synchronized boolean beginRound(ReactionOrderGenerator.Batch batch) {
        if (batch == null) throw new IllegalArgumentException("Missing practice orders");
        if (rounds.size() >= MAX_ROUNDS) { droppedRounds++; return false; }
        if (current != null && !current.closed) {
            current.closed = true;
            if (current.winner == null) current.status = "refreshed";
        }
        current = new MutableRound(batch); rounds.add(current); return true;
    }

    /** A UI-frame scheduling timestamp, never a claim about physical pixel presentation. */
    public synchronized void markPresented(long time) {
        if (time < 0) throw new IllegalArgumentException("Invalid presentation clock");
        if (current != null && !current.closed && current.presentedTime < 0) current.presentedTime = time;
    }

    public synchronized boolean recordAttempt(int orderIndex, long down, long firstMove, long target,
                                             long finish, boolean validStart, String outcome,
                                             InteractionRecorder.TraceSnapshot trace) {
        if (current == null || current.closed || orderIndex < 0 || orderIndex >= current.batch.orders.size()) return false;
        if (totalAttempts >= MAX_ATTEMPTS) { droppedAttempts++; return false; }
        for (Attempt previous : current.attempts) {
            if (previous.orderIndex == orderIndex && previous.downTime == down) return false;
        }
        boolean timingValid = down >= 0 && finish >= down
                && (firstMove == -1 || firstMove >= down && firstMove <= finish)
                && (target == -1 || target >= down && target <= finish)
                && (firstMove == -1 || target == -1 || firstMove <= target);
        boolean practice = current.winner != null;
        boolean eligible = timingValid && !practice && current.presentedTime >= 0 && down >= current.presentedTime;
        boolean correct = orderIndex == current.batch.targetIndex;
        boolean scored = eligible && validStart && correct && "success".equals(outcome) && target >= down;
        Attempt attempt = new Attempt(++totalAttempts, orderIndex, down, firstMove, target, finish,
                validStart, outcome == null ? "unknown" : outcome, correct, eligible, scored, practice, timingValid, trace);
        current.attempts.add(attempt);
        if (eligible) {
            if (validStart) {
                if (current.firstValidDownTime >= 0) current.retryCount++;
                current.firstValidDownTime = first(current.firstValidDownTime, down);
                if (correct) current.firstCorrectDownTime = first(current.firstCorrectDownTime, down);
                else current.wrongAttempts++;
            } else current.invalidStarts++;
            if ("cancelled".equals(outcome)) current.cancelledAttempts++;
        }
        if (scored) {
            current.winner = attempt; current.status = "success"; current.finishedTime = finish;
        }
        return true;
    }

    public synchronized void recordScroll(int scrollY, long time) {
        if (!activeAt(time) || scrollY < 0) return;
        int delta = scrollY - current.lastScrollY;
        if (delta == 0) return;
        int direction = delta > 0 ? 1 : -1;
        if (current.lastDirection != 0 && direction != current.lastDirection) current.directionChanges++;
        current.lastDirection = direction;
        current.scrollDistancePx += Math.abs((long) delta);
        current.maxScrollYPx = Math.max(current.maxScrollYPx, scrollY);
        current.lastScrollY = scrollY; current.scrollEvents++;
        current.firstScrollTime = first(current.firstScrollTime, time); current.lastScrollTime = time;
    }

    /** Layout visibility estimate for the marker glyph; this is not eye tracking. */
    public synchronized void markTargetVisible(long time) {
        if (activeAt(time) && current.targetVisibleTime < 0) current.targetVisibleTime = time;
    }

    public synchronized void finishRound(String reason, long time) {
        if (current == null || current.closed) return;
        current.closed = true;
        if (current.winner == null) {
            current.status = reason == null || reason.isEmpty() ? "stopped" : reason;
            current.finishedTime = time >= current.presentedTime && time >= 0 ? time : -1;
        }
    }

    private boolean activeAt(long time) {
        return current != null && !current.closed && current.winner == null
                && current.presentedTime >= 0 && time >= current.presentedTime;
    }
    private static long first(long previous, long value) { return previous < 0 ? value : Math.min(previous, value); }

    public synchronized Snapshot snapshot() {
        List<Round> copy = new ArrayList<>();
        for (MutableRound round : rounds) copy.add(new Round(round));
        return new Snapshot(copy, totalAttempts, droppedRounds, droppedAttempts);
    }

    public static final class Snapshot {
        public final List<Round> rounds;
        public final int totalAttempts;
        public final long droppedRounds, droppedAttempts;
        private Snapshot(List<Round> rounds, int totalAttempts, long droppedRounds, long droppedAttempts) {
            this.rounds = Collections.unmodifiableList(rounds); this.totalAttempts = totalAttempts;
            this.droppedRounds = droppedRounds; this.droppedAttempts = droppedAttempts;
        }
    }

    public static final class Attempt {
        public final int index, orderIndex;
        public final long downTime, firstMoveTime, targetTime, finishedTime;
        public final boolean validStart, correctOrder, eligible, scored, practice, timingValid;
        public final String outcome;
        public final InteractionRecorder.TraceSnapshot trace;
        private Attempt(int index, int orderIndex, long down, long firstMove, long target, long finish,
                        boolean validStart, String outcome, boolean correct, boolean eligible,
                        boolean scored, boolean practice, boolean timingValid, InteractionRecorder.TraceSnapshot trace) {
            this.index = index; this.orderIndex = orderIndex; downTime = down; firstMoveTime = firstMove;
            targetTime = target; finishedTime = finish; this.validStart = validStart; this.outcome = outcome;
            correctOrder = correct; this.eligible = eligible; this.scored = scored; this.practice = practice;
            this.timingValid = timingValid; this.trace = trace;
        }
    }

    public static final class Round {
        public final ReactionOrderGenerator.Batch batch;
        public final long presentedTime, firstValidDownTime, firstCorrectDownTime, targetVisibleTime, finishedTime;
        public final long scrollDistancePx, firstScrollTime, lastScrollTime;
        public final int maxScrollYPx, directionChanges, scrollEvents, wrongAttempts, invalidStarts, cancelledAttempts, retryCount;
        public final String status;
        public final boolean closed;
        public final List<Attempt> attempts;
        private final Attempt winningAttempt;
        private Round(MutableRound round) {
            batch = round.batch; presentedTime = round.presentedTime; firstValidDownTime = round.firstValidDownTime;
            firstCorrectDownTime = round.firstCorrectDownTime; targetVisibleTime = round.targetVisibleTime;
            finishedTime = round.finishedTime; scrollDistancePx = round.scrollDistancePx;
            firstScrollTime = round.firstScrollTime; lastScrollTime = round.lastScrollTime;
            maxScrollYPx = round.maxScrollYPx; directionChanges = round.directionChanges; scrollEvents = round.scrollEvents;
            wrongAttempts = round.wrongAttempts; invalidStarts = round.invalidStarts;
            cancelledAttempts = round.cancelledAttempts; retryCount = round.retryCount;
            status = round.status; closed = round.closed;
            attempts = Collections.unmodifiableList(new ArrayList<>(round.attempts)); winningAttempt = round.winner;
        }
        public Attempt winner() { return winningAttempt; }
        public double searchMillis() { return duration(presentedTime, firstCorrectDownTime); }
        public double dragMillis() { return winner() == null ? Double.NaN : duration(winner().downTime, winner().targetTime); }
        public double totalMillis() { return winner() == null ? Double.NaN : duration(presentedTime, winner().targetTime); }
        public double initiationMillis() { return winner() == null ? Double.NaN : duration(winner().downTime, winner().firstMoveTime); }
        public double holdMillis() { return winner() == null ? Double.NaN : duration(winner().targetTime, winner().finishedTime); }
        public double visibleToDownMillis() { return duration(targetVisibleTime, firstCorrectDownTime); }
    }

    public static double duration(long from, long to) { return from >= 0 && to >= from ? (double) (to - from) : Double.NaN; }

    private static final class MutableRound {
        final ReactionOrderGenerator.Batch batch;
        final List<Attempt> attempts = new ArrayList<>();
        long presentedTime = -1, firstValidDownTime = -1, firstCorrectDownTime = -1, targetVisibleTime = -1, finishedTime = -1;
        long scrollDistancePx, firstScrollTime = -1, lastScrollTime = -1;
        int maxScrollYPx, directionChanges, scrollEvents, wrongAttempts, invalidStarts, cancelledAttempts, retryCount;
        int lastScrollY, lastDirection;
        boolean closed;
        String status = "active";
        Attempt winner;
        MutableRound(ReactionOrderGenerator.Batch batch) { this.batch = batch; }
    }
}
