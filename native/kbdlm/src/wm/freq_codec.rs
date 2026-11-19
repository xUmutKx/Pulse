// Taken from WM Keyboard's wm-engine (https://github.com/wasi-master/wmkeyboard, native/wm-engine/src/freq_codec.rs), MIT License, Copyright (c) 2026 Wasi Master.
//! The 16-bit minifloat `.wmdict` and `.wmng` store frequencies and counts
//! in: a 5-bit exponent and an 11-bit mantissa with an implicit leading bit.
//!
//! Exact below 2048, which covers every absolute threshold the engine tests
//! against and most of a bundled word list; above that the relative error is
//! at most 1/2048. Monotone in the raw bit pattern, so quantising can never
//! reverse two frequencies, only tie ones within 0.05% of each other, and
//! `round(max(fs)) == max(round(f))`: a subtree maximum stays exact rather
//! than becoming a loose bound, which is what the branch-and-bound completion
//! relies on.

const MANTISSA_BITS: u32 = 11;
const MANTISSA_MASK: u32 = (1 << MANTISSA_BITS) - 1;
const IMPLICIT_BIT: u32 = 1 << MANTISSA_BITS;

/// Largest code [`encode`] can produce: `i32::MAX` lands here.
pub const MAX_CODE: u16 = 0xA7FF;

/// `value` as a 16-bit code. Values at or below the mantissa mask are stored verbatim.
pub fn encode(value: i32) -> u16 {
    if value <= MANTISSA_MASK as i32 {
        return if value <= 0 { 0 } else { value as u16 };
    }
    let v = value as u32;
    let exponent = 32 - v.leading_zeros() - MANTISSA_BITS;
    ((exponent << MANTISSA_BITS) | ((v >> (exponent - 1)) & MANTISSA_MASK)) as u16
}

/// The frequency a 16-bit `code` stands for. A code no writer produces (an
/// exponent past 20, from a corrupt file) saturates at `i32::MAX`.
pub fn decode(code: u16) -> i32 {
    let code = code as u32;
    let exponent = code >> MANTISSA_BITS;
    if exponent == 0 {
        return code as i32;
    }
    let value = ((IMPLICIT_BIT | (code & MANTISSA_MASK)) as u64) << (exponent - 1);
    if value > i32::MAX as u64 {
        i32::MAX
    } else {
        value as i32
    }
}

/// `value` snapped to the nearest representable frequency.
pub fn round(value: i32) -> i32 {
    decode(encode(value))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn exact_below_2048_and_monotone_above() {
        for v in 0..2048 {
            assert_eq!(v, round(v));
            assert_eq!(v as u16, encode(v));
        }
        assert_eq!(0, encode(-5));
        let mut previous = 0u16;
        let mut v = 1i32;
        while v > 0 && v < i32::MAX / 2 {
            let code = encode(v);
            assert!(code >= previous, "{v}");
            previous = code;
            let back = decode(code);
            let error = (back - v).abs() as f64 / v as f64;
            assert!(error <= 1.0 / 2048.0, "{v} -> {back}");
            v = v.saturating_mul(3) / 2 + 1;
        }
        assert_eq!(MAX_CODE, encode(i32::MAX));
        assert_eq!(i32::MAX, decode(0xFFFF));
    }

    #[test]
    fn the_values_the_kotlin_tests_pin() {
        assert_eq!(204288, round(204351));
        assert_eq!(16816, round(16821));
        assert_eq!(50000, round(50000));
        assert_eq!(900, round(900));
    }
}
