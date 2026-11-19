// Taken from WM Keyboard's wm-engine (https://github.com/wasi-master/wmkeyboard, native/wm-engine/src/sort.rs), MIT License, Copyright (c) 2026 Wasi Master.
//! One stable sort for the crate.
//!
//! `slice::sort_by` is generic over both the element and the comparator, and
//! the standard library's stable sort is large: each call site, with its own
//! closure, got its own 5 to 27 KB copy of it. In the Android library that
//! was 141 functions, about 430 KB of its 1.5 MB of code. [`by`] and
//! [`by_key`] sort the positions instead, through one comparator behind
//! `dyn`, so the library holds a single copy, and then move the elements into
//! that order.
//!
//! The result is the same as `slice::sort_by`'s: a stable sort's output is
//! fixed by the comparator alone when the comparator is a total order, which
//! every caller's is (integers, UTF-16 order, `f64::total_cmp`). Sorts on hot
//! paths over plain numbers (`kth_best`) keep the slice method, whose one
//! copy they share.

use std::cmp::Ordering;

/// `v.sort_by(cmp)`.
pub fn by<T>(v: &mut [T], mut cmp: impl FnMut(&T, &T) -> Ordering) {
    if v.len() < 2 {
        return;
    }
    let order = positions(v.len(), &mut |a, b| cmp(&v[a], &v[b]));
    permute(v, order);
}

/// `v.sort_by_key(key)`.
pub fn by_key<T, K: Ord>(v: &mut [T], mut key: impl FnMut(&T) -> K) {
    by(v, |a, b| key(a).cmp(&key(b)))
}

/// The positions `0..len` stably sorted by `cmp`. The only instantiation of
/// the standard library's sort behind [`by`].
#[inline(never)]
fn positions(len: usize, cmp: &mut dyn FnMut(usize, usize) -> Ordering) -> Vec<u32> {
    let len = u32::try_from(len).expect("sort: more than u32::MAX elements");
    let mut order: Vec<u32> = (0..len).collect();
    order.sort_by(|&a, &b| cmp(a as usize, b as usize));
    order
}

/// Puts `v[order[i]]` at `i` for every `i`, following each cycle of the
/// permutation with swaps. `order` marks the positions it has placed.
fn permute<T>(v: &mut [T], mut order: Vec<u32>) {
    const DONE: u32 = u32::MAX;
    for start in 0..v.len() {
        if order[start] == DONE {
            continue;
        }
        // `v[j]` holds what was at `start`; everything later in the cycle is
        // still where it began.
        let mut j = start;
        loop {
            let from = order[j] as usize;
            order[j] = DONE;
            if from == start {
                break;
            }
            v.swap(j, from);
            j = from;
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    /// A small LCG, so the cases are the same on every run.
    fn lcg(seed: &mut u64) -> u64 {
        *seed = seed.wrapping_mul(6364136223846793005).wrapping_add(1442695040888963407);
        *seed >> 33
    }

    #[test]
    fn matches_the_slice_sort_with_ties() {
        let mut seed = 7;
        for len in [0usize, 1, 2, 3, 7, 16, 20, 21, 33, 64, 100, 257, 1000, 4097] {
            for range in [1u64, 3, 50, 1 << 20] {
                // (key, original position): the position shows stability.
                let v: Vec<(u64, usize)> = (0..len).map(|i| (lcg(&mut seed) % range, i)).collect();
                let mut want = v.clone();
                want.sort_by(|a, b| b.0.cmp(&a.0));
                let mut got = v.clone();
                by(&mut got, |a, b| b.0.cmp(&a.0));
                assert_eq!(want, got, "len {len} range {range}");
                let mut want = v.clone();
                want.sort_by_key(|e| e.0 % 7);
                let mut got = v;
                by_key(&mut got, |e| e.0 % 7);
                assert_eq!(want, got, "by_key len {len} range {range}");
            }
        }
    }

    #[test]
    fn moves_owned_elements() {
        let mut v: Vec<String> = ["pear", "fig", "apple", "kiwi", "date", "fig"].map(String::from).to_vec();
        by_key(&mut v, |s| s.len());
        assert_eq!(v, ["fig", "fig", "pear", "kiwi", "date", "apple"]);
        let mut sorted = v.clone();
        sorted.sort();
        by(&mut v, |a, b| a.cmp(b));
        assert_eq!(v, sorted);
    }

    #[test]
    fn floats_by_total_order() {
        let mut seed = 11;
        let v: Vec<(f64, u32)> = (0..500)
            .map(|i| {
                let r = lcg(&mut seed);
                let x = match r % 9 {
                    0 => -0.0,
                    1 => 0.0,
                    2 => f64::NEG_INFINITY,
                    3 => f64::INFINITY,
                    _ => (r % 40) as f64 / 4.0 - 5.0,
                };
                (x, i)
            })
            .collect();
        let mut want = v.clone();
        want.sort_by(|a, b| b.0.total_cmp(&a.0));
        let mut got = v;
        by(&mut got, |a, b| b.0.total_cmp(&a.0));
        assert_eq!(want.iter().map(|e| (e.0.to_bits(), e.1)).collect::<Vec<_>>(), got.iter().map(|e| (e.0.to_bits(), e.1)).collect::<Vec<_>>());
    }
}
