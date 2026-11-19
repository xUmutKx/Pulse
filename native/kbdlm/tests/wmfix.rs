use pulse_lm::wmfix::Fixer;
#[test]
fn fixes_turkish_and_english() {
    let tr = std::fs::read_to_string("../../app/src/main/assets/kbd/tr.txt").unwrap();
    let en = std::fs::read_to_string("../../app/src/main/assets/kbd/en.txt").unwrap();
    let mut t = Fixer::new(&tr, &["qwertyuıopğü", "asdfghjklşi", "zxcvbnmöç"]).unwrap();
    let mut e = Fixer::new(&en, &["qwertyuiop", "asdfghjkl", "zxcvbnm"]).unwrap();
    let t0 = std::time::Instant::now();
    for w in ["merhabq", "nasılsn", "gelyorum", "teşekkürker", "bugn"] { println!("tr {w} -> {:?}", t.search(w, 3)); }
    for w in ["twst", "helo", "teh", "recieve", "becuase"] { println!("en {w} -> {:?}", e.search(w, 3)); }
    println!("10 searches in {:?}", t0.elapsed());
}
