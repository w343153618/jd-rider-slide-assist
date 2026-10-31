package com.example.slideassist;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Seeded fictional practice orders. The target is ordinary text, not a UI hint. */
public final class ReactionOrderGenerator {
    private static final String[] SHOPS = {"晴禾便利", "青竹果铺", "橙光食集", "小巷书屋", "晨星药房", "云桥花坊", "湖畔超市", "松叶餐厅"};
    private static final String[] GOODS = {"鲜果", "饮品", "文具", "日用品", "便当", "蔬菜", "药品", "面包"};
    private static final String[] NOTES = {"请轻放", "先联系再送", "走西侧入口", "包装勿倒置", "需核对件数", "到门口请联系", "可放服务台", "请按楼号寻找", "注意易碎", "请保持密封", "从小区南门进入", "送到前请再确认"};
    private static final String[] ROADS = {"青云路", "星河街", "春晓巷", "橙园路", "云松街", "湖光路", "花桥街", "竹影巷"};
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
        for (int i = 0; i < 6; i++) {
            String title, address, note;
            do {
                title = pick(random, SHOPS) + "·" + (10 + random.nextInt(990)) + "号店 "
                        + pick(random, GOODS) + " " + (1 + random.nextInt(9)) + "件";
                if (random.nextBoolean()) title += "（" + pick(random, NOTES) + "，" + pick(random, NOTES) + "）";
            } while (!titles.add(title));
            do {
                address = "虚构市" + pick(random, ROADS) + (1 + random.nextInt(200)) + "号 "
                        + pick(random, PLACES) + (1 + random.nextInt(20)) + "栋";
                if (random.nextBoolean()) address += " " + (1 + random.nextInt(3)) + "单元" + (101 + random.nextInt(900)) + "室";
                if (random.nextBoolean()) address += "，" + pick(random, NOTES);
            } while (!addresses.add(address));
            do {
                StringBuilder text = new StringBuilder("练习备注：");
                int count = 1 + random.nextInt(5);
                for (int n = 0; n < count; n++) text.append(n == 0 ? "" : "，").append(pick(random, NOTES));
                note = text.append("；核对码").append(1000 + random.nextInt(9000)).toString();
            } while (!notes.add(note));
            orders.add(new Order(title, address, note));
        }
        int target = random.nextInt(6), fieldIndex = random.nextInt(3);
        String field = new String[]{"title", "address", "note"}[fieldIndex];
        Order order = orders.get(target);
        String original = fieldIndex == 0 ? order.title : fieldIndex == 1 ? order.address : order.note;
        int offset = random.nextInt(original.length() + 1);
        String marked = original.substring(0, offset) + "田" + original.substring(offset);
        orders.set(target, new Order(fieldIndex == 0 ? marked : order.title,
                fieldIndex == 1 ? marked : order.address, fieldIndex == 2 ? marked : order.note));
        return new Batch(seed, orders, target, field, offset);
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
