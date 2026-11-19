// Taken from WM Keyboard's wm-engine (https://github.com/wasi-master/wmkeyboard, native/wm-engine/src/wmdict.rs), MIT License, Copyright (c) 2026 Wasi Master.
//! The `.wmdict` binary dictionary (version 3): a frequency-weighted prefix
//! trie as flat arrays, written so a reader can walk the file in place.
//!
//! [`PackedTrie::of`] lays a word list out breadth-first; [`write`] is the
//! Kotlin `PackedTrieCodec.write`, byte for byte, which the tests prove
//! against the dictionaries the Android build compiles; [`Dict`] is
//! `MappedTrie`: it reads the bytes where they are (a `Vec<u8>`, a memory
//! map, anything `AsRef<[u8]>`), with no parse and no copy at open.
//!
//! Layout, big-endian, every section padded to four bytes:
//! ```text
//! magic       u32   "WMDC"
//! version     u16   3
//! flags       u16   bit 0 = labels are byte indices into the symbol table,
//!                   bit 1 = childStart is per-node counts plus checkpoints
//! wordCount   i32
//! nodeCount   i32
//! edgeCount   i32   nodeCount - 1
//! symbolCount i32   distinct edge labels when bit 0, else 0
//! offsets     i32 × 7   symbols, childStart, checkpoint, edgeLabel, freq,
//!                       maxSubtree, isWord
//! symbols     u16 × symbolCount, ascending
//! childStart  u8 × nodeCount (children per node) when bit 1, else i32 × (nodeCount + 1)
//! checkpoint  i32 × (nodeCount / 32 + 1) when bit 1: childStart at every 32nd node
//! edgeLabel   u8 × edgeCount when bit 0, else u16 × edgeCount
//! freq        u16 × nodeCount, minifloat
//! maxSubtree  u16 × nodeCount, minifloat
//! isWord      bitset, LSB first within each byte
//! ```
//! Nodes are numbered breadth-first and edges in the same sweep, so the
//! target of edge `e` is node `e + 1` and the file stores no child array.
//! Labels are UTF-16 code units, as the Kotlin side types them: a word with
//! a character outside the BMP takes two edges.

use crate::wm::completer;
use crate::wm::freq_codec;
use std::cmp::Ordering;

pub const MAGIC: u32 = 0x574D_4443;
pub const VERSION: u16 = 3;
pub const FLAG_LABELS_U8: u16 = 1;
pub const FLAG_DEGREE_U8: u16 = 2;
pub const MAX_SYMBOLS: usize = 256;
pub const MAX_DEGREE: usize = 255;
pub const CHECKPOINT_STRIDE: usize = 32;
const CHECKPOINT_SHIFT: u32 = 5;
pub const HEADER_BYTES: usize = 4 + 2 + 2 + 4 + 4 + 4 + 4 + 7 * 4;
/// Fills the symbol table's unused tail: a corrupt label byte reads this
/// permanent noncharacter rather than running off the table.
const UNUSED_SYMBOL: u16 = 0xFFFF;

pub fn pad4(bytes: usize) -> usize {
    (bytes + 3) & !3
}

/// A word and its frequency, as a completion hands them back.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Suggestion {
    pub word: String,
    pub frequency: i32,
}

/// The trie as arrays, the shape the file is an image of. Node 0 is the
/// root; node `i`'s edges are `child_start[i]..child_start[i + 1]`, sorted
/// by label; edge `e` leads to node `e + 1`.
pub struct PackedTrie {
    pub child_start: Vec<u32>,
    pub edge_label: Vec<u16>,
    pub freq: Vec<i32>,
    pub is_word: Vec<bool>,
    pub max_subtree: Vec<i32>,
}

impl PackedTrie {
    /// Lays `entries` out breadth-first. Empty words are dropped; a word
    /// listed twice keeps its higher frequency; frequencies are snapped to
    /// the minifloat grid here, so the arrays and the file hold the same
    /// numbers.
    pub fn of<S: AsRef<str>>(entries: &[(S, i32)]) -> PackedTrie {
        let mut words: Vec<(Vec<u16>, i32)> = entries
            .iter()
            .filter(|(w, _)| !w.as_ref().is_empty())
            .map(|(w, f)| (w.as_ref().encode_utf16().collect(), *f))
            .collect();
        // Code-unit order, which is what the edge binary search compares.
        words.sort_by(|a, b| a.0.cmp(&b.0));

        let mut node_count = 1usize;
        for i in 0..words.len() {
            let shared = if i == 0 {
                0
            } else {
                common_prefix(&words[i - 1].0, &words[i].0)
            };
            node_count += words[i].0.len() - shared;
        }

        let mut child_start = vec![0u32; node_count + 1];
        let mut edge_label = vec![0u16; node_count - 1];
        let mut freq = vec![0i32; node_count];
        let mut is_word = vec![false; node_count];
        let mut max_subtree = vec![0i32; node_count];
        let mut range_start = vec![0usize; node_count];
        let mut range_end = vec![0usize; node_count];

        // Breadth-first ids and the CSR layout in one pass: every word under
        // a node at depth d shares its prefix and is at least d long, so its
        // unit at d picks the child, and the sorted order hands the children
        // out sorted by label.
        range_end[0] = words.len();
        let mut next_id = 1usize;
        let mut edge_cursor = 0usize;
        let mut depth = 0usize;
        let mut depth_end = 1usize;
        for id in 0..node_count {
            if id == depth_end {
                depth += 1;
                depth_end = next_id;
            }
            let mut i = range_start[id];
            let end = range_end[id];
            while i < end && words[i].0.len() == depth {
                is_word[id] = true;
                freq[id] = freq[id].max(freq_codec::round(words[i].1));
                i += 1;
            }
            child_start[id] = edge_cursor as u32;
            while i < end {
                let unit = words[i].0[depth];
                let mut j = i + 1;
                while j < end && words[j].0[depth] == unit {
                    j += 1;
                }
                let child = next_id;
                next_id += 1;
                range_start[child] = i;
                range_end[child] = j;
                edge_label[edge_cursor] = unit;
                edge_cursor += 1;
                i = j;
            }
        }
        child_start[node_count] = edge_cursor as u32;

        // Children have larger ids than their parent, so a reverse sweep
        // sees every child before the node.
        for id in (0..node_count).rev() {
            let mut best = if is_word[id] { freq[id] } else { 0 };
            for e in child_start[id]..child_start[id + 1] {
                best = best.max(max_subtree[e as usize + 1]);
            }
            max_subtree[id] = best;
        }

        PackedTrie {
            child_start,
            edge_label,
            freq,
            is_word,
            max_subtree,
        }
    }

    pub fn node_count(&self) -> usize {
        self.freq.len()
    }

    pub fn word_count(&self) -> usize {
        self.is_word.iter().filter(|w| **w).count()
    }
}

fn common_prefix(a: &[u16], b: &[u16]) -> usize {
    a.iter().zip(b).take_while(|(x, y)| x == y).count()
}

/// The file for `trie`.
pub fn write(trie: &PackedTrie) -> Vec<u8> {
    let node_count = trie.node_count();
    let edge_count = trie.edge_label.len();
    let bitset_bytes = node_count.div_ceil(8);

    let mut symbols: Vec<u16> = trie.edge_label.clone();
    symbols.sort_unstable();
    symbols.dedup();
    let labels_are_bytes = symbols.len() <= MAX_SYMBOLS;
    let symbol_count = if labels_are_bytes { symbols.len() } else { 0 };
    let label_bytes = if labels_are_bytes {
        edge_count
    } else {
        edge_count * 2
    };

    let max_degree = (0..node_count)
        .map(|n| (trie.child_start[n + 1] - trie.child_start[n]) as usize)
        .max()
        .unwrap_or(0);
    let degrees_are_bytes = max_degree <= MAX_DEGREE;
    let child_start_bytes = if degrees_are_bytes {
        pad4(node_count)
    } else {
        (node_count + 1) * 4
    };
    let checkpoint_count = if degrees_are_bytes {
        (node_count >> CHECKPOINT_SHIFT) + 1
    } else {
        0
    };

    let mut cursor = HEADER_BYTES;
    let mut offsets = [0u32; 7];
    let mut section = |size: usize| {
        let at = cursor;
        cursor += size;
        at as u32
    };
    offsets[0] = section(pad4(symbol_count * 2));
    offsets[1] = section(child_start_bytes);
    offsets[2] = section(checkpoint_count * 4);
    offsets[3] = section(pad4(label_bytes));
    offsets[4] = section(pad4(node_count * 2));
    offsets[5] = section(pad4(node_count * 2));
    offsets[6] = section(pad4(bitset_bytes));

    let mut out = Writer::with_capacity(cursor);
    out.u32(MAGIC);
    out.u16(VERSION);
    out.u16(
        if labels_are_bytes { FLAG_LABELS_U8 } else { 0 }
            | if degrees_are_bytes { FLAG_DEGREE_U8 } else { 0 },
    );
    out.u32(trie.word_count() as u32);
    out.u32(node_count as u32);
    out.u32(edge_count as u32);
    out.u32(symbol_count as u32);
    for offset in offsets {
        out.u32(offset);
    }

    for &symbol in &symbols[..symbol_count] {
        out.u16(symbol);
    }
    out.pad4();

    if degrees_are_bytes {
        for n in 0..node_count {
            out.u8((trie.child_start[n + 1] - trie.child_start[n]) as u8);
        }
        out.pad4();
        // Checkpoint k is childStart at node k * 32; the last one covers a
        // read of childStart(nodeCount), the end of the final node's edges.
        for k in 0..checkpoint_count {
            out.u32(trie.child_start[k << CHECKPOINT_SHIFT]);
        }
    } else {
        for &value in &trie.child_start {
            out.u32(value);
        }
    }

    if labels_are_bytes {
        for label in &trie.edge_label {
            out.u8(symbols
                .binary_search(label)
                .expect("every label is in the table") as u8);
        }
    } else {
        for &label in &trie.edge_label {
            out.u16(label);
        }
    }
    out.pad4();

    for &value in &trie.freq {
        out.u16(freq_codec::encode(value));
    }
    out.pad4();
    for &value in &trie.max_subtree {
        out.u16(freq_codec::encode(value));
    }
    out.pad4();

    let mut byte = 0u8;
    for (id, &word) in trie.is_word.iter().enumerate() {
        if word {
            byte |= 1 << (id & 7);
        }
        if id & 7 == 7 {
            out.u8(byte);
            byte = 0;
        }
    }
    if node_count & 7 != 0 {
        out.u8(byte);
    }
    out.pad4();
    debug_assert_eq!(cursor, out.bytes.len());
    out.bytes
}

/// A big-endian byte sink, shared with the n-gram pack writer.
pub(crate) struct Writer {
    pub bytes: Vec<u8>,
}

impl Writer {
    pub fn with_capacity(bytes: usize) -> Writer {
        Writer {
            bytes: Vec::with_capacity(bytes),
        }
    }
    pub fn u8(&mut self, v: u8) {
        self.bytes.push(v);
    }
    pub fn u16(&mut self, v: u16) {
        self.bytes.extend_from_slice(&v.to_be_bytes());
    }
    pub fn u32(&mut self, v: u32) {
        self.bytes.extend_from_slice(&v.to_be_bytes());
    }
    pub fn pad4(&mut self) {
        while self.bytes.len() & 3 != 0 {
            self.bytes.push(0);
        }
    }
    pub fn pad_to(&mut self, len: usize) {
        self.bytes.resize(len, 0);
    }
}

pub(crate) fn be_u16(b: &[u8], at: usize) -> u16 {
    u16::from_be_bytes([b[at], b[at + 1]])
}

pub(crate) fn be_u32(b: &[u8], at: usize) -> u32 {
    u32::from_be_bytes([b[at], b[at + 1], b[at + 2], b[at + 3]])
}

pub(crate) fn be_i32(b: &[u8], at: usize) -> i32 {
    be_u32(b, at) as i32
}

/// The fixed header of a file: enough for "152,000 words" without reading
/// the rest.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct Header {
    pub word_count: u32,
    pub node_count: u32,
    pub edge_count: u32,
}

impl Header {
    pub fn read(bytes: &[u8]) -> Result<Header, &'static str> {
        if bytes.len() < HEADER_BYTES {
            return Err("truncated dictionary header");
        }
        if be_u32(bytes, 0) != MAGIC {
            return Err("not a .wmdict file");
        }
        if be_u16(bytes, 4) != VERSION {
            return Err("unsupported dictionary version");
        }
        Ok(Header {
            word_count: be_u32(bytes, 8),
            node_count: be_u32(bytes, 12),
            edge_count: be_u32(bytes, 16),
        })
    }
}

/// A dictionary read in place. Every lookup reads the bytes it needs and
/// nothing else, so opening is a validation pass over the header and the
/// section bounds, and the words a user never types are never touched.
pub struct Dict<B: AsRef<[u8]> = Vec<u8>> {
    bytes: B,
    word_count: u32,
    node_count: usize,
    /// The edge-label alphabet, always 256 entries, or `None` when labels
    /// are plain u16 units.
    symbols: Option<Box<[u16; MAX_SYMBOLS]>>,
    child_start_off: usize,
    /// Running totals behind a counted `childStart`, or `None` when the file
    /// stores the plain i32 array.
    checkpoint_off: Option<usize>,
    edge_label_off: usize,
    freq_off: usize,
    max_subtree_off: usize,
    is_word_off: usize,
}

impl<B: AsRef<[u8]>> Dict<B> {
    /// `None` when `bytes` is not a version-3 file this reader can trust:
    /// wrong magic or version, a count that contradicts another, or a
    /// section the file does not hold in full.
    pub fn open(bytes: B) -> Option<Dict<B>> {
        let b = bytes.as_ref();
        if b.len() < HEADER_BYTES || be_u32(b, 0) != MAGIC || be_u16(b, 4) != VERSION {
            return None;
        }
        let flags = be_u16(b, 6);
        let word_count = be_u32(b, 8);
        let node_count = be_i32(b, 12);
        let edge_count = be_i32(b, 16);
        let symbol_count = be_i32(b, 20);
        if node_count <= 0 || edge_count != node_count - 1 {
            return None;
        }
        let node_count = node_count as usize;
        let edge_count = edge_count as usize;
        let labels_are_bytes = flags & FLAG_LABELS_U8 != 0;
        if symbol_count < 0 || symbol_count as usize > MAX_SYMBOLS {
            return None;
        }
        let symbol_count = symbol_count as usize;
        if !labels_are_bytes && symbol_count != 0 {
            return None;
        }
        let counted = flags & FLAG_DEGREE_U8 != 0;
        let mut offsets = [0usize; 7];
        for (i, offset) in offsets.iter_mut().enumerate() {
            let value = be_i32(b, 24 + i * 4);
            if value < HEADER_BYTES as i32 {
                return None;
            }
            *offset = value as usize;
        }
        let sizes = [
            pad4(symbol_count * 2),
            if counted {
                pad4(node_count)
            } else {
                (node_count + 1) * 4
            },
            if counted {
                ((node_count >> CHECKPOINT_SHIFT) + 1) * 4
            } else {
                0
            },
            pad4(if labels_are_bytes {
                edge_count
            } else {
                edge_count * 2
            }),
            pad4(node_count * 2),
            pad4(node_count * 2),
            pad4(node_count.div_ceil(8)),
        ];
        for i in 0..6 {
            if offsets[i + 1] < offsets[i] || offsets[i + 1] - offsets[i] < sizes[i] {
                return None;
            }
        }
        if offsets[6] + sizes[6] > b.len() {
            return None;
        }
        let symbols = labels_are_bytes.then(|| {
            let mut table = Box::new([UNUSED_SYMBOL; MAX_SYMBOLS]);
            for (i, slot) in table.iter_mut().enumerate().take(symbol_count) {
                *slot = be_u16(b, offsets[0] + i * 2);
            }
            table
        });
        Some(Dict {
            word_count,
            node_count,
            symbols,
            child_start_off: offsets[1],
            checkpoint_off: counted.then_some(offsets[2]),
            edge_label_off: offsets[3],
            freq_off: offsets[4],
            max_subtree_off: offsets[5],
            is_word_off: offsets[6],
            bytes,
        })
    }

    /// Distinct words, as the header states it.
    pub fn word_count(&self) -> u32 {
        self.word_count
    }

    pub fn node_count(&self) -> usize {
        self.node_count
    }

    /// The root node.
    pub const ROOT: u32 = 0;

    fn b(&self) -> &[u8] {
        self.bytes.as_ref()
    }

    /// Where `node`'s edges begin: in a counted file the nearest stored
    /// running total plus the counts of the nodes between, at most 32
    /// contiguous bytes.
    fn child_start(&self, node: usize) -> usize {
        let b = self.b();
        match self.checkpoint_off {
            None => be_u32(b, self.child_start_off + node * 4) as usize,
            Some(checkpoints) => {
                let mut total = be_u32(b, checkpoints + (node >> CHECKPOINT_SHIFT) * 4) as usize;
                let from = node & !(CHECKPOINT_STRIDE - 1);
                for counted in from..node {
                    total += b[self.child_start_off + counted] as usize;
                }
                total
            }
        }
    }

    fn child_count(&self, node: usize) -> usize {
        let b = self.b();
        match self.checkpoint_off {
            None => {
                (be_u32(b, self.child_start_off + (node + 1) * 4)
                    - be_u32(b, self.child_start_off + node * 4)) as usize
            }
            Some(_) => b[self.child_start_off + node] as usize,
        }
    }

    fn edge_label(&self, edge: usize) -> u16 {
        let b = self.b();
        match &self.symbols {
            None => be_u16(b, self.edge_label_off + edge * 2),
            Some(symbols) => symbols[b[self.edge_label_off + edge] as usize],
        }
    }

    /// The node edge `edge` leads to: breadth-first numbering makes it `edge + 1`.
    fn edge_child(edge: usize) -> u32 {
        edge as u32 + 1
    }

    fn freq(&self, node: u32) -> i32 {
        freq_codec::decode(be_u16(self.b(), self.freq_off + node as usize * 2))
    }

    /// The highest word frequency anywhere under `node`, itself included.
    pub fn max_subtree(&self, node: u32) -> i32 {
        freq_codec::decode(be_u16(self.b(), self.max_subtree_off + node as usize * 2))
    }

    pub fn is_word(&self, node: u32) -> bool {
        let node = node as usize;
        (self.b()[self.is_word_off + (node >> 3)] >> (node & 7)) & 1 == 1
    }

    /// `node`'s frequency when it ends a word, else 0.
    pub fn frequency(&self, node: u32) -> i32 {
        if self.is_word(node) {
            self.freq(node)
        } else {
            0
        }
    }

    /// The child of `node` along `label`, or `None`.
    pub fn child(&self, node: u32, label: u16) -> Option<u32> {
        self.child_edge(node as usize, label).map(Self::edge_child)
    }

    /// Every `(label, child)` of `node`, sorted by label.
    pub fn children(&self, node: u32) -> Vec<(u16, u32)> {
        let start = self.child_start(node as usize);
        let count = self.child_count(node as usize);
        (start..start + count)
            .map(|e| (self.edge_label(e), Self::edge_child(e)))
            .collect()
    }

    /// `children` into a caller-owned buffer, cleared first: the fuzzy walk
    /// reads a node's edges once per state and must not allocate for it.
    pub fn children_into(&self, node: u32, out: &mut Vec<(u16, u32)>) {
        out.clear();
        let start = self.child_start(node as usize);
        let count = self.child_count(node as usize);
        out.extend((start..start + count).map(|e| (self.edge_label(e), Self::edge_child(e))));
    }

    /// Edge index of `node`'s child labelled `unit`: binary search, the edges
    /// of a node being contiguous and sorted by label.
    fn child_edge(&self, node: usize, unit: u16) -> Option<usize> {
        let start = self.child_start(node);
        let count = self.child_count(node);
        let mut lo = start as isize;
        let mut hi = (start + count) as isize - 1;
        while lo <= hi {
            let mid = ((lo + hi) >> 1) as usize;
            match self.edge_label(mid).cmp(&unit) {
                Ordering::Less => lo = mid as isize + 1,
                Ordering::Greater => hi = mid as isize - 1,
                Ordering::Equal => return Some(mid),
            }
        }
        None
    }

    /// The node `word` reaches from the root, or `None`.
    pub fn node_for(&self, word: &str) -> Option<u32> {
        let mut node = Self::ROOT;
        for unit in word.encode_utf16() {
            node = self.child(node, unit)?;
        }
        Some(node)
    }

    /// [`Dict::node_for`] over UTF-16 units as Kotlin holds them, lone
    /// surrogates included.
    pub fn node_for_units(&self, word: &[u16]) -> Option<u32> {
        let mut node = Self::ROOT;
        for &unit in word {
            node = self.child(node, unit)?;
        }
        Some(node)
    }

    pub fn frequency_of(&self, word: &str) -> i32 {
        self.node_for(word).map_or(0, |node| self.frequency(node))
    }

    pub fn frequency_of_units(&self, word: &[u16]) -> i32 {
        self.node_for_units(word).map_or(0, |node| self.frequency(node))
    }

    pub fn contains(&self, word: &str) -> bool {
        self.frequency_of(word) > 0
    }

    /// Up to `limit` completions of `prefix`, best frequency first. Empty
    /// for an empty prefix: [`Dict::top`] is the whole-dictionary walk,
    /// asked for by name. Equal frequencies come out in the order the
    /// phone's `TrieCompleter` gives them (see [`Dict::complete_units`]).
    pub fn complete(&self, prefix: &str, limit: usize) -> Vec<Suggestion> {
        let units: Vec<u16> = prefix.encode_utf16().collect();
        Self::suggestions(self.complete_units(&units, limit))
    }

    /// The `limit` highest-frequency words in the whole dictionary.
    pub fn top(&self, limit: usize) -> Vec<Suggestion> {
        Self::suggestions(self.top_units(limit))
    }

    fn suggestions(found: Vec<(Vec<u16>, i32)>) -> Vec<Suggestion> {
        found
            .into_iter()
            .map(|(text, frequency)| Suggestion { word: String::from_utf16_lossy(&text), frequency })
            .collect()
    }

    /// [`Dict::complete`] in UTF-16, exactly as Kotlin's
    /// `TrieCompleter.complete` walks a `MappedTrie`: the same frontier, the
    /// same pushes in the same order, popped by [`JavaHeap`], so even words
    /// with equal frequencies come out in the same order.
    pub fn complete_units(&self, prefix: &[u16], limit: usize) -> Vec<(Vec<u16>, i32)> {
        if prefix.is_empty() || limit == 0 {
            return Vec::new();
        }
        match self.node_for_units(prefix) {
            None => Vec::new(),
            Some(node) => self.from(node, prefix.to_vec(), limit),
        }
    }

    pub fn top_units(&self, limit: usize) -> Vec<(Vec<u16>, i32)> {
        if limit == 0 {
            return Vec::new();
        }
        self.from(Self::ROOT, Vec::new(), limit)
    }

    /// Branch and bound from `node` ([`completer::from`]): a frontier
    /// ordered by the best frequency a subtree can still hold, so a word is
    /// emitted only once no unopened branch can outrank it.
    fn from(&self, node: u32, text: Vec<u16>, limit: usize) -> Vec<(Vec<u16>, i32)> {
        completer::from(self, node, text, limit, self.word_count as usize)
    }

    /// Every `(word, frequency)`, by depth-first walk: for building another
    /// index, never per keystroke.
    pub fn entries(&self) -> Vec<(String, i32)> {
        let mut out = Vec::with_capacity(self.word_count as usize);
        self.for_each_entry(|text, frequency| out.push((String::from_utf16_lossy(text), frequency)));
        out
    }

    /// Calls `f` with every word (UTF-16) and its frequency, in the order
    /// Kotlin's `MappedTrie.entries` lists them: depth first, children by
    /// label.
    pub fn for_each_entry(&self, mut f: impl FnMut(&[u16], i32)) {
        let mut text: Vec<u16> = Vec::new();
        let mut edge_stack = vec![self.child_start(0)];
        let mut end_stack = vec![edge_stack[0] + self.child_count(0)];
        while let Some(&end) = end_stack.last() {
            let depth = end_stack.len() - 1;
            let edge = edge_stack[depth];
            if edge < end {
                edge_stack[depth] = edge + 1;
                let child = Self::edge_child(edge);
                text.push(self.edge_label(edge));
                if self.is_word(child) {
                    f(&text, self.freq(child));
                }
                let start = self.child_start(child as usize);
                edge_stack.push(start);
                end_stack.push(start + self.child_count(child as usize));
            } else {
                end_stack.pop();
                edge_stack.pop();
                text.pop();
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::wm::wordlist;
    use std::collections::HashMap;
    use std::path::Path;

    const REPO: &str = concat!(env!("CARGO_MANIFEST_DIR"), "/../..");

    fn real_entries(name: &str) -> Vec<(String, i32)> {
        let path = format!("{REPO}/app/dictionaries-src/{name}.txt");
        wordlist::parse(&std::fs::read_to_string(&path).unwrap_or_else(|e| panic!("{path}: {e}")))
    }

    fn open(entries: &[(String, i32)]) -> Dict {
        Dict::open(write(&PackedTrie::of(entries)))
            .expect("the reader rejected the writer's output")
    }

    fn folded(entries: &[(String, i32)]) -> HashMap<String, i32> {
        let mut map = HashMap::new();
        for (w, f) in entries {
            if !w.is_empty() {
                let f = freq_codec::round(*f);
                map.entry(w.clone())
                    .and_modify(|held: &mut i32| *held = (*held).max(f))
                    .or_insert(f);
            }
        }
        map
    }

    fn canonical(mut list: Vec<Suggestion>) -> Vec<(String, i32)> {
        list.sort_by(|a, b| {
            b.frequency
                .cmp(&a.frequency)
                .then_with(|| a.word.cmp(&b.word))
        });
        list.into_iter().map(|s| (s.word, s.frequency)).collect()
    }

    /// What completion must return, by brute force.
    fn expected(folded: &HashMap<String, i32>, prefix: &str, limit: usize) -> Vec<(String, i32)> {
        let mut all: Vec<(String, i32)> = folded
            .iter()
            .filter(|(w, _)| w.starts_with(prefix))
            .map(|(w, f)| (w.clone(), *f))
            .collect();
        all.sort_by(|a, b| b.1.cmp(&a.1).then_with(|| a.0.cmp(&b.0)));
        all.truncate(limit);
        all
    }

    fn flags(bytes: &[u8]) -> u16 {
        be_u16(bytes, 6)
    }

    #[test]
    fn round_trip_preserves_every_query_on_the_real_english_list() {
        let entries = real_entries("en");
        let dict = open(&entries);
        let folded = folded(&entries);
        assert_eq!(folded.len(), dict.word_count() as usize);
        for (word, frequency) in &folded {
            assert_eq!(*frequency, dict.frequency_of(word), "frequencyOf({word:?})");
            assert!(dict.contains(word));
        }
        let mut prefixes: Vec<String> = ('a'..='z').map(|a| a.to_string()).collect();
        for a in 'a'..='z' {
            for b in 'a'..='z' {
                prefixes.push(format!("{a}{b}"));
            }
        }
        let full = entries.len() + 1;
        for p in &prefixes {
            assert_eq!(
                expected(&folded, p, full),
                canonical(dict.complete(p, full)),
                "prefix {p:?}"
            );
        }
        for p in ["a", "th", "co", "wor"] {
            let got: Vec<i32> = dict.complete(p, 5).iter().map(|s| s.frequency).collect();
            let want: Vec<i32> = expected(&folded, p, 5).iter().map(|e| e.1).collect();
            assert_eq!(want, got, "top-5 for {p:?}");
        }
        assert!(dict.complete("", 5).is_empty());
        assert_eq!(expected(&folded, "", 5), canonical(dict.top(5)));
        assert_eq!(
            folded,
            dict.entries().into_iter().collect::<HashMap<_, _>>()
        );
    }

    /// The Android build compiles the same two lists with the Kotlin codec;
    /// when its output is on disk, the Rust writer must match it byte for
    /// byte. Skipped, loudly, when the app has not been built here.
    #[test]
    fn writes_the_same_bytes_as_the_kotlin_codec() {
        let mut checked = 0;
        for name in ["en", "bn"] {
            let built = format!("{REPO}/app/build/generated/assets/compileBundledDictionaries/dictionaries/{name}.wmdict");
            let Ok(kotlin) = std::fs::read(&built) else {
                eprintln!("skipping {name}: no {built}");
                continue;
            };
            let rust = write(&PackedTrie::of(&real_entries(name)));
            assert_eq!(kotlin.len(), rust.len(), "{name}.wmdict length");
            assert!(
                kotlin == rust,
                "{name}.wmdict differs from the Kotlin codec's bytes"
            );
            checked += 1;
        }
        if checked == 0 && Path::new(&format!("{REPO}/app/build")).is_dir() {
            eprintln!("no compiled dictionaries under app/build; run the Android build to enable this check");
        }
    }

    #[test]
    fn header_reports_counts() {
        let bytes = write(&PackedTrie::of(&[("cat", 50), ("car", 90), ("care", 10)]));
        let header = Header::read(&bytes).unwrap();
        assert_eq!(3, header.word_count);
        assert_eq!(header.node_count - 1, header.edge_count);
        assert_eq!(Err("not a .wmdict file"), Header::read(&[0u8; 64]));
        assert_eq!(
            Err("truncated dictionary header"),
            Header::read(&bytes[..10])
        );
    }

    #[test]
    fn surrogate_pairs_and_non_latin_round_trip() {
        let entries: Vec<(String, i32)> = [("বাংলা", 7), ("কলম", 3), ("😀word", 2), ("কল", 9)]
            .iter()
            .map(|(w, f)| (w.to_string(), *f))
            .collect();
        let dict = open(&entries);
        for (w, f) in &entries {
            assert_eq!(*f, dict.frequency_of(w));
        }
        assert_eq!(
            vec![("কল".to_string(), 9), ("কলম".to_string(), 3)],
            canonical(dict.complete("ক", 10))
        );
        assert_eq!(
            vec![("😀word".to_string(), 2)],
            canonical(dict.complete("😀", 10))
        );
        assert_eq!(folded(&entries), dict.entries().into_iter().collect());
    }

    #[test]
    fn a_node_with_more_children_than_a_byte_can_count_still_round_trips() {
        let entries: Vec<(String, i32)> = (0..300)
            .map(|i| {
                (
                    format!("a{}", char::from_u32(0x4E00 + i).unwrap()),
                    i as i32 + 1,
                )
            })
            .collect();
        let bytes = write(&PackedTrie::of(&entries));
        assert_eq!(
            0,
            flags(&bytes) & FLAG_DEGREE_U8,
            "expected the i32 childStart fallback"
        );
        let dict = Dict::open(bytes).unwrap();
        assert_eq!(300, dict.word_count());
        for (w, f) in &entries {
            assert_eq!(*f, dict.frequency_of(w));
        }
        assert_eq!(
            expected(&folded(&entries), "a", 300),
            canonical(dict.complete("a", 300))
        );
        assert_eq!(folded(&entries), dict.entries().into_iter().collect());
    }

    #[test]
    fn more_than_256_distinct_labels_fall_back_to_u16_labels() {
        let entries: Vec<(String, i32)> = (0..300)
            .map(|i| {
                (
                    char::from_u32(0x4E00 + i).unwrap().to_string(),
                    i as i32 + 1,
                )
            })
            .collect();
        let bytes = write(&PackedTrie::of(&entries));
        assert_eq!(0, flags(&bytes) & FLAG_LABELS_U8);
        let dict = Dict::open(bytes).unwrap();
        for (w, f) in &entries {
            assert_eq!(*f, dict.frequency_of(w));
        }
        assert_eq!(folded(&entries), dict.entries().into_iter().collect());
    }

    #[test]
    fn tries_straddling_a_checkpoint_boundary_round_trip() {
        let stride = CHECKPOINT_STRIDE;
        for count in [stride - 1, stride, stride + 1, stride * 2, stride * 3 + 7] {
            let entries: Vec<(String, i32)> = (0..count)
                .map(|i| (format!("w{i:04}"), i as i32 + 1))
                .collect();
            let bytes = write(&PackedTrie::of(&entries));
            assert_eq!(
                FLAG_DEGREE_U8,
                flags(&bytes) & FLAG_DEGREE_U8,
                "{count} words"
            );
            let dict = Dict::open(bytes).unwrap();
            for (w, f) in &entries {
                assert_eq!(*f, dict.frequency_of(w), "{count} words: {w}");
            }
            assert_eq!(
                folded(&entries),
                dict.entries().into_iter().collect(),
                "{count} words: walk"
            );
            assert_eq!(
                expected(&folded(&entries), "w", count),
                canonical(dict.complete("w", count))
            );
        }
    }

    #[test]
    fn duplicates_keep_the_higher_frequency_and_empties_are_dropped() {
        let dict = open(&[
            ("a".to_string(), 3),
            ("".to_string(), 9),
            ("a".to_string(), 8),
            ("ab".to_string(), 1),
        ]);
        assert_eq!(2, dict.word_count());
        assert_eq!(8, dict.frequency_of("a"));
        assert_eq!(0, dict.frequency_of(""));
        assert_eq!(
            vec![("a".to_string(), 8), ("ab".to_string(), 1)],
            canonical(dict.complete("a", 9))
        );
        assert!(dict.complete("b", 9).is_empty());
        assert_eq!(Some(0), dict.node_for(""));
    }

    #[test]
    fn corrupt_files_are_rejected_not_crashed() {
        assert!(Dict::open((0..64u8).collect::<Vec<u8>>()).is_none());
        let bytes = write(&PackedTrie::of(&[("cat", 50), ("car", 90)]));
        assert!(Dict::open(bytes[..bytes.len() / 2].to_vec()).is_none());
        assert!(Dict::open(Vec::new()).is_none());
        let mut bad_version = bytes.clone();
        bad_version[5] = 9;
        assert!(Dict::open(bad_version).is_none());
        let mut bad_edges = bytes.clone();
        bad_edges[19] ^= 1;
        assert!(Dict::open(bad_edges).is_none());
        let mut bad_offset = bytes.clone();
        bad_offset[24 + 6 * 4 + 3] = 0xFF;
        assert!(Dict::open(bad_offset).is_none());
        assert!(Dict::open(bytes).is_some());
    }
}
