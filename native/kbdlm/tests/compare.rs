// Prints the scores for fixed sentences; `scripts/lm_compare.sh` runs the Java model on the same ones and checks that they agree.
use pulse_lm::Lm;

fn u(s: &str) -> Vec<u16> { s.encode_utf16().collect() }

#[test]
fn print_scores() {
    let path = std::env::var("LM_FILE").unwrap_or_else(|_| "../../app/src/main/assets/kb/lm_tr.bin".into());
    let lm = Lm::load(&std::fs::read(path).unwrap()).expect("model");
    let cases = [("bugün hava çok ", "güzel"), ("bugün hava çok ", "guzel"), ("nasılsın bugün ", "ne"), ("Merhaba, ben ", "Umut"), ("Selam ve Bir şey yok ", "ya"), ("", "bir")];
    let mut out = String::new();
    for (ctx, w) in cases {
        let st = lm.state(&u(ctx));
        let a = lm.score(&st, &u(w), f32::NEG_INFINITY);
        let adv = lm.advance(&st, &u("x"));
        let b = lm.score(&adv, &u("y"), f32::NEG_INFINITY);
        out.push_str(&format!("{}|{}|{:.4}|{:.4}\n", ctx, w, a, b));
    }
    std::fs::write(std::env::var("LM_OUT").unwrap_or_else(|_| "/tmp/rust_scores.txt".into()), out).unwrap();
}

#[test]
fn bench() {
    let path = std::env::var("LM_FILE").unwrap_or_else(|_| "../../app/src/main/assets/kb/lm_tr.bin".into());
    let lm = Lm::load(&std::fs::read(path).unwrap()).expect("model");
    let r: Vec<i64> = (0..5).map(|_| lm.step_nanos()).collect();
    let st = lm.state(&u("bugün hava çok "));
    let t = std::time::Instant::now();
    for _ in 0..200 { lm.score(&st, &u("güzel"), f32::NEG_INFINITY); }
    println!("rust step ns: {:?}\nrust score us: {}", r, t.elapsed().as_micros() / 200);
}
