// Taken from WM Keyboard's wm-engine (https://github.com/wasi-master/wmkeyboard, native/wm-engine/src/strict_math.rs), MIT License, Copyright (c) 2026 Wasi Master.
//! Java's `StrictMath`, bit for bit: fdlibm, the library every Java runtime
//! (HotSpot and Android's ART alike) must reproduce exactly for `StrictMath`.
//!
//! The platform `ln` is not one function: Apple's libm, bionic and HotSpot's
//! `Math.log` intrinsic each round about one result in a hundred differently
//! in the last bit. A score that differs by one ulp can reorder two
//! candidates, so the ported prediction code, and the Kotlin code it is
//! checked against, both use these: Kotlin through `StrictMath`, Rust
//! through this module. Then the phone, the JVM evals and WMK Desktop agree
//! everywhere.
//!
//! Transcribed from fdlibm 5.3 (`e_log.c`, `e_pow.c`, `s_scalbn.c`). Rust
//! never contracts `a * b + c`
//! into a fused multiply-add on its own, which fdlibm's rounding relies on.

const LN2_HI: f64 = 6.93147180369123816490e-01; // 3fe62e42 fee00000
const LN2_LO: f64 = 1.90821492927058770002e-10; // 3dea39ef 35793c76
const TWO54: f64 = 1.80143985094819840000e+16; // 43500000 00000000
const LG1: f64 = 6.666666666666735130e-01; // 3FE55555 55555593
const LG2: f64 = 3.999999999940941908e-01; // 3FD99999 9997FA04
const LG3: f64 = 2.857142874366239149e-01; // 3FD24924 94229359
const LG4: f64 = 2.222219843214978396e-01; // 3FCC71C5 1D8E78AF
const LG5: f64 = 1.818357216161805012e-01; // 3FC74664 96CB03DE
const LG6: f64 = 1.531383769920937332e-01; // 3FC39A09 D078C69F
const LG7: f64 = 1.479819860511658591e-01; // 3FC2F112 DF3E5244

fn hi(x: f64) -> i32 {
    (x.to_bits() >> 32) as i32
}

fn lo(x: f64) -> u32 {
    x.to_bits() as u32
}

fn with_hi(x: f64, hi: i32) -> f64 {
    f64::from_bits(((hi as u32 as u64) << 32) | (x.to_bits() & 0xFFFF_FFFF))
}

/// `StrictMath.log(x)`.
pub fn log(x: f64) -> f64 {
    let mut x = x;
    let mut hx = hi(x);
    let lx = lo(x);
    let mut k: i32 = 0;
    if hx < 0x0010_0000 {
        // x < 2^-1022
        if ((hx & 0x7fff_ffff) as u32 | lx) == 0 {
            return f64::NEG_INFINITY; // log(+-0) = -inf
        }
        if hx < 0 {
            return f64::NAN; // log(-#) = NaN
        }
        k -= 54;
        x *= TWO54; // subnormal: scale up
        hx = hi(x);
    }
    if hx >= 0x7ff0_0000 {
        return x + x;
    }
    k += (hx >> 20) - 1023;
    hx &= 0x000f_ffff;
    let i = (hx + 0x95f64) & 0x10_0000;
    x = with_hi(x, hx | (i ^ 0x3ff0_0000)); // normalise x or x/2
    k += i >> 20;
    let f = x - 1.0;
    if (0x000f_ffff & (2 + hx)) < 3 {
        // |f| < 2^-20
        if f == 0.0 {
            if k == 0 {
                return 0.0;
            }
            let dk = k as f64;
            return dk * LN2_HI + dk * LN2_LO;
        }
        let r = f * f * (0.5 - 0.33333333333333333 * f);
        if k == 0 {
            return f - r;
        }
        let dk = k as f64;
        return dk * LN2_HI - ((r - dk * LN2_LO) - f);
    }
    let s = f / (2.0 + f);
    let dk = k as f64;
    let z = s * s;
    let mut i = hx - 0x6147a;
    let w = z * z;
    let j = 0x6b851 - hx;
    let t1 = w * (LG2 + w * (LG4 + w * LG6));
    let t2 = z * (LG1 + w * (LG3 + w * (LG5 + w * LG7)));
    i |= j;
    let r = t2 + t1;
    if i > 0 {
        let hfsq = 0.5 * f * f;
        if k == 0 {
            f - (hfsq - s * (hfsq + r))
        } else {
            dk * LN2_HI - ((hfsq - (s * (hfsq + r) + dk * LN2_LO)) - f)
        }
    } else if k == 0 {
        f - s * (f - r)
    } else {
        dk * LN2_HI - ((s * (f - r) - dk * LN2_LO) - f)
    }
}

/// `StrictMath.log(1.0 + v)` for a count: what the Kotlin walks call
/// `ln1p(v)` (it is `ln(1.0 + v)`, not `log1p`).
pub fn ln1p_count(v: i32) -> f64 {
    log(1.0 + v as f64)
}

// e_pow.c
const BP: [f64; 2] = [1.0, 1.5];
const DP_H: [f64; 2] = [0.0, 5.84962487220764160156e-01]; // 0x3FE2B803 0x40000000
const DP_L: [f64; 2] = [0.0, 1.35003920212974897128e-08]; // 0x3E4CFDEB 0x43CFD006
const TWO53: f64 = 9007199254740992.0; // 0x43400000 0x00000000
const HUGE: f64 = 1.0e300;
const TINY: f64 = 1.0e-300;
// poly coefs for (3/2)*(log(x)-2s-2/3*s**3
const L1: f64 = 5.99999999999994648725e-01; // 0x3FE33333 0x33333303
const L2: f64 = 4.28571428578550184252e-01; // 0x3FDB6DB6 0xDB6FABFF
const L3: f64 = 3.33333329818377432918e-01; // 0x3FD55555 0x518F264D
const L4: f64 = 2.72728123808534006489e-01; // 0x3FD17460 0xA91D4101
const L5: f64 = 2.30660745775561754067e-01; // 0x3FCD864A 0x93C9DB65
const L6: f64 = 2.06975017800338417784e-01; // 0x3FCA7E28 0x4A454EEF
const P1: f64 = 1.66666666666666019037e-01; // 0x3FC55555 0x5555553E
const P2: f64 = -2.77777777770155933842e-03; // 0xBF66C16C 0x16BEBD93
const P3: f64 = 6.61375632143793436117e-05; // 0x3F11566A 0xAF25DE2C
const P4: f64 = -1.65339022054652515390e-06; // 0xBEBBBD41 0xC5D26BF1
const P5: f64 = 4.13813679705723846039e-08; // 0x3E663769 0x72BEA4D0
const POW_LG2: f64 = 6.93147180559945286227e-01; // 0x3FE62E42 0xFEFA39EF
const LG2_H: f64 = 6.93147182464599609375e-01; // 0x3FE62E43 0x00000000
const LG2_L: f64 = -1.90465429995776804525e-09; // 0xBE205C61 0x0CA86C39
const OVT: f64 = 8.0085662595372944372e-17; // -(1024-log2(ovfl+.5ulp))
const CP: f64 = 9.61796693925975554329e-01; // 0x3FEEC709 0xDC3A03FD =2/(3ln2)
const CP_H: f64 = 9.61796700954437255859e-01; // 0x3FEEC709 0xE0000000 =(float)cp
const CP_L: f64 = -7.02846165095275826516e-09; // 0xBE3E2FE0 0x145B01F5 =tail of cp_h
const IVLN2: f64 = 1.44269504088896338700e+00; // 0x3FF71547 0x652B82FE =1/ln2
const IVLN2_H: f64 = 1.44269502162933349609e+00; // 0x3FF71547 0x60000000 =24b 1/ln2
const IVLN2_L: f64 = 1.92596299112661746887e-08; // 0x3E54AE0B 0xF85DDF44 =1/ln2 tail

fn with_lo(x: f64, lo: u32) -> f64 {
    f64::from_bits((x.to_bits() & 0xFFFF_FFFF_0000_0000) | lo as u64)
}

/// `StrictMath.pow(x, y)`.
#[allow(clippy::many_single_char_names)]
pub fn pow(x: f64, y: f64) -> f64 {
    let hx = hi(x);
    let lx = lo(x);
    let hy = hi(y);
    let ly = lo(y);
    let mut ix = hx & 0x7fff_ffff;
    let iy = hy & 0x7fff_ffff;

    // y == zero: x**0 = 1
    if (iy as u32 | ly) == 0 {
        return 1.0;
    }
    // +-NaN return x+y
    if ix > 0x7ff0_0000 || (ix == 0x7ff0_0000 && lx != 0) || iy > 0x7ff0_0000 || (iy == 0x7ff0_0000 && ly != 0) {
        return x + y;
    }

    // determine if y is an odd int when x < 0
    // yisint = 0 ... y is not an integer
    // yisint = 1 ... y is an odd int
    // yisint = 2 ... y is an even int
    let mut yisint = 0;
    if hx < 0 {
        if iy >= 0x4340_0000 {
            yisint = 2; // even integer y
        } else if iy >= 0x3ff0_0000 {
            let k = (iy >> 20) - 0x3ff; // exponent
            if k > 20 {
                let j = ly >> (52 - k);
                if (j << (52 - k)) == ly {
                    yisint = 2 - (j & 1) as i32;
                }
            } else if ly == 0 {
                let j = iy >> (20 - k);
                if (j << (20 - k)) == iy {
                    yisint = 2 - (j & 1);
                }
            }
        }
    }

    // special value of y
    if ly == 0 {
        if iy == 0x7ff0_0000 {
            // y is +-inf
            if ((ix - 0x3ff0_0000) as u32 | lx) == 0 {
                return y - y; // inf**+-1 is NaN
            } else if ix >= 0x3ff0_0000 {
                // (|x|>1)**+-inf = inf,0
                return if hy >= 0 { y } else { 0.0 };
            } else {
                // (|x|<1)**-,+inf = inf,0
                return if hy < 0 { -y } else { 0.0 };
            }
        }
        if iy == 0x3ff0_0000 {
            // y is +-1
            return if hy < 0 { 1.0 / x } else { x };
        }
        if hy == 0x4000_0000 {
            return x * x; // y is 2
        }
        if hy == 0x3fe0_0000 && hx >= 0 {
            // y is 0.5, x >= +0
            return x.sqrt();
        }
    }

    let mut ax = x.abs();
    // special value of x
    if lx == 0 && (ix == 0x7ff0_0000 || ix == 0 || ix == 0x3ff0_0000) {
        let mut z = ax; // x is +-0,+-inf,+-1
        if hy < 0 {
            z = 1.0 / z; // z = (1/|x|)
        }
        if hx < 0 {
            if ((ix - 0x3ff0_0000) | yisint) == 0 {
                z = (z - z) / (z - z); // (-1)**non-int is NaN
            } else if yisint == 1 {
                z = -z; // (x<0)**odd = -(|x|**odd)
            }
        }
        return z;
    }

    let mut n = ((hx as u32) >> 31) as i32 - 1;

    // (x<0)**(non-int) is NaN
    if (n | yisint) == 0 {
        return (x - x) / (x - x);
    }

    let mut s = 1.0; // s (sign of result -ve**odd) = -1 else = 1
    if (n | (yisint - 1)) == 0 {
        s = -1.0; // (-ve)**(odd int)
    }

    let (t1, t2);
    // |y| is huge
    if iy > 0x41e0_0000 {
        // if |y| > 2**31
        if iy > 0x43f0_0000 {
            // if |y| > 2**64, must o/uflow
            if ix <= 0x3fef_ffff {
                return if hy < 0 { HUGE * HUGE } else { TINY * TINY };
            }
            if ix >= 0x3ff0_0000 {
                return if hy > 0 { HUGE * HUGE } else { TINY * TINY };
            }
        }
        // over/underflow if x is not close to one
        if ix < 0x3fef_ffff {
            return if hy < 0 { s * HUGE * HUGE } else { s * TINY * TINY };
        }
        if ix > 0x3ff0_0000 {
            return if hy > 0 { s * HUGE * HUGE } else { s * TINY * TINY };
        }
        // now |1-x| is tiny <= 2**-20, suffice to compute
        // log(x) by x-x^2/2+x^3/3-x^4/4
        let t = ax - 1.0; // t has 20 trailing zeros
        let w = (t * t) * (0.5 - t * (0.3333333333333333333333 - t * 0.25));
        let u = IVLN2_H * t; // ivln2_h has 21 sig. bits
        let v = t * IVLN2_L - w * IVLN2;
        let tt1 = with_lo(u + v, 0);
        t1 = tt1;
        t2 = v - (tt1 - u);
    } else {
        let mut nn = 0i32;
        // take care subnormal number
        if ix < 0x0010_0000 {
            ax *= TWO53;
            nn -= 53;
            ix = hi(ax);
        }
        nn += (ix >> 20) - 0x3ff;
        let j = ix & 0x000f_ffff;
        // determine interval
        ix = j | 0x3ff0_0000; // normalize ix
        let k;
        if j <= 0x3988E {
            k = 0; // |x|<sqrt(3/2)
        } else if j < 0xBB67A {
            k = 1; // |x|<sqrt(3)
        } else {
            k = 0;
            nn += 1;
            ix -= 0x0010_0000;
        }
        n = nn;
        ax = with_hi(ax, ix);

        // compute ss = s_h+s_l = (x-1)/(x+1) or (x-1.5)/(x+1.5)
        let u = ax - BP[k]; // bp[0]=1.0, bp[1]=1.5
        let v = 1.0 / (ax + BP[k]);
        let ss = u * v;
        let s_h = with_lo(ss, 0);
        // t_h=ax+bp[k] High
        let t_h = f64::from_bits(((((ix >> 1) | 0x2000_0000) + 0x0008_0000 + ((k as i32) << 18)) as u32 as u64) << 32);
        let t_l = ax - (t_h - BP[k]);
        let s_l = v * ((u - s_h * t_h) - s_h * t_l);
        // compute log(ax)
        let s2 = ss * ss;
        let mut r = s2 * s2 * (L1 + s2 * (L2 + s2 * (L3 + s2 * (L4 + s2 * (L5 + s2 * L6)))));
        r += s_l * (s_h + ss);
        let s2 = s_h * s_h;
        let t_h = with_lo(3.0 + s2 + r, 0);
        let t_l = r - ((t_h - 3.0) - s2);
        // u+v = ss*(1+...)
        let u = s_h * t_h;
        let v = s_l * t_h + t_l * ss;
        // 2/(3log2)*(ss+...)
        let p_h = with_lo(u + v, 0);
        let p_l = v - (p_h - u);
        let z_h = CP_H * p_h; // cp_h+cp_l = 2/(3*log2)
        let z_l = CP_L * p_h + p_l * CP + DP_L[k];
        // log2(ax) = (ss+..)*2/(3*log2) = n + dp_h + z_h + z_l
        let t = n as f64;
        let tt1 = with_lo(((z_h + z_l) + DP_H[k]) + t, 0);
        t1 = tt1;
        t2 = z_l - (((tt1 - t) - DP_H[k]) - z_h);
    }

    // split up y into y1+y2 and compute (y1+y2)*(t1+t2)
    let y1 = with_lo(y, 0);
    let p_l = (y - y1) * t1 + y * t2;
    let mut p_h = y1 * t1;
    let mut z = p_l + p_h;
    let j = hi(z);
    let i = lo(z) as i32;
    if j >= 0x4090_0000 {
        // z >= 1024
        if ((j - 0x4090_0000) | i) != 0 {
            return s * HUGE * HUGE; // overflow
        } else if p_l + OVT > z - p_h {
            return s * HUGE * HUGE; // overflow
        }
    } else if (j & 0x7fff_ffff) >= 0x4090_cc00 {
        // z <= -1075
        if ((j as u32).wrapping_sub(0xc090_cc00) as i32 | i) != 0 {
            return s * TINY * TINY; // underflow
        } else if p_l <= z - p_h {
            return s * TINY * TINY; // underflow
        }
    }
    // compute 2**(p_h+p_l)
    let i = j & 0x7fff_ffff;
    let mut k = (i >> 20) - 0x3ff;
    let mut nn = 0;
    if i > 0x3fe0_0000 {
        // if |z| > 0.5, set n = [z+0.5]
        nn = j + (0x0010_0000 >> (k + 1));
        k = ((nn & 0x7fff_ffff) >> 20) - 0x3ff; // new k for n
        let t = f64::from_bits(((nn & !(0x000f_ffff >> k)) as u32 as u64) << 32);
        nn = ((nn & 0x000f_ffff) | 0x0010_0000) >> (20 - k);
        if j < 0 {
            nn = -nn;
        }
        p_h -= t;
    }
    let t = with_lo(p_l + p_h, 0);
    let u = t * LG2_H;
    let v = (p_l - (t - p_h)) * POW_LG2 + t * LG2_L;
    z = u + v;
    let w = v - (z - u);
    let t = z * z;
    let t1 = z - t * (P1 + t * (P2 + t * (P3 + t * (P4 + t * P5))));
    let r = (z * t1) / (t1 - 2.0) - (w + z * w);
    z = 1.0 - (r - z);
    let mut j = hi(z);
    j += nn << 20;
    if (j >> 20) <= 0 {
        z = scalbn(z, nn); // subnormal output
    } else {
        z = with_hi(z, j);
    }
    s * z
}

/// fdlibm's `scalbn`: `x * 2^n` by exponent arithmetic.
fn scalbn(x: f64, n: i32) -> f64 {
    const TWO54: f64 = 1.80143985094819840000e+16;
    const TWOM54: f64 = 5.55111512312578270212e-17;
    let mut x = x;
    let mut hx = hi(x);
    let lx = lo(x);
    let mut k = (hx & 0x7ff0_0000) >> 20; // extract exponent
    if k == 0 {
        // 0 or subnormal x
        if (lx | (hx & 0x7fff_ffff) as u32) == 0 {
            return x; // +-0
        }
        x *= TWO54;
        hx = hi(x);
        k = ((hx & 0x7ff0_0000) >> 20) - 54;
        if n < -50000 {
            return TINY * x; // underflow
        }
    }
    if k == 0x7ff {
        return x + x; // NaN or Inf
    }
    k += n;
    if k > 0x7fe {
        return HUGE * HUGE.copysign(x); // overflow
    }
    if k > 0 {
        // normal result
        return with_hi(x, ((hx as u32 & 0x800f_ffff) | ((k as u32) << 20)) as i32);
    }
    if k <= -54 {
        if n > 50000 {
            // in case integer overflow in n+k
            return HUGE * HUGE.copysign(x); // overflow
        } else {
            return TINY * TINY.copysign(x); // underflow
        }
    }
    k += 54; // subnormal result
    x = with_hi(x, ((hx as u32 & 0x800f_ffff) | ((k as u32) << 20)) as i32);
    x * TWOM54
}

#[cfg(test)]
mod tests {
    use super::*;

    /// The same hash the generator in the comment below prints, over the
    /// same inputs: every count below two million, a sweep of fractions, and
    /// a million raw bit patterns from a fixed generator.
    ///
    /// ```java
    /// // javac S.java && java S
    /// public class S {
    ///   static long h = 0;
    ///   // NaN payloads differ by CPU even in Java; any NaN counts as one.
    ///   static void mix(double r) { if (r != r) r = Double.NaN; h = h * 1000003 ^ Double.doubleToRawLongBits(r); }
    ///   public static void main(String[] a) {
    ///     for (int v = 0; v < 2_000_000; v++) mix(StrictMath.log(1.0 + v));
    ///     for (int v = 1; v < 400_000; v++) mix(StrictMath.log(v * 0.00025));
    ///     long s = 0x9E3779B97F4A7C15L;
    ///     for (int i = 0; i < 1_000_000; i++) { s ^= s << 13; s ^= s >>> 7; s ^= s << 17; mix(StrictMath.log(Double.longBitsToDouble(s))); }
    ///     System.out.println(Long.toHexString(h)); } }
    /// ```
    #[test]
    fn log_is_strict_math_bit_for_bit() {
        let mut h: i64 = 0;
        let mut mix = |v: f64| {
            let v = if v.is_nan() { f64::NAN } else { v };
            h = h.wrapping_mul(1000003) ^ (v.to_bits() as i64)
        };
        for v in 0..2_000_000 {
            mix(log(1.0 + v as f64));
        }
        for v in 1..400_000 {
            mix(log(v as f64 * 0.00025));
        }
        let mut s: u64 = 0x9E37_79B9_7F4A_7C15;
        for _ in 0..1_000_000 {
            s ^= s << 13;
            s ^= s >> 7;
            s ^= s << 17;
            mix(log(f64::from_bits(s)));
        }
        assert_eq!(STRICT_LOG_HASH, format!("{:x}", h));
    }

    const STRICT_LOG_HASH: &str = "d2dc08f9e66f56fe";

    /// As for [`log`], against this generator's output:
    ///
    /// ```java
    /// public class P {
    ///   static long h = 0;
    ///   static void mix(double r) { if (r != r) r = Double.NaN; h = h * 1000003 ^ Double.doubleToRawLongBits(r); }
    ///   public static void main(String[] a) {
    ///     for (int k = 0; k < 1_000_000; k++) mix(StrictMath.pow(2.0, -(k / 64.0)));
    ///     for (int k = 1; k < 200_000; k++) { double x = k * 0.0001; mix(StrictMath.pow(x, 1.7)); mix(StrictMath.pow(x, -0.5));
    ///       mix(StrictMath.pow(x, 3.0)); mix(StrictMath.pow(-x, 3.0)); mix(StrictMath.pow(-x, 2.0)); mix(StrictMath.pow(x, k % 40 - 20)); }
    ///     long s = 0x9E3779B97F4A7C15L;
    ///     for (int i = 0; i < 1_000_000; i++) { s ^= s << 13; s ^= s >>> 7; s ^= s << 17; double x = Double.longBitsToDouble(s);
    ///       s ^= s << 13; s ^= s >>> 7; s ^= s << 17; double y = Double.longBitsToDouble(s >>> 2) % 64.0;
    ///       mix(StrictMath.pow(x, y)); mix(StrictMath.pow(Math.abs(x) % 1000.0, y)); }
    ///     double[] sp = {0.0, -0.0, 1.0, -1.0, 2.0, 0.5, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
    ///       Double.NaN, Double.MIN_VALUE, Double.MAX_VALUE, -3.0, 1e-310};
    ///     for (double x : sp) for (double y : sp) mix(StrictMath.pow(x, y));
    ///     System.out.println(Long.toHexString(h)); } }
    /// ```
    #[test]
    fn pow_is_strict_math_bit_for_bit() {
        let mut h: i64 = 0;
        let mut mix = |v: f64| {
            let v = if v.is_nan() { f64::NAN } else { v };
            h = h.wrapping_mul(1000003) ^ (v.to_bits() as i64)
        };
        for k in 0..1_000_000 {
            mix(pow(2.0, -(k as f64 / 64.0)));
        }
        for k in 1..200_000 {
            let x = k as f64 * 0.0001;
            mix(pow(x, 1.7));
            mix(pow(x, -0.5));
            mix(pow(x, 3.0));
            mix(pow(-x, 3.0));
            mix(pow(-x, 2.0));
            mix(pow(x, (k % 40 - 20) as f64));
        }
        let mut s: u64 = 0x9E37_79B9_7F4A_7C15;
        for _ in 0..1_000_000 {
            s ^= s << 13;
            s ^= s >> 7;
            s ^= s << 17;
            let x = f64::from_bits(s);
            s ^= s << 13;
            s ^= s >> 7;
            s ^= s << 17;
            let y = f64::from_bits(s >> 2) % 64.0;
            mix(pow(x, y));
            mix(pow(x.abs() % 1000.0, y));
        }
        let sp = [
            0.0, -0.0, 1.0, -1.0, 2.0, 0.5, f64::INFINITY, f64::NEG_INFINITY, f64::NAN, f64::from_bits(1), f64::MAX, -3.0, 1e-310,
        ];
        for x in sp {
            for y in sp {
                mix(pow(x, y));
            }
        }
        assert_eq!("f5d98c7493ac1ec1", format!("{:x}", h));
    }

    #[test]
    fn edge_cases() {
        assert_eq!(f64::NEG_INFINITY, log(0.0));
        assert_eq!(f64::NEG_INFINITY, log(-0.0));
        assert!(log(-1.0).is_nan());
        assert!(log(f64::NAN).is_nan());
        assert_eq!(f64::INFINITY, log(f64::INFINITY));
        assert_eq!(0.0, log(1.0));
        assert_eq!(0.0, ln1p_count(0));
    }
}
