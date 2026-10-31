package com.example.slideassist;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Seeded fictional practice orders. The target is ordinary text, not a UI hint. */
public final class ReactionOrderGenerator {
    public static final int ORDER_COUNT = 3, TARGET_CANDIDATE_COUNT = 2;
    private static final String[] SHOPS = {"晴禾便利", "青竹果铺", "橙光食集", "小巷书屋", "晨星药房", "云桥花坊", "湖畔超市", "松叶餐厅"};
    private static final String[] GOODS = {"鲜果", "饮品", "文具", "日用品", "便当", "蔬菜", "药品", "面包"};
    private static final String[] NOTES = {"请轻放", "先联系再送", "走西侧入口", "包装勿倒置", "需核对件数", "到门口请联系", "可放服务台", "请按楼号寻找", "注意易碎", "请保持密封", "从小区南门进入", "送到前请再确认"};
    private static final String[] ROADS = {"青云路", "星河街", "春晓巷", "橙园路", "云松街", "湖光路", "花桥街", "竹影巷"};
    private static final String[] TARGET_ROADS = {"田园路", "青田街", "福田巷", "田丰路", "乐田街", "田心路"};
    private static final String[] PLACES = {"社区", "公寓", "办公楼", "园区", "商厦", "书院", "小区", "广场"};
    private ReactionOrderGenerator() { }

    public static Batch generate(long seed, Batch previous) {
        Random random = new Random(seed);
        Set<String> titles = new HashSet<>(), addresses = new HashSet<>(), notes = new HashSet<>();
        if (previous != null) for (Order order : previous.orders) {
            titles.add(order.title.replace("田", "")); addresses.add(order.address.replace("田", ""));
            notes.add(order.note.replace("田", ""));
        }
        List<Order> orders = new ArrayList<>();
        for (int i = 0; i < ORDER_COUNT; i++) {
            String title, address, note;
            do {
                title = pick(random, SHOPS) + "·" + (10 + random.nextInt(990)) + "号店 "
                        + pick(random, GOODS) + " " + (1 + random.nextInt(9)) + "件";
            } while (!titles.add(title));
            do {
                address = address(random, ROADS);
            } while (!addresses.add(address));
            do {
                note = pick(random, NOTES) + " · 核对" + (1000 + random.nextInt(9000));
            } while (!notes.add(note));
            orders.add(new Order(title, address, note));
        }
        int target = random.nextInt(TARGET_CANDIDATE_COUNT);
        Order order = orders.get(target);
        String marked;
        do {
            // The marker is an ordinary character in a fictional road name, never a separate hint.
            marked = address(random, TARGET_ROADS);
        } while (!addresses.add(marked.replace("田", "")));
        orders.set(target, new Order(order.title, marked, order.note));
        return new Batch(seed, orders, target, "address", marked.indexOf('田'));
    }

    private static String address(Random random, String[] roads) {
        String value = "虚构市" + pick(random, roads) + (1 + random.nextInt(200)) + "号 "
                + pick(random, PLACES) + (1 + random.nextInt(20)) + "栋";
        if (random.nextBoolean()) value += " " + (1 + random.nextInt(3)) + "单元" + (101 + random.nextInt(900)) + "室";
        return value;
    }

    private static String pick(Random random, String[] values) { return values[random.nextInt(values.length)]; }

    public static final class Order {
        public final String title, address, note;
        private Order(String title, String address, String note) { this.title = title; this.address = address; this.note = note; }
    }
    public static final class Batch {
        public final long seed;
        public final List<Order> orders;
        public final int targetIndex, targetOffset;
        public final String targetField;
        private Batch(long seed, List<Order> orders, int targetIndex, String targetField, int targetOffset) {
            this.seed = seed; this.orders = Collections.unmodifiableList(new ArrayList<>(orders));
            this.targetIndex = targetIndex; this.targetField = targetField; this.targetOffset = targetOffset;
        }
    }
}
