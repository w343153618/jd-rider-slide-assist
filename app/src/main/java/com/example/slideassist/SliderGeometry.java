package com.example.slideassist;

/** Shared, Android-independent geometry for the visible orange strip and white handle. */
public final class SliderGeometry {
    private final float width, height, inset, thumb, radius, travel;
    private final boolean usable;

    public SliderGeometry(float width, float height, float density) {
        usable = finite(width) && finite(height) && finite(density) && density > 0
                && height > 8 * density && width > 8 * density;
        this.width = usable ? width : 0;
        this.height = usable ? height : 0;
        inset = usable ? 4 * density : 0;
        thumb = usable ? Math.min(56 * density, height - 2 * inset) : 0;
        radius = usable ? Math.min(8 * density, Math.min(thumb, height - 2 * inset) / 2) : 0;
        travel = usable ? Math.max(0, width - 2 * inset - thumb) : 0;
    }

    public float thumbLeft() { return inset; }
    public float thumbTop() { return inset; }
    public float thumbRight() { return inset + thumb; }
    public float thumbBottom() { return height - inset; }
    public float thumbWidth() { return thumb; }
    public float cornerRadius() { return radius; }
    public float travel() { return travel; }

    /** Hit-test the same rounded white shape that the tool draws, at its initial position. */
    public boolean inHandle(float x, float y) {
        if (!usable || travel <= 0 || !finite(x) || !finite(y) || x < inset || y < inset
                || x >= thumbRight() || y >= thumbBottom()) return false;
        float nearestX = Math.max(inset + radius, Math.min(thumbRight() - radius, x));
        float nearestY = Math.max(inset + radius, Math.min(thumbBottom() - radius, y));
        float dx = x - nearestX, dy = y - nearestY;
        return dx * dx + dy * dy <= radius * radius;
    }

    /** Full thumb translation keeps the user's grab offset, without forcing a center start. */
    public float endX(float localStartX) { return localStartX + travel; }

    public Swipe fromLocal(float downX, float downY, float originX, float originY) {
        if (!inHandle(downX, downY) || !finite(originX) || !finite(originY))
            throw new IllegalArgumentException("Swipe must start inside the initial white handle");
        return new Swipe(originX + downX, originY + downY, originX + endX(downX), originY + downY);
    }

    private static boolean finite(float value) { return !Float.isNaN(value) && !Float.isInfinite(value); }

    public static final class Swipe {
        public final float startX, startY, endX, endY;
        private Swipe(float startX, float startY, float endX, float endY) {
            this.startX = startX; this.startY = startY; this.endX = endX; this.endY = endY;
        }
    }
}
