package com.lyalnr.torchlight;

import android.graphics.Bitmap;

/**
 * 纯 Java 图像识别：颜色阈值 + 模板匹配（零外部依赖）
 * 用于识别金色箭头等 UI 元素
 */
public class ImageFinder {

    public static class Result {
        public float x;      // 归一化 x (0~1)
        public float y;      // 归一化 y (0~1)
        public float score;  // 匹配得分 (0~1)
    }

    public interface ColorFilter {
        boolean match(int r, int g, int b);
    }

    /** 判断金色/金黄色 */
    public static boolean isGold(int r, int g, int b) {
        return r > 180 && g > 120 && b < 120 && r > b + 80 && g > b;
    }

    /** 判断橙色菱形引导石（亮金黄：R高 G中高 B低） */
    public static boolean isOrange(int r, int g, int b) {
        return r > 240 && g > 170 && g < 235 && b < 110 && r > b + 130;
    }

    /**
     * 在指定区域内找"最靠下（y最大）的颜色团块"的重心。
     * 适用于：菱形引导石排成一排，取离角色最近（屏幕最下方）的那个。
     */
    public static Result findLowestCluster(Bitmap bitmap, ColorFilter filter, float[] region) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int x0 = 0, y0 = 0, x1 = w, y1 = h;
        if (region != null && region.length == 4) {
            x0 = (int) (region[0] * w);
            y0 = (int) (region[1] * h);
            x1 = (int) (region[2] * w);
            y1 = (int) (region[3] * h);
        }

        int lowestY = -1;
        for (int y = y1 - 1; y >= y0; y--) {
            boolean has = false;
            for (int x = x0; x < x1; x++) {
                int c = bitmap.getPixel(x, y);
                int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
                if (filter.match(r, g, b)) { has = true; break; }
            }
            if (has) { lowestY = y; break; }
        }
        if (lowestY < 0) return null;

        int minY = Math.max(y0, lowestY - 120);
        long sumX = 0, sumY = 0;
        int count = 0;
        for (int y = minY; y <= lowestY; y++) {
            for (int x = x0; x < x1; x++) {
                int c = bitmap.getPixel(x, y);
                int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
                if (filter.match(r, g, b)) {
                    sumX += x;
                    sumY += y;
                    count++;
                }
            }
        }
        if (count == 0) return null;
        Result res = new Result();
        res.x = (float) sumX / count / w;
        res.y = (float) sumY / count / h;
        res.score = 1.0f;
        return res;
    }

    /**
     * 在整张图中扫描某种颜色，找到该颜色像素的重心（归一化坐标）
     */
    public static Result findColorCentroid(Bitmap bitmap, ColorFilter colorFilter, float[] region) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int x0 = 0, y0 = 0, x1 = w, y1 = h;
        if (region != null && region.length == 4) {
            x0 = (int) (region[0] * w);
            y0 = (int) (region[1] * h);
            x1 = (int) (region[2] * w);
            y1 = (int) (region[3] * h);
        }
        long sumX = 0, sumY = 0;
        int count = 0;
        int step = 3;
        for (int y = y0; y < y1; y += step) {
            for (int x = x0; x < x1; x += step) {
                int c = bitmap.getPixel(x, y);
                int r = (c >> 16) & 0xFF;
                int g = (c >> 8) & 0xFF;
                int b = c & 0xFF;
                if (colorFilter.match(r, g, b)) {
                    sumX += x;
                    sumY += y;
                    count++;
                }
            }
        }
        if (count == 0) return null;
        Result res = new Result();
        res.x = (float) sumX / count / w;
        res.y = (float) sumY / count / h;
        res.score = 1.0f;
        return res;
    }

    /** 模板匹配（归一化互相关灰度） */
    public static Result matchTemplate(Bitmap screen, Bitmap template, float[] region) {
        if (screen == null || template == null) return null;
        int w = screen.getWidth();
        int h = screen.getHeight();
        int tw = template.getWidth();
        int th = template.getHeight();
        if (tw > w || th > h) return null;

        int x0 = 0, y0 = 0, x1 = w, y1 = h;
        if (region != null && region.length == 4) {
            x0 = (int) (region[0] * w);
            y0 = (int) (region[1] * h);
            x1 = (int) (region[2] * w);
            y1 = (int) (region[3] * h);
        }

        int[] tGray = new int[tw * th];
        for (int y = 0; y < th; y++) {
            for (int x = 0; x < tw; x++) {
                int c = template.getPixel(x, y);
                int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
                tGray[y * tw + x] = (r + g + b) / 3;
            }
        }

        double bestScore = -1;
        int bestX = -1, bestY = -1;
        int step = 2;
        for (int y = y0; y <= y1 - th; y += step) {
            for (int x = x0; x <= x1 - tw; x += step) {
                long sum = 0, sumT = 0, sumS = 0;
                int sampleStep = 3;
                for (int ty = 0; ty < th; ty += sampleStep) {
                    for (int tx = 0; tx < tw; tx += sampleStep) {
                        int c = screen.getPixel(x + tx, y + ty);
                        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
                        int sg = (r + g + b) / 3;
                        int tg = tGray[ty * tw + tx];
                        sum += (long) sg * tg;
                        sumS += (long) sg * sg;
                        sumT += (long) tg * tg;
                    }
                }
                double denom = Math.sqrt((double) sumS * sumT);
                double score = denom == 0 ? 0 : sum / denom;
                if (score > bestScore) {
                    bestScore = score;
                    bestX = x;
                    bestY = y;
                }
            }
        }
        if (bestX < 0) return null;
        Result res = new Result();
        res.x = (bestX + tw / 2f) / w;
        res.y = (bestY + th / 2f) / h;
        res.score = (float) bestScore;
        return res;
    }
}