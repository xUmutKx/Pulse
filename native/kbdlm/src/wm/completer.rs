// Taken from WM Keyboard's wm-engine (https://github.com/wasi-master/wmkeyboard, native/wm-engine/src/completer.rs), MIT License, Copyright (c) 2026 Wasi Master.
//! Branch-and-bound completion over any [`Walker`]: Kotlin's
//! `TrieCompleter.from`, push for push, so equal frequencies come out in the
//! phone's order ([`JavaHeap`]).

use crate::wm::fuzzy::Walker;
use crate::wm::java_heap::JavaHeap;

/// A frontier entry: a subtree to open, or (`node == None`) a word to emit
/// at its exact frequency.
struct Frontier {
    priority: i32,
    node: Option<u32>,
    text: Vec<u16>,
}

/// Up to `limit` completions of `prefix`, best frequency first. Empty for
/// an empty prefix, as Kotlin's `complete` refuses one.
pub fn complete<W: Walker + ?Sized>(walker: &W, prefix: &[u16], limit: usize) -> Vec<(Vec<u16>, i32)> {
    if prefix.is_empty() || limit == 0 {
        return Vec::new();
    }
    let mut node = walker.root();
    for &unit in prefix {
        match walker.child(node, unit) {
            None => return Vec::new(),
            Some(next) => node = next,
        }
    }
    from(walker, node, prefix.to_vec(), limit, limit)
}

/// The `limit` best words under `node`, `text` being the path to it.
/// `capacity` bounds the result's preallocation.
pub fn from<W: Walker + ?Sized>(walker: &W, node: u32, text: Vec<u16>, limit: usize, capacity: usize) -> Vec<(Vec<u16>, i32)> {
    let mut results = Vec::with_capacity(limit.min(capacity));
    let mut heap = JavaHeap::new(|a: &Frontier, b: &Frontier| b.priority.cmp(&a.priority));
    let mut children = Vec::new();
    heap.push(Frontier { priority: walker.max_subtree(node), node: Some(node), text });
    while results.len() < limit {
        let Some(f) = heap.pop() else { break };
        match f.node {
            None => results.push((f.text, f.priority)),
            Some(node) => {
                if walker.is_word(node) {
                    heap.push(Frontier { priority: walker.frequency(node), node: None, text: f.text.clone() });
                }
                walker.children_into(node, &mut children);
                for &(label, child) in &children {
                    let mut text = Vec::with_capacity(f.text.len() + 1);
                    text.extend_from_slice(&f.text);
                    text.push(label);
                    heap.push(Frontier { priority: walker.max_subtree(child), node: Some(child), text });
                }
            }
        }
    }
    results
}
