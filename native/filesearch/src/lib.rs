//! Fast file-name search for Pulse Files: the whole phone's folders are walked by several threads at once, the names are compared without allocating, and the walk stops
//! as soon as enough names matched. Called from Kotlin through JNI (`FileSearchNative`). std only (no thread pool crate).

use jni::objects::{JClass, JString};
use jni::sys::{jboolean, jint, jobjectArray};
use jni::JNIEnv;
use std::collections::VecDeque;
use std::fs;
use std::path::PathBuf;
use std::sync::atomic::{AtomicBool, AtomicUsize, Ordering};
use std::sync::{Arc, Condvar, Mutex};

/// A name folded for comparing: lower case, and the Turkish dotted / dotless i are the plain i (so "Iskender" finds "ıskender" and "İş" finds "iş").
fn fold(s: &str) -> String {
    let mut out = String::with_capacity(s.len());
    for c in s.chars() {
        match c {
            'İ' | 'ı' | 'I' => out.push('i'),
            _ if c.is_ascii() => out.push(c.to_ascii_lowercase()),
            _ => out.extend(c.to_lowercase()),
        }
    }
    out
}

struct Shared {
    queue: Mutex<VecDeque<PathBuf>>,
    cv: Condvar,
    /// directories queued or being read; the walk is over when it reaches zero
    pending: AtomicUsize,
    stop: AtomicBool,
    hits: Mutex<Vec<String>>,
}

/// Searches the names under [root]; [needle] is already folded. Returns at most [limit] paths (folders and files), in the order they were found.
pub fn search(root: &str, needle: &str, hidden: bool, limit: usize, threads: usize) -> Vec<String> {
    let needle = fold(needle.trim());
    if needle.is_empty() || limit == 0 {
        return Vec::new();
    }
    let sh = Arc::new(Shared {
        queue: Mutex::new(VecDeque::new()),
        cv: Condvar::new(),
        pending: AtomicUsize::new(1),
        stop: AtomicBool::new(false),
        hits: Mutex::new(Vec::new()),
    });
    sh.queue.lock().unwrap().push_back(PathBuf::from(root));
    let root_path = PathBuf::from(root);
    let mut handles = Vec::new();
    for _ in 0..threads.max(1) {
        let sh = Arc::clone(&sh);
        let needle = needle.clone();
        let root_path = root_path.clone();
        handles.push(std::thread::spawn(move || worker(&sh, &needle, hidden, limit, &root_path)));
    }
    for h in handles {
        let _ = h.join();
    }
    let mut v = sh.hits.lock().unwrap().clone();
    v.truncate(limit);
    v
}

fn worker(sh: &Shared, needle: &str, hidden: bool, limit: usize, root: &PathBuf) {
    loop {
        // take a folder, or wait for one (another thread may still queue more), or finish when none is queued or being read
        let dir = {
            let mut q = sh.queue.lock().unwrap();
            loop {
                if sh.stop.load(Ordering::Relaxed) {
                    return;
                }
                if let Some(d) = q.pop_front() {
                    break d;
                }
                if sh.pending.load(Ordering::SeqCst) == 0 {
                    sh.cv.notify_all();
                    return;
                }
                q = sh.cv.wait(q).unwrap();
            }
        };
        if let Ok(rd) = fs::read_dir(&dir) {
            let mut local_hits: Vec<String> = Vec::new();
            let mut subdirs: Vec<PathBuf> = Vec::new();
            for e in rd.flatten() {
                let name_os = e.file_name();
                let name = match name_os.to_str() {
                    Some(n) => n,
                    None => continue,
                };
                if !hidden && name.starts_with('.') {
                    continue;
                }
                let ft = match e.file_type() {
                    Ok(t) => t,
                    Err(_) => continue,
                };
                if fold(name).contains(needle) {
                    local_hits.push(e.path().to_string_lossy().into_owned());
                }
                // links are not followed (no loops); the app folders of Android/ are not readable anyway, so they are not entered at the top
                if ft.is_dir() && !(dir == *root && name == "Android") {
                    subdirs.push(e.path());
                }
            }
            if !local_hits.is_empty() {
                let mut h = sh.hits.lock().unwrap();
                h.extend(local_hits);
                if h.len() >= limit {
                    sh.stop.store(true, Ordering::Relaxed);
                }
            }
            if !subdirs.is_empty() {
                sh.pending.fetch_add(subdirs.len(), Ordering::SeqCst);
                let mut q = sh.queue.lock().unwrap();
                q.extend(subdirs);
            }
        }
        // this folder is done
        if sh.pending.fetch_sub(1, Ordering::SeqCst) == 1 || sh.stop.load(Ordering::Relaxed) {
            sh.cv.notify_all();
        } else {
            sh.cv.notify_one();
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_umutk_pulse_features_FileSearchNative_search(
    mut env: JNIEnv,
    _class: JClass,
    root: JString,
    query: JString,
    hidden: jboolean,
    limit: jint,
) -> jobjectArray {
    let root: String = env.get_string(&root).map(|s| s.into()).unwrap_or_default();
    let query: String = env.get_string(&query).map(|s| s.into()).unwrap_or_default();
    let found = search(&root, &query, hidden != 0, limit.max(1) as usize, 4);
    let string_class = env.find_class("java/lang/String").expect("String class");
    let empty = env.new_string("").expect("empty string");
    let arr = env.new_object_array(found.len() as jint, string_class, empty).expect("array");
    for (i, p) in found.iter().enumerate() {
        if let Ok(js) = env.new_string(p) {
            let _ = env.set_object_array_element(&arr, i as jint, js);
        }
    }
    arr.into_raw()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn folds_turkish_i() {
        assert_eq!(fold("İŞ Iskender ıspanak"), "iş iskender ispanak");
    }

    #[test]
    fn finds_names_in_nested_folders() {
        let base = std::env::temp_dir().join(format!("pulse_search_test_{}", std::process::id()));
        let _ = fs::remove_dir_all(&base);
        fs::create_dir_all(base.join("a/b/c")).unwrap();
        fs::write(base.join("a/b/c/Rapor_2026.pdf"), b"x").unwrap();
        fs::write(base.join("a/notes.txt"), b"x").unwrap();
        fs::write(base.join("a/.hidden_rapor"), b"x").unwrap();
        let r = search(base.to_str().unwrap(), "rapor", false, 50, 4);
        assert_eq!(r.len(), 1, "{:?}", r);
        assert!(r[0].ends_with("Rapor_2026.pdf"));
        let r2 = search(base.to_str().unwrap(), "rapor", true, 50, 4);
        assert_eq!(r2.len(), 2);
        let r3 = search(base.to_str().unwrap(), "zzz", true, 50, 4);
        assert!(r3.is_empty());
        let _ = fs::remove_dir_all(&base);
    }

    #[test]
    fn stops_at_the_limit() {
        let base = std::env::temp_dir().join(format!("pulse_search_limit_{}", std::process::id()));
        let _ = fs::remove_dir_all(&base);
        fs::create_dir_all(&base).unwrap();
        for i in 0..40 {
            fs::write(base.join(format!("match_{i}.txt")), b"x").unwrap();
        }
        let r = search(base.to_str().unwrap(), "match", false, 10, 4);
        assert_eq!(r.len(), 10);
        let _ = fs::remove_dir_all(&base);
    }
}
