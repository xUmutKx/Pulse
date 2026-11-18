use pulse_shelf::{GameBox, Shelf};
use std::sync::Arc;

fn art(w: u32, h: u32, hue: f32) -> Vec<u8> {
    let mut v = vec![0u8; (w * h * 4) as usize];
    for y in 0..h {
        for x in 0..w {
            let fx = x as f32 / w as f32;
            let fy = y as f32 / h as f32;
            let r = (hue + fx * 0.6).sin() * 0.5 + 0.5;
            let g = (hue * 1.7 + fy * 2.0).sin() * 0.5 + 0.5;
            let b = 0.3 + 0.7 * fy;
            let border = fx < 0.04 || fx > 0.96 || fy < 0.03 || fy > 0.97;
            let c = if border { [1.0, 1.0, 1.0] } else { [r, g, b] };
            let i = ((y * w + x) * 4) as usize;
            v[i] = (c[0] * 255.0) as u8; v[i + 1] = (c[1] * 255.0) as u8; v[i + 2] = (c[2] * 255.0) as u8; v[i + 3] = 255;
        }
    }
    v
}

#[test]
fn render_png() {
    let inst = wgpu::Instance::new(&wgpu::InstanceDescriptor::default());
    let adapter = pollster::block_on(inst.request_adapter(&wgpu::RequestAdapterOptions::default())).expect("adapter");
    let (dev, q) = pollster::block_on(adapter.request_device(&wgpu::DeviceDescriptor::default())).expect("device");
    let (dev, q) = (Arc::new(dev), Arc::new(q));
    let (w, h) = (512u32, 960u32);
    let fmt = wgpu::TextureFormat::Rgba8UnormSrgb;
    let mut shelf = Shelf::new(dev.clone(), q.clone(), fmt, w, h);
    if let Ok(dir) = std::env::var("COVER_DIR") {
        let mut files: Vec<_> = std::fs::read_dir(dir).unwrap().filter_map(|e| e.ok()).map(|e| e.path()).filter(|p| p.extension().map(|x| x == "png").unwrap_or(false)).collect();
        files.sort();
        let games: Vec<GameBox> = files.iter().map(|p| {
            let im = image::open(p).unwrap().to_rgba8();
            let (w, h) = im.dimensions();
            let n = (w * h) as f32;
            let mut s = [0f32; 3];
            for px in im.pixels() { for c in 0..3 { s[c] += px[c] as f32 / 255.0 / n; } }
            GameBox { front: im.into_raw(), fw: w, fh: h, back: None, side: s }
        }).collect();
        shelf.set_games(games);
        shelf.select(std::env::var("SEL").ok().and_then(|v| v.parse().ok()).unwrap_or(3));
        if std::env::var("FLIP").is_ok() { shelf.flip(true); }
        for _ in 0..140 { shelf.update(0.016); }
    } else {
    shelf.set_games((0..5).map(|i| GameBox { front: art(256, 384, i as f32 * 1.3), fw: 256, fh: 384, back: if i == 1 { Some((art(256, 384, 9.0), 256, 384)) } else { None }, side: [0.2 + i as f32 * 0.12, 0.35, 0.8 - i as f32 * 0.1] }).collect());
    shelf.select(1);
    for _ in 0..120 { shelf.update(0.016); }
    }
    let tex = dev.create_texture(&wgpu::TextureDescriptor { label: None, size: wgpu::Extent3d { width: w, height: h, depth_or_array_layers: 1 }, mip_level_count: 1, sample_count: 1, dimension: wgpu::TextureDimension::D2, format: fmt, usage: wgpu::TextureUsages::RENDER_ATTACHMENT | wgpu::TextureUsages::COPY_SRC, view_formats: &[] });
    let view = tex.create_view(&Default::default());
    shelf.render(&view);
    let bpr = w * 4;
    let buf = dev.create_buffer(&wgpu::BufferDescriptor { label: None, size: (bpr * h) as u64, usage: wgpu::BufferUsages::COPY_DST | wgpu::BufferUsages::MAP_READ, mapped_at_creation: false });
    let mut enc = dev.create_command_encoder(&Default::default());
    enc.copy_texture_to_buffer(tex.as_image_copy(), wgpu::TexelCopyBufferInfo { buffer: &buf, layout: wgpu::TexelCopyBufferLayout { offset: 0, bytes_per_row: Some(bpr), rows_per_image: Some(h) } }, wgpu::Extent3d { width: w, height: h, depth_or_array_layers: 1 });
    q.submit(Some(enc.finish()));
    let slice = buf.slice(..);
    slice.map_async(wgpu::MapMode::Read, |_| {});
    dev.poll(wgpu::PollType::Wait).unwrap();
    let data = slice.get_mapped_range().to_vec();
    image::save_buffer("/tmp/shelf.png", &data, w, h, image::ColorType::Rgba8).unwrap();
}
