// Taken from WM Keyboard's wm-engine (https://github.com/wasi-master/wmkeyboard, native/wm-engine/src/wordlist.rs), MIT License, Copyright (c) 2026 Wasi Master.
//! Word lists as text: the app's own `word<space>frequency` lines, and the
//! AOSP `.combined` list every wordlist published for AOSP-derived keyboards
//! is written in (HeliBoard's repository, the lists FUTO links, the sources
//! the `.dict` compiler takes).
//!
//! The two are told apart on the first line that is not blank or a comment.
//! A combined list read as the plain format would not be rejected but
//! accepted wrongly: its record lines hold no space, so the whole of
//! `word=the,f=222,flags=` became one word at frequency 1 and an import
//! quietly filled a user's dictionary with junk. The sniff is what stops it.

/// `(word, frequency)` for every entry of `text`, in file order; duplicates
/// are kept, the trie builder folds them.
pub fn parse(text: &str) -> Vec<(String, i32)> {
    let mut entries = Vec::new();
    let mut combined: Option<bool> = None;
    for line in text.lines() {
        let trimmed = line.trim();
        if trimmed.is_empty() || trimmed.starts_with('#') {
            continue;
        }
        let is_combined = *combined.get_or_insert_with(|| trimmed.starts_with(COMBINED_HEADER));
        if is_combined {
            if let Some(entry) = combined_entry(trimmed) {
                entries.push(entry);
            }
        } else {
            entries.push(plain_entry(trimmed));
        }
    }
    entries
}

fn plain_entry(line: &str) -> (String, i32) {
    match line.rfind(' ') {
        Some(separator) if separator > 0 => {
            let word = line[..separator].trim().to_string();
            let frequency = line[separator + 1..].parse().unwrap_or(1);
            (word, frequency)
        }
        _ => (line.to_string(), 1),
    }
}

/// One `word=` record, or `None` for everything else in a combined file: a
/// `bigram=` or `shortcut=` line describes a word already read, and a
/// shortcut's target is a word nobody typed.
fn combined_entry(line: &str) -> Option<(String, i32)> {
    if !line.starts_with(WORD_FIELD) {
        return None;
    }
    let mut word: Option<&str> = None;
    let mut frequency = DEFAULT_COMBINED_FREQUENCY;
    for field in line.split(',') {
        let (name, value) = field.split_once('=').unwrap_or((field, ""));
        match name {
            "word" => word = Some(value),
            // `f` is 0..255 there, and `whitelist` where a shortcut stands in
            // for a number; a non-numeric value keeps the default.
            "f" => {
                if let Ok(f) = value.parse::<i32>() {
                    frequency = scale_aosp_frequency(f);
                }
            }
            _ => {}
        }
    }
    word.filter(|w| !w.is_empty())
        .map(|w| (w.to_string(), frequency))
}

/// An AOSP frequency (0..255) on this app's scale (bundled lists run to
/// 10000), so an imported word keeps its rank among the bundled ones.
pub fn scale_aosp_frequency(f: i32) -> i32 {
    (f.clamp(0, MAX_COMBINED_FREQUENCY) * MAX_LOCAL_FREQUENCY / MAX_COMBINED_FREQUENCY).max(1)
}

const COMBINED_HEADER: &str = "dictionary=";
const WORD_FIELD: &str = "word=";
const MAX_COMBINED_FREQUENCY: i32 = 255;
const MAX_LOCAL_FREQUENCY: i32 = 10000;
const DEFAULT_COMBINED_FREQUENCY: i32 = 1;

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn plain_lines() {
        let entries = parse("# comment\nthe 10000\n\n of  40 \nalone\nbad freq\ntwo words 7\n");
        assert_eq!(
            vec![
                ("the".to_string(), 10000),
                ("of".to_string(), 40),
                ("alone".to_string(), 1),
                ("bad".to_string(), 1),
                ("two words".to_string(), 7),
            ],
            entries
        );
    }

    #[test]
    fn combined_lists_are_sniffed_on_their_header() {
        let text = "dictionary=main:en,locale=en,description=English,date=1414726273,version=54\n\
                    \x20word=the,f=222,flags=,originalFreq=222\n\
                    \x20 bigram=is,f=8\n\
                    \x20word=pls,f=whitelist\n\
                    \x20 shortcut=please,f=whitelist\n\
                    \x20word=,f=3\n";
        assert_eq!(
            vec![
                ("the".to_string(), scale_aosp_frequency(222)),
                ("pls".to_string(), 1)
            ],
            parse(text)
        );
        assert_eq!(10000, scale_aosp_frequency(255));
        assert_eq!(1, scale_aosp_frequency(0));
        assert_eq!(8705, scale_aosp_frequency(222));
    }

    #[test]
    fn a_word_line_half_way_down_a_plain_list_is_a_word() {
        assert_eq!(
            vec![("a".to_string(), 5), ("word=b".to_string(), 1)],
            parse("a 5\nword=b\n")
        );
    }
}
