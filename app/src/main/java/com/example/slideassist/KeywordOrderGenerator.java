package com.example.slideassist;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Small synthetic batches for local keyword-reminder practice; no live order data is read. */
public final class KeywordOrderGenerator {
    private static final String[] TITLES = {
            "示例鲜果店", "示例花店 · 湖畔分店", "示例便利店 · 云杉路店", "示例面包坊",
            "示例生活超市 · 今日蔬果配送点", "示例文具书店 · 校园用品配送", "示例药房 · 中心店"
    };
    private static final String[] ROADS = {"云杉路", "晨光街", "清溪路", "望湖街", "青禾路", "林荫巷"};
    private static final String[] PLACES = {"晴川苑", "星河园", "云景公寓", "书香小区", "青禾社区", "湖畔花园"};
    private static final String[] GATES = {"东门", "北门", "服务站", "快递驿站"};
    private static final String[] GOODS = {"超市", "生鲜水果", "冷藏食品", "日用品", "鲜花", "医药", "文具", "家用小电器"};
    private static final String[] NOTES = {
            "请轻放", "到门口后联系", "放在示例门卫处", "易碎物品，请轻拿轻放并核对件数",
            "若联系不上，请放在示例置物架并拍照留存", "取货点有台阶，搬运时留意包装",
            "只在示例北门交接，不进入教学楼", "请核对包装上的件数，示例收货点在便民服务站旁"
    };

    private KeywordOrderGenerator() { }

    public static Batch generate(long seed, Batch previous) {
        Random random = new Random(seed);
        int count = 6 + random.nextInt(3);
        String[] schools = new String[count];
        for (String keyword : KeywordMatcher.keywords()) {
            if (random.nextDouble() >= .35) continue;
            int first = random.nextInt(count);
            int copies = 1 + random.nextInt(2);
            for (int copy = 0; copy < copies; copy++) {
                int destination = copy == 0 ? first : (first + 1 + random.nextInt(count - 1)) % count;
                schools[destination] = schools[destination] == null ? keyword : schools[destination] + " / " + keyword;
            }
        }
        List<Order> orders = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            Order old = previous != null && index < previous.orders.size() ? previous.orders.get(index) : null;
            String title = fresh(pick(random, TITLES), old == null ? null : old.title,
                    "示例果蔬店 · 湖畔分店", "示例烘焙坊 · 林荫分店");
            String gate = pick(random, GATES);
            String address = fresh(schoolAddress(schools[index], gate, address(random)), old == null ? null : old.address,
                    schoolAddress(schools[index], gate, "示例区白桦街17号云岚苑2栋"),
                    schoolAddress(schools[index], gate, "示例区银杏路29号晴川园5栋"));
            int tenths = 2 + random.nextInt(68);
            String goods = fresh(pick(random, GOODS) + " · " + (1 + random.nextInt(12)) + "件 · "
                    + tenths / 10 + "." + tenths % 10 + "公斤", old == null ? null : old.goods,
                    "日用品 · 3件 · 1.2公斤", "生鲜水果 · 2件 · 2.4公斤");
            String note = fresh(pick(random, NOTES), old == null ? null : old.note,
                    "请在示例服务站门口交接", "包装内有易碎物品，请平稳放置");
            orders.add(new Order(title, address, goods, note));
        }
        return new Batch(seed, orders);
    }

    private static String address(Random random) {
        String base = "示例区" + pick(random, ROADS) + (1 + random.nextInt(98)) + "号";
        switch (random.nextInt(3)) {
            case 0: return base + pick(random, GATES);
            case 1: return base + pick(random, PLACES) + (1 + random.nextInt(16)) + "栋";
            default: return base + pick(random, PLACES) + "第" + (1 + random.nextInt(16)) + "栋"
                    + (1 + random.nextInt(4)) + "单元" + (1 + random.nextInt(12)) + "层" + pick(random, GATES) + "旁便民驿站";
        }
    }

    private static String schoolAddress(String school, String gate, String address) {
        return school == null ? address : "示例交接点：" + school + " · " + gate + " · " + address;
    }

    private static String pick(Random random, String[] values) { return values[random.nextInt(values.length)]; }

    /** Bounded collision handling ensures every retained position changes even for a repeated seed. */
    private static String fresh(String candidate, String previous, String alternate, String secondAlternate) {
        if (previous == null || !withoutKeywords(candidate).equals(withoutKeywords(previous))) return candidate;
        return !withoutKeywords(alternate).equals(withoutKeywords(previous)) ? alternate : secondAlternate;
    }

    private static String withoutKeywords(String text) {
        for (String keyword : KeywordMatcher.keywords()) text = text.replace(keyword, "");
        return text;
    }

    public static final class Batch {
        public final long seed;
        public final List<Order> orders;
        private Batch(long seed, List<Order> orders) {
            this.seed = seed;
            this.orders = Collections.unmodifiableList(new ArrayList<>(orders));
        }
    }

    public static final class Order {
        public final String title, address, goods, note;
        private Order(String title, String address, String goods, String note) {
            this.title = title; this.address = address; this.goods = goods; this.note = note;
        }
    }
}
