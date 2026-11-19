//! WM Keyboard's correction engine (MIT, see each file): the trie-guided fuzzy search, the key proximity and the packed trie.
#![allow(dead_code, clippy::all)]
pub mod accents;
pub mod completer;
pub mod freq_codec;
pub mod fuzzy;
pub mod java_heap;
pub mod proximity;
pub mod sort;
pub mod strict_math;
pub mod wmdict;
pub mod wordlist;
