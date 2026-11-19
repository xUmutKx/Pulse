// Taken from WM Keyboard's wm-engine (https://github.com/wasi-master/wmkeyboard, native/wm-engine/src/java_heap.rs), MIT License, Copyright (c) 2026 Wasi Master.
//! A binary heap that pops in exactly the order `java.util.PriorityQueue`
//! does, ties included.
//!
//! The phone's walks (completion, the fuzzy beam, glide) order their
//! frontiers with a `PriorityQueue` and a comparator that only looks at a
//! score, so items with equal scores come out in an order the heap's own
//! sift steps decide. It is deterministic, but it is not alphabetical or
//! first-in-first-out, and a strip that must be identical between the Kotlin
//! and the Rust walk has to reproduce it: same array layout, same
//! `siftUp`/`siftDown`, same comparisons in the same order. This is a
//! transcription of OpenJDK's `siftUpUsingComparator`,
//! `siftDownUsingComparator`, `offer` and `poll`, which Android's libcore
//! runs too.
//!
//! The comparator returns what Java's would: `Less` means "comes out first".

use std::cmp::Ordering;

pub struct JavaHeap<T, F: Fn(&T, &T) -> Ordering> {
    items: Vec<T>,
    cmp: F,
}

impl<T, F: Fn(&T, &T) -> Ordering> JavaHeap<T, F> {
    pub fn new(cmp: F) -> JavaHeap<T, F> {
        JavaHeap { items: Vec::new(), cmp }
    }

    pub fn with_capacity(capacity: usize, cmp: F) -> JavaHeap<T, F> {
        JavaHeap { items: Vec::with_capacity(capacity), cmp }
    }

    pub fn len(&self) -> usize {
        self.items.len()
    }

    pub fn is_empty(&self) -> bool {
        self.items.is_empty()
    }

    pub fn clear(&mut self) {
        self.items.clear();
    }

    /// The head, as `peek()`.
    pub fn peek(&self) -> Option<&T> {
        self.items.first()
    }

    /// `offer(x)`: append, then sift up while `x` sorts strictly before its
    /// parent.
    pub fn push(&mut self, x: T) {
        self.items.push(x);
        let mut k = self.items.len() - 1;
        while k > 0 {
            let parent = (k - 1) >> 1;
            if (self.cmp)(&self.items[k], &self.items[parent]) != Ordering::Less {
                break;
            }
            self.items.swap(k, parent);
            k = parent;
        }
    }

    /// `poll()`: take the head, move the last element to the root and sift
    /// it down, preferring the right child only when the left sorts strictly
    /// after it, and stopping as soon as the moved element sorts no later
    /// than the chosen child.
    pub fn pop(&mut self) -> Option<T> {
        let n = self.items.len();
        if n == 0 {
            return None;
        }
        let result = self.items.swap_remove(0);
        let n = n - 1;
        if n > 0 {
            // `swap_remove` put the last element at 0, which is exactly
            // Java's `x = es[n]; siftDown(0, x)`.
            let mut k = 0;
            let half = n >> 1;
            while k < half {
                let mut child = (k << 1) + 1;
                let right = child + 1;
                if right < n && (self.cmp)(&self.items[child], &self.items[right]) == Ordering::Greater {
                    child = right;
                }
                if (self.cmp)(&self.items[k], &self.items[child]) != Ordering::Greater {
                    break;
                }
                self.items.swap(k, child);
                k = child;
            }
        }
        Some(result)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    /// Pops `(score, tag)` pairs ordered by score only (highest first, as
    /// `compareByDescending`); the tags show the tie order.
    fn drain(input: &[(i32, char)]) -> String {
        let mut heap = JavaHeap::new(|a: &(i32, char), b: &(i32, char)| b.0.cmp(&a.0));
        for &item in input {
            heap.push(item);
        }
        let mut out = String::new();
        while let Some((_, tag)) = heap.pop() {
            out.push(tag);
        }
        out
    }

    #[test]
    fn ties_come_out_in_javas_order() {
        // Recorded from java.util.PriorityQueue with a score-only comparator
        // (highest first) on OpenJDK 21, offering these in this order.
        assert_eq!("agfedcb", drain(&[(1, 'a'), (1, 'b'), (1, 'c'), (1, 'd'), (1, 'e'), (1, 'f'), (1, 'g')]));
        assert_eq!("debca", drain(&[(1, 'a'), (2, 'b'), (1, 'c'), (3, 'd'), (2, 'e')]));
        let forty: Vec<(i32, char)> = (0..40).map(|i| ((i * 7) % 5, (b'0' + i as u8) as char)).collect();
        assert_eq!("27APUF<KR4CW9H>M@OT6E1;JLB=3V8GQ5DNSI0:?", drain(&forty));
    }

    #[test]
    fn higher_scores_always_come_first() {
        let input: Vec<(i32, char)> = (0..50).map(|i| ((i * 37) % 11, 'x')).collect();
        let mut heap = JavaHeap::new(|a: &(i32, char), b: &(i32, char)| b.0.cmp(&a.0));
        for &item in &input {
            heap.push(item);
        }
        let mut last = i32::MAX;
        while let Some((score, _)) = heap.pop() {
            assert!(score <= last);
            last = score;
        }
    }
}
