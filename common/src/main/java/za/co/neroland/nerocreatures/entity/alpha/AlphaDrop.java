package za.co.neroland.nerocreatures.entity.alpha;

/**
 * One candidate for an alpha's guaranteed rare drop, read from
 * {@code data/<entity namespace>/nerocreatures/alpha/drops/<entity path>.json}
 * ({@code {"drops": [{"item": "...", "count": 2, "weight": 1}]}}), falling back to
 * {@code data/nerocreatures/nerocreatures/alpha/drops/default.json}. Exactly one candidate drops.
 *
 * @param item   item id
 * @param count  stack size, 1-64
 * @param weight roll weight, at least 1
 */
public record AlphaDrop(String item, int count, int weight) {
}
