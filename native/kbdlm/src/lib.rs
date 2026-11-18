//! Pulse's keyboard language model (a one-layer character GRU) in Rust: the same file format and the same maths as
//! `CharLm.java`, called through JNI. The Java class stays as the fallback when this library is not in the APK.

use jni::objects::{JClass, JFloatArray, JString};
use jni::sys::{jfloat, jlong};
use jni::JNIEnv;

const LANES: usize = 8;

pub struct Lm {
    v: usize,
    h: usize,
    chars: Vec<u16>,
    map: Vec<i32>,
    unk: i32,
    space: i32,
    start: i32,
    x: Vec<f32>,  // V x 3H
    w: Vec<i8>,   // 3H x H
    ws: Vec<f32>, // 3H
    bh: Vec<f32>, // 3H
    o: Vec<i8>,   // V x H
    os: Vec<f32>, // V
    bo: Vec<f32>, // V
}

struct Rd<'a> {
    b: &'a [u8],
    p: usize,
}

impl<'a> Rd<'a> {
    fn take(&mut self, n: usize) -> Option<&'a [u8]> {
        if self.p + n > self.b.len() {
            return None;
        }
        let s = &self.b[self.p..self.p + n];
        self.p += n;
        Some(s)
    }
    fn i32(&mut self) -> Option<i32> {
        self.take(4).map(|s| i32::from_be_bytes([s[0], s[1], s[2], s[3]]))
    }
    fn f32s(&mut self, n: usize) -> Option<Vec<f32>> {
        let s = self.take(n * 4)?;
        Some(s.chunks_exact(4).map(|c| f32::from_bits(u32::from_be_bytes([c[0], c[1], c[2], c[3]]))).collect())
    }
    fn halves(&mut self, n: usize) -> Option<Vec<f32>> {
        let s = self.take(n * 2)?;
        Some(s.chunks_exact(2).map(|c| half(u16::from_be_bytes([c[0], c[1]]))).collect())
    }
    fn i8s(&mut self, n: usize) -> Option<Vec<i8>> {
        self.take(n).map(|s| s.iter().map(|&b| b as i8).collect())
    }
}

/// IEEE half float to float.
fn half(b: u16) -> f32 {
    let e = ((b >> 10) & 0x1f) as i32;
    let m = (b & 0x3ff) as u32;
    let sign = if b & 0x8000 != 0 { -1f32 } else { 1f32 };
    if e == 0 {
        return sign * m as f32 * 5.9604645e-8;
    }
    if e == 31 {
        return if m == 0 { sign * f32::INFINITY } else { f32::NAN };
    }
    sign * f32::from_bits((((e - 15 + 127) as u32) << 23) | (m << 13))
}

#[inline]
fn sig(a: f32) -> f32 {
    1.0 / (1.0 + ((-a) as f64).exp() as f32)
}

impl Lm {
    pub fn load(data: &[u8]) -> Option<Lm> {
        let mut r = Rd { b: data, p: 0 };
        if r.take(4)? != b"PLM1" {
            return None;
        }
        let v = r.i32()? as usize;
        let h = r.i32()? as usize;
        let n = r.i32()? as usize;
        let cs = r.take(n)?;
        let text = String::from_utf8_lossy(cs).to_string();
        let chars: Vec<u16> = text.encode_utf16().collect();
        if chars.len() != v {
            return None;
        }
        let find = |c: char| chars.iter().position(|&x| x == c as u16).map(|i| i as i32).unwrap_or(-1);
        let unk = find('#');
        let space = find(' ');
        let start = find('\n');
        let mut map = vec![unk; 0x500];
        for (i, &c) in chars.iter().enumerate() {
            if (c as usize) < map.len() {
                map[c as usize] = i as i32;
            }
        }
        for c in b'1'..=b'9' {
            map[c as usize] = map[b'0' as usize];
        }
        let g = 3 * h;
        let x = r.halves(v * g)?;
        let w = r.i8s(g * h)?;
        let ws = r.f32s(g)?;
        let bh = r.f32s(g)?;
        let o = r.i8s(v * h)?;
        let os = r.f32s(v)?;
        let bo = r.f32s(v)?;
        Some(Lm { v, h, chars, map, unk, space, start, x, w, ws, bh, o, os, bo })
    }

    pub fn cased(&self) -> bool {
        self.chars.contains(&(b'A' as u16))
    }

    /// The index of a UTF-16 unit, as `CharLm.index` does it.
    fn index(&self, u: u16) -> i32 {
        // Java's Character.isWhitespace: not the no-break spaces
        let ws = matches!(u, 9..=13 | 28..=31 | 32 | 0x1680 | 0x2000..=0x2006 | 0x2008..=0x200A | 0x2028 | 0x2029 | 0x205F | 0x3000);
        if ws {
            return self.space;
        }
        let mut c = u;
        if c == 0x2019 || c == b'`' as u16 {
            c = b'\'' as u16;
        }
        if c == 0x2013 || c == 0x2014 {
            c = b'-' as u16;
        }
        if matches!(c, 0x201C | 0x201D | 0x00AB | 0x00BB) {
            c = b'"' as u16;
        }
        if (c as usize) < self.map.len() {
            self.map[c as usize]
        } else {
            self.unk
        }
    }

    /// One character in: the state moves on in place. `tmp` holds 3H floats.
    fn step(&self, st: &mut [f32], c: i32, tmp: &mut [f32]) {
        let h = self.h;
        let g = 3 * h;
        for r in 0..g {
            let row = &self.w[r * h..r * h + h];
            let mut acc = [0f32; LANES];
            let mut rc = row.chunks_exact(LANES);
            let mut sc = st.chunks_exact(LANES);
            for (a, b) in (&mut rc).zip(&mut sc) {
                for k in 0..LANES {
                    acc[k] += a[k] as f32 * b[k];
                }
            }
            let mut s = 0f32;
            for k in 0..LANES {
                s += acc[k];
            }
            for (a, b) in rc.remainder().iter().zip(sc.remainder()) {
                s += *a as f32 * *b;
            }
            tmp[r] = s * self.ws[r] + self.bh[r];
        }
        let xb = c as usize * g;
        for j in 0..h {
            let rr = sig(self.x[xb + j] + tmp[j]);
            let zz = sig(self.x[xb + h + j] + tmp[h + j]);
            let nn = ((self.x[xb + 2 * h + j] + rr * tmp[2 * h + j]) as f64).tanh() as f32;
            st[j] = (1.0 - zz) * nn + zz * st[j];
        }
    }

    /// log2 of the chance of character `c` after state `st`.
    fn logp(&self, st: &[f32], c: i32, lg: &mut [f32]) -> f32 {
        let h = self.h;
        let mut max = -1e30f32;
        for k in 0..self.v {
            let row = &self.o[k * h..k * h + h];
            let mut acc = [0f32; LANES];
            let mut rc = row.chunks_exact(LANES);
            let mut sc = st.chunks_exact(LANES);
            for (a, b) in (&mut rc).zip(&mut sc) {
                for l in 0..LANES {
                    acc[l] += a[l] as f32 * b[l];
                }
            }
            let mut s = 0f32;
            for l in 0..LANES {
                s += acc[l];
            }
            for (a, b) in rc.remainder().iter().zip(sc.remainder()) {
                s += *a as f32 * *b;
            }
            lg[k] = s * self.os[k] + self.bo[k];
            if lg[k] > max {
                max = lg[k];
            }
        }
        let mut sum = 0f64;
        for k in 0..self.v {
            sum += ((lg[k] - max) as f64).exp();
        }
        (((lg[c as usize] - max) as f64 - sum.ln()) / std::f64::consts::LN_2) as f32
    }

    pub fn state(&self, text: &[u16]) -> Vec<f32> {
        let mut st = vec![0f32; self.h];
        let mut tmp = vec![0f32; 3 * self.h];
        self.step(&mut st, self.start, &mut tmp);
        let from = text.len().saturating_sub(120);
        for &u in &text[from..] {
            let i = self.index(u);
            self.step(&mut st, i, &mut tmp);
        }
        st
    }

    pub fn advance(&self, st: &[f32], more: &[u16]) -> Vec<f32> {
        let mut r = st.to_vec();
        let mut tmp = vec![0f32; 3 * self.h];
        for &u in more {
            let i = self.index(u);
            self.step(&mut r, i, &mut tmp);
        }
        r
    }

    pub fn score(&self, ctx: &[f32], word: &[u16], floor: f32) -> f32 {
        let mut st = ctx.to_vec();
        let mut tmp = vec![0f32; 3 * self.h];
        let mut lg = vec![0f32; self.v];
        let mut s = 0f32;
        let n = word.len();
        for i in 0..=n {
            let c = if i < n { self.index(word[i]) } else { self.space };
            s += self.logp(&st, c, &mut lg);
            if s < floor {
                return s;
            }
            if i < n {
                self.step(&mut st, c, &mut tmp);
            }
        }
        s
    }

    pub fn step_nanos(&self) -> i64 {
        let mut st = vec![0f32; self.h];
        let mut tmp = vec![0f32; 3 * self.h];
        for i in 0..60 {
            self.step(&mut st, (i % self.v) as i32, &mut tmp);
        }
        let mut t = [0i64; 7];
        for k in 0..t.len() {
            let a = std::time::Instant::now();
            for i in 0..24 {
                self.step(&mut st, ((i * 7) % self.v) as i32, &mut tmp);
            }
            t[k] = a.elapsed().as_nanos() as i64 / 24;
        }
        t.sort();
        t[t.len() / 2]
    }
}

// ---- JNI: com.umutk.pulse.extras.keyboard.CharLmNative ------------------------------------------------------------------------------------------

fn utf16(env: &mut JNIEnv, s: &JString) -> Vec<u16> {
    env.get_string(s).map(|j| String::from(j).encode_utf16().collect()).unwrap_or_default()
}

fn floats(env: &mut JNIEnv, a: &JFloatArray) -> Vec<f32> {
    let n = env.get_array_length(a).unwrap_or(0) as usize;
    let mut v = vec![0f32; n];
    let _ = env.get_float_array_region(a, 0, &mut v);
    v
}

fn to_java<'l>(env: &mut JNIEnv<'l>, v: &[f32]) -> JFloatArray<'l> {
    let a = env.new_float_array(v.len() as i32).expect("float array");
    let _ = env.set_float_array_region(&a, 0, v);
    a
}

#[no_mangle]
pub extern "system" fn Java_com_umutk_pulse_extras_keyboard_CharLmNative_load<'l>(env: JNIEnv<'l>, _c: JClass<'l>, data: jni::objects::JByteArray<'l>) -> jlong {
    let bytes = match env.convert_byte_array(&data) {
        Ok(b) => b,
        Err(_) => return 0,
    };
    match Lm::load(&bytes) {
        Some(lm) => Box::into_raw(Box::new(lm)) as jlong,
        None => 0,
    }
}

#[no_mangle]
pub extern "system" fn Java_com_umutk_pulse_extras_keyboard_CharLmNative_free(_env: JNIEnv, _c: JClass, h: jlong) {
    if h != 0 {
        unsafe { drop(Box::from_raw(h as *mut Lm)) };
    }
}

#[no_mangle]
pub extern "system" fn Java_com_umutk_pulse_extras_keyboard_CharLmNative_cased(_env: JNIEnv, _c: JClass, h: jlong) -> jni::sys::jboolean {
    let lm = unsafe { &*(h as *const Lm) };
    lm.cased() as jni::sys::jboolean
}

#[no_mangle]
pub extern "system" fn Java_com_umutk_pulse_extras_keyboard_CharLmNative_state<'l>(mut env: JNIEnv<'l>, _c: JClass<'l>, h: jlong, text: JString<'l>) -> JFloatArray<'l> {
    let lm = unsafe { &*(h as *const Lm) };
    let t = utf16(&mut env, &text);
    let st = lm.state(&t);
    to_java(&mut env, &st)
}

#[no_mangle]
pub extern "system" fn Java_com_umutk_pulse_extras_keyboard_CharLmNative_advance<'l>(mut env: JNIEnv<'l>, _c: JClass<'l>, h: jlong, st: JFloatArray<'l>, more: JString<'l>) -> JFloatArray<'l> {
    let lm = unsafe { &*(h as *const Lm) };
    let s = floats(&mut env, &st);
    let m = utf16(&mut env, &more);
    let r = lm.advance(&s, &m);
    to_java(&mut env, &r)
}

#[no_mangle]
pub extern "system" fn Java_com_umutk_pulse_extras_keyboard_CharLmNative_score<'l>(mut env: JNIEnv<'l>, _c: JClass<'l>, h: jlong, ctx: JFloatArray<'l>, word: JString<'l>, floor: jfloat) -> jfloat {
    let lm = unsafe { &*(h as *const Lm) };
    let c = floats(&mut env, &ctx);
    let w = utf16(&mut env, &word);
    lm.score(&c, &w, floor)
}

#[no_mangle]
pub extern "system" fn Java_com_umutk_pulse_extras_keyboard_CharLmNative_stepNanos(_env: JNIEnv, _c: JClass, h: jlong) -> jlong {
    let lm = unsafe { &*(h as *const Lm) };
    lm.step_nanos()
}
