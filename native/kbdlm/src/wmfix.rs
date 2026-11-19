//! Pulse's use of WM Keyboard's engine: a packed trie made from Pulse's own word list (word count lines), the QWERTY proximity of the
//! language's rows, and the fuzzy search for the best corrections of a typed word.
use crate::wm::fuzzy::{self, RankScale, Tier, WalkSource, Workspace, NO_HABITS};
use crate::wm::proximity::KeyProximity;
use crate::wm::wmdict::{self, Dict, PackedTrie};

pub struct Fixer {
    dict: Dict<Vec<u8>>,
    prox: KeyProximity,
    ws: Workspace,
}

impl Fixer {
    /// [list]: "word count" lines (Pulse's assets/kbd/<lang>.txt); [rows]: the letter rows of the layout, top to bottom.
    pub fn new(list: &str, rows: &[&str]) -> Option<Fixer> {
        let mut entries: Vec<(String, i32)> = Vec::new();
        for line in list.lines() {
            let mut it = line.split_whitespace();
            let (Some(w), Some(c)) = (it.next(), it.next()) else { continue };
            let c: i64 = c.parse().unwrap_or(1);
            // the trie keeps counts as i32: very common words are capped, their order stays
            entries.push((w.to_lowercase(), c.clamp(1, i32::MAX as i64 / 4) as i32));
        }
        if entries.is_empty() { return None; }
        let trie = PackedTrie::of(&entries);
        let dict = Dict::open(wmdict::write(&trie))?;
        Some(Fixer { dict, prox: KeyProximity::of_rows(rows), ws: Workspace::new() })
    }

    /// Up to [limit] corrections or completions of [typed], best first: (word, score, edits).
    pub fn search(&mut self, typed: &str, limit: usize) -> Vec<(String, f64, u32)> {
        let src = [WalkSource { walker: &self.dict, log_weight: 0.0, tier: Tier::Dictionary, scale: RankScale::RAW }];
        fuzzy::search(&src, &typed.to_lowercase(), &self.prox, limit, &mut self.ws, None, &NO_HABITS)
            .into_iter()
            .take(limit)
            .map(|c| (c.word, c.score, c.edits))
            .collect()
    }

    pub fn contains(&self, w: &str) -> bool { self.dict.contains(&w.to_lowercase()) }
}
