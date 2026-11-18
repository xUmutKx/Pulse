
use crate::{GameBox, Shelf};
use jni::objects::{JByteArray, JClass, JObject};
use jni::sys::{jboolean, jfloat, jint, jlong};
use jni::JNIEnv;
use raw_window_handle::{AndroidDisplayHandle, AndroidNdkWindowHandle, RawDisplayHandle, RawWindowHandle};
use std::sync::Arc;

struct State {
    _window: ndk::native_window::NativeWindow,
    surface: wgpu::Surface<'static>,
    device: Arc<wgpu::Device>,
    queue: Arc<wgpu::Queue>,
    config: wgpu::SurfaceConfiguration,
    shelf: Shelf,
    pending: Vec<GameBox>,
}

fn st<'a>(h: jlong) -> &'a mut State {
    unsafe { &mut *(h as *mut State) }
}

#[no_mangle]
pub extern "system" fn Java_com_umutk_pulse_extras_gamehub_NativeShelf_create(env: JNIEnv, _c: JClass, surface: JObject, w: jint, h: jint) -> jlong {
    let window = match unsafe { ndk::native_window::NativeWindow::from_surface(env.get_raw() as *mut _, surface.as_raw() as *mut _) } {
        Some(w) => w,
        None => return 0,
    };
    let instance = wgpu::Instance::new(&wgpu::InstanceDescriptor::default());
    let handle = RawWindowHandle::AndroidNdk(AndroidNdkWindowHandle::new(window.ptr().cast()));
    let surf = match unsafe { instance.create_surface_unsafe(wgpu::SurfaceTargetUnsafe::RawHandle { raw_display_handle: RawDisplayHandle::Android(AndroidDisplayHandle::new()), raw_window_handle: handle }) } {
        Ok(s) => s,
        Err(_) => return 0,
    };
    let adapter = match pollster::block_on(instance.request_adapter(&wgpu::RequestAdapterOptions { power_preference: wgpu::PowerPreference::HighPerformance, compatible_surface: Some(&surf), force_fallback_adapter: false })) {
        Ok(a) => a,
        Err(_) => return 0,
    };
    let (device, queue) = match pollster::block_on(adapter.request_device(&wgpu::DeviceDescriptor { required_limits: wgpu::Limits::downlevel_defaults().using_resolution(adapter.limits()), ..Default::default() })) {
        Ok(x) => x,
        Err(_) => return 0,
    };
    let caps = surf.get_capabilities(&adapter);
    let format = caps.formats.iter().copied().find(|f| f.is_srgb()).unwrap_or(caps.formats[0]);
    let config = wgpu::SurfaceConfiguration { usage: wgpu::TextureUsages::RENDER_ATTACHMENT, format, width: w.max(1) as u32, height: h.max(1) as u32, present_mode: wgpu::PresentMode::Fifo, desired_maximum_frame_latency: 2, alpha_mode: caps.alpha_modes[0], view_formats: vec![] };
    surf.configure(&device, &config);
    let (device, queue) = (Arc::new(device), Arc::new(queue));
    let shelf = Shelf::new(device.clone(), queue.clone(), format, config.width, config.height);
    Box::into_raw(Box::new(State { _window: window, surface: surf, device, queue, config, shelf, pending: Vec::new() })) as jlong
}

#[no_mangle]
pub extern "system" fn Java_com_umutk_pulse_extras_gamehub_NativeShelf_destroy(_e: JNIEnv, _c: JClass, h: jlong) {
    if h != 0 {
        unsafe { drop(Box::from_raw(h as *mut State)) };
    }
}

#[no_mangle]
pub extern "system" fn Java_com_umutk_pulse_extras_gamehub_NativeShelf_resize(_e: JNIEnv, _c: JClass, h: jlong, w: jint, hh: jint) {
    let s = st(h);
    s.config.width = w.max(1) as u32;
    s.config.height = hh.max(1) as u32;
    s.surface.configure(&s.device, &s.config);
    s.shelf.resize(s.config.width, s.config.height);
}

#[no_mangle]
pub extern "system" fn Java_com_umutk_pulse_extras_gamehub_NativeShelf_addGame<'l>(env: JNIEnv<'l>, _c: JClass<'l>, h: jlong, front: JByteArray<'l>, fw: jint, fh: jint, back: JByteArray<'l>, bw: jint, bh: jint, r: jfloat, g: jfloat, b: jfloat) {
    let s = st(h);
    let f = env.convert_byte_array(&front).unwrap_or_default();
    let back = if back.is_null() { None } else { env.convert_byte_array(&back).ok().map(|d| (d, bw as u32, bh as u32)) };
    if f.len() as i64 >= fw as i64 * fh as i64 * 4 && fw > 0 && fh > 0 {
        s.pending.push(GameBox { front: f, fw: fw as u32, fh: fh as u32, back, side: [r, g, b] });
    }
}

#[no_mangle]
pub extern "system" fn Java_com_umutk_pulse_extras_gamehub_NativeShelf_commit(_e: JNIEnv, _c: JClass, h: jlong) {
    let s = st(h);
    let g = std::mem::take(&mut s.pending);
    s.shelf.set_games(g);
}

#[no_mangle]
pub extern "system" fn Java_com_umutk_pulse_extras_gamehub_NativeShelf_drag(_e: JNIEnv, _c: JClass, h: jlong, di: jfloat) {
    st(h).shelf.drag(di);
}

#[no_mangle]
pub extern "system" fn Java_com_umutk_pulse_extras_gamehub_NativeShelf_release(_e: JNIEnv, _c: JClass, h: jlong, v: jfloat) {
    st(h).shelf.release(v);
}

#[no_mangle]
pub extern "system" fn Java_com_umutk_pulse_extras_gamehub_NativeShelf_select(_e: JNIEnv, _c: JClass, h: jlong, i: jint) {
    st(h).shelf.select(i.max(0) as usize);
}

#[no_mangle]
pub extern "system" fn Java_com_umutk_pulse_extras_gamehub_NativeShelf_flip(_e: JNIEnv, _c: JClass, h: jlong, back: jboolean) {
    st(h).shelf.flip(back != 0);
}

#[no_mangle]
pub extern "system" fn Java_com_umutk_pulse_extras_gamehub_NativeShelf_selected(_e: JNIEnv, _c: JClass, h: jlong) -> jint {
    st(h).shelf.selected() as jint
}

#[no_mangle]
pub extern "system" fn Java_com_umutk_pulse_extras_gamehub_NativeShelf_flipped(_e: JNIEnv, _c: JClass, h: jlong) -> jboolean {
    st(h).shelf.flipped() as jboolean
}

#[no_mangle]
pub extern "system" fn Java_com_umutk_pulse_extras_gamehub_NativeShelf_frame(_e: JNIEnv, _c: JClass, h: jlong, dt: jfloat) -> jboolean {
    let s = st(h);
    s.shelf.update(dt);
    match s.surface.get_current_texture() {
        Ok(t) => {
            let view = t.texture.create_view(&Default::default());
            s.shelf.render(&view);
            t.present();
            1
        }
        Err(wgpu::SurfaceError::Outdated) | Err(wgpu::SurfaceError::Lost) => {
            s.surface.configure(&s.device, &s.config);
            1
        }
        Err(_) => 0,
    }
}
