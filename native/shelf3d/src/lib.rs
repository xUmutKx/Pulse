
use bytemuck::{Pod, Zeroable};
use glam::{Mat4, Vec3};
use std::sync::Arc;

#[cfg(target_os = "android")]
mod android;

const SAMPLES: u32 = 4;
const DEPTH: wgpu::TextureFormat = wgpu::TextureFormat::Depth32Float;

pub struct GameBox {
    pub front: Vec<u8>, // RGBA8
    pub fw: u32,
    pub fh: u32,
    pub back: Option<(Vec<u8>, u32, u32)>,
    pub side: [f32; 3],
}

#[repr(C)]
#[derive(Clone, Copy, Pod, Zeroable)]
struct Vertex {
    pos: [f32; 3],
    normal: [f32; 3],
    uv: [f32; 2],
    tint: [f32; 4],
}

#[repr(C)]
#[derive(Clone, Copy, Pod, Zeroable)]
struct Globals {
    vp: [[f32; 4]; 4],
    eye: [f32; 4],
    time: [f32; 4],
}

struct GpuBox {
    front: wgpu::BindGroup,
    back: wgpu::BindGroup,
    plain: wgpu::BindGroup,
    side: [f32; 3],
    ratio: f32, // width / height of the front picture
}

pub struct Shelf {
    device: Arc<wgpu::Device>,
    queue: Arc<wgpu::Queue>,
    format: wgpu::TextureFormat,
    w: u32,
    h: u32,
    msaa: Option<wgpu::TextureView>,
    depth: Option<wgpu::TextureView>,
    globals_buf: wgpu::Buffer,
    globals_bg: wgpu::BindGroup,
    tex_layout: wgpu::BindGroupLayout,
    sampler: wgpu::Sampler,
    p_bg: wgpu::RenderPipeline,
    p_box: wgpu::RenderPipeline,
    p_ref: wgpu::RenderPipeline,
    vbuf: wgpu::Buffer,
    vcap: u64,
    boxes: Vec<GpuBox>,
    pub scroll: f32,
    target: f32,
    vel: f32,
    dragging: bool,
    flip: f32,
    flip_to: f32,
    time: f32,
}

fn mip_chain(data: &[u8], w: u32, h: u32) -> Vec<(Vec<u8>, u32, u32)> {
    let mut out = vec![(data.to_vec(), w, h)];
    loop {
        let (prev, pw, ph) = out.last().unwrap();
        if *pw <= 1 && *ph <= 1 {
            break;
        }
        let (nw, nh) = ((*pw / 2).max(1), (*ph / 2).max(1));
        let mut n = vec![0u8; (nw * nh * 4) as usize];
        for y in 0..nh {
            for x in 0..nw {
                for c in 0..4 {
                    let mut s = 0u32;
                    for (dx, dy) in [(0, 0), (1, 0), (0, 1), (1, 1)] {
                        let sx = (x * 2 + dx).min(*pw - 1);
                        let sy = (y * 2 + dy).min(*ph - 1);
                        s += prev[((sy * *pw + sx) * 4 + c) as usize] as u32;
                    }
                    n[((y * nw + x) * 4 + c) as usize] = (s / 4) as u8;
                }
            }
        }
        out.push((n, nw, nh));
    }
    out
}

impl Shelf {
    pub fn new(device: Arc<wgpu::Device>, queue: Arc<wgpu::Queue>, format: wgpu::TextureFormat, w: u32, h: u32) -> Shelf {
        let shader = device.create_shader_module(wgpu::ShaderModuleDescriptor { label: Some("shelf"), source: wgpu::ShaderSource::Wgsl(include_str!("shader.wgsl").into()) });
        let g_layout = device.create_bind_group_layout(&wgpu::BindGroupLayoutDescriptor {
            label: None,
            entries: &[wgpu::BindGroupLayoutEntry { binding: 0, visibility: wgpu::ShaderStages::VERTEX_FRAGMENT, ty: wgpu::BindingType::Buffer { ty: wgpu::BufferBindingType::Uniform, has_dynamic_offset: false, min_binding_size: None }, count: None }],
        });
        let tex_layout = device.create_bind_group_layout(&wgpu::BindGroupLayoutDescriptor {
            label: None,
            entries: &[
                wgpu::BindGroupLayoutEntry { binding: 0, visibility: wgpu::ShaderStages::FRAGMENT, ty: wgpu::BindingType::Texture { sample_type: wgpu::TextureSampleType::Float { filterable: true }, view_dimension: wgpu::TextureViewDimension::D2, multisampled: false }, count: None },
                wgpu::BindGroupLayoutEntry { binding: 1, visibility: wgpu::ShaderStages::FRAGMENT, ty: wgpu::BindingType::Sampler(wgpu::SamplerBindingType::Filtering), count: None },
            ],
        });
        let pl = device.create_pipeline_layout(&wgpu::PipelineLayoutDescriptor { label: None, bind_group_layouts: &[&g_layout, &tex_layout], push_constant_ranges: &[] });
        let globals_buf = device.create_buffer(&wgpu::BufferDescriptor { label: None, size: std::mem::size_of::<Globals>() as u64, usage: wgpu::BufferUsages::UNIFORM | wgpu::BufferUsages::COPY_DST, mapped_at_creation: false });
        let globals_bg = device.create_bind_group(&wgpu::BindGroupDescriptor { label: None, layout: &g_layout, entries: &[wgpu::BindGroupEntry { binding: 0, resource: globals_buf.as_entire_binding() }] });
        let sampler = device.create_sampler(&wgpu::SamplerDescriptor { mag_filter: wgpu::FilterMode::Linear, min_filter: wgpu::FilterMode::Linear, mipmap_filter: wgpu::FilterMode::Linear, address_mode_u: wgpu::AddressMode::ClampToEdge, address_mode_v: wgpu::AddressMode::ClampToEdge, anisotropy_clamp: 4, ..Default::default() });
        let attrs = wgpu::vertex_attr_array![0 => Float32x3, 1 => Float32x3, 2 => Float32x2, 3 => Float32x4];
        let vlayout = wgpu::VertexBufferLayout { array_stride: std::mem::size_of::<Vertex>() as u64, step_mode: wgpu::VertexStepMode::Vertex, attributes: &attrs };
        let ms = wgpu::MultisampleState { count: SAMPLES, mask: !0, alpha_to_coverage_enabled: false };
        let prim = wgpu::PrimitiveState { topology: wgpu::PrimitiveTopology::TriangleList, cull_mode: None, ..Default::default() };
        let make = |label: &str, vs: &str, fs: &str, bufs: &[wgpu::VertexBufferLayout], depth_write: bool, blend: Option<wgpu::BlendState>| {
            device.create_render_pipeline(&wgpu::RenderPipelineDescriptor {
                label: Some(label),
                layout: Some(&pl),
                vertex: wgpu::VertexState { module: &shader, entry_point: Some(vs), compilation_options: Default::default(), buffers: bufs },
                primitive: prim,
                depth_stencil: Some(wgpu::DepthStencilState { format: DEPTH, depth_write_enabled: depth_write, depth_compare: wgpu::CompareFunction::LessEqual, stencil: Default::default(), bias: Default::default() }),
                multisample: ms,
                fragment: Some(wgpu::FragmentState { module: &shader, entry_point: Some(fs), compilation_options: Default::default(), targets: &[Some(wgpu::ColorTargetState { format, blend, write_mask: wgpu::ColorWrites::ALL })] }),
                multiview: None,
                cache: None,
            })
        };
        let p_bg = make("bg", "vs_bg", "fs_bg", &[], false, None);
        let p_box = make("box", "vs", "fs", &[vlayout.clone()], true, None);
        let p_ref = make("ref", "vs", "fs_reflect", &[vlayout], false, Some(wgpu::BlendState::ALPHA_BLENDING));
        let vcap = 64 * 1024;
        let vbuf = device.create_buffer(&wgpu::BufferDescriptor { label: None, size: vcap, usage: wgpu::BufferUsages::VERTEX | wgpu::BufferUsages::COPY_DST, mapped_at_creation: false });
        let mut s = Shelf {
            device, queue, format, w: 0, h: 0, msaa: None, depth: None, globals_buf, globals_bg, tex_layout, sampler, p_bg, p_box, p_ref, vbuf, vcap,
            boxes: Vec::new(), scroll: 0.0, target: 0.0, vel: 0.0, dragging: false, flip: 0.0, flip_to: 0.0, time: 0.0,
        };
        s.resize(w, h);
        s
    }

    pub fn resize(&mut self, w: u32, h: u32) {
        let (w, h) = (w.max(1), h.max(1));
        self.w = w;
        self.h = h;
        let size = wgpu::Extent3d { width: w, height: h, depth_or_array_layers: 1 };
        let m = self.device.create_texture(&wgpu::TextureDescriptor { label: None, size, mip_level_count: 1, sample_count: SAMPLES, dimension: wgpu::TextureDimension::D2, format: self.format, usage: wgpu::TextureUsages::RENDER_ATTACHMENT, view_formats: &[] });
        self.msaa = Some(m.create_view(&Default::default()));
        let d = self.device.create_texture(&wgpu::TextureDescriptor { label: None, size, mip_level_count: 1, sample_count: SAMPLES, dimension: wgpu::TextureDimension::D2, format: DEPTH, usage: wgpu::TextureUsages::RENDER_ATTACHMENT, view_formats: &[] });
        self.depth = Some(d.create_view(&Default::default()));
    }

    fn upload(&self, data: &[u8], w: u32, h: u32) -> wgpu::BindGroup {
        let chain = mip_chain(data, w, h);
        let tex = self.device.create_texture(&wgpu::TextureDescriptor {
            label: None,
            size: wgpu::Extent3d { width: w, height: h, depth_or_array_layers: 1 },
            mip_level_count: chain.len() as u32,
            sample_count: 1,
            dimension: wgpu::TextureDimension::D2,
            format: wgpu::TextureFormat::Rgba8UnormSrgb,
            usage: wgpu::TextureUsages::TEXTURE_BINDING | wgpu::TextureUsages::COPY_DST,
            view_formats: &[],
        });
        for (i, (px, mw, mh)) in chain.iter().enumerate() {
            self.queue.write_texture(
                wgpu::TexelCopyTextureInfo { texture: &tex, mip_level: i as u32, origin: wgpu::Origin3d::ZERO, aspect: wgpu::TextureAspect::All },
                px,
                wgpu::TexelCopyBufferLayout { offset: 0, bytes_per_row: Some(mw * 4), rows_per_image: Some(*mh) },
                wgpu::Extent3d { width: *mw, height: *mh, depth_or_array_layers: 1 },
            );
        }
        let view = tex.create_view(&Default::default());
        self.device.create_bind_group(&wgpu::BindGroupDescriptor { label: None, layout: &self.tex_layout, entries: &[wgpu::BindGroupEntry { binding: 0, resource: wgpu::BindingResource::TextureView(&view) }, wgpu::BindGroupEntry { binding: 1, resource: wgpu::BindingResource::Sampler(&self.sampler) }] })
    }

    pub fn set_games(&mut self, games: Vec<GameBox>) {
        let mut out = Vec::new();
        for g in games {
            let front = self.upload(&g.front, g.fw, g.fh);
            let back = match &g.back {
                Some((d, w, h)) => self.upload(d, *w, *h),
                None => self.upload(&flat(g.side, 0.5), 2, 2),
            };
            let plain = self.upload(&flat(g.side, 1.0), 2, 2);
            out.push(GpuBox { front, back, plain, side: g.side, ratio: g.fw as f32 / g.fh.max(1) as f32 });
        }
        self.boxes = out;
        let max = self.boxes.len().saturating_sub(1) as f32;
        self.scroll = self.scroll.clamp(0.0, max);
        self.target = self.target.clamp(0.0, max);
    }

    pub fn count(&self) -> usize {
        self.boxes.len()
    }
    pub fn selected(&self) -> usize {
        self.target.round().max(0.0) as usize
    }
    pub fn select(&mut self, i: usize) {
        self.target = (i as f32).clamp(0.0, self.boxes.len().saturating_sub(1) as f32);
        self.flip_to = 0.0;
    }
    pub fn flip(&mut self, back: bool) {
        self.flip_to = if back { 1.0 } else { 0.0 };
    }
    pub fn flipped(&self) -> bool {
        self.flip_to > 0.5
    }
    pub fn drag(&mut self, di: f32) {
        self.dragging = true;
        let max = self.boxes.len().saturating_sub(1) as f32;
        self.scroll = (self.scroll - di).clamp(-0.35, max + 0.35);
        self.target = self.scroll;
        self.vel = 0.0;
        self.flip_to = 0.0;
    }
    pub fn release(&mut self, v: f32) {
        self.dragging = false;
        let max = self.boxes.len().saturating_sub(1) as f32;
        self.target = (self.scroll - v * 0.18).round().clamp(0.0, max);
    }

    pub fn update(&mut self, dt: f32) {
        let dt = dt.clamp(0.0, 0.05);
        self.time += dt;
        if !self.dragging {
            let k = 90.0f32;
            let c = 2.0 * k.sqrt();
            let a = k * (self.target - self.scroll) - c * self.vel;
            self.vel += a * dt;
            self.scroll += self.vel * dt;
        }
        let kf = 14.0;
        self.flip += (self.flip_to - self.flip) * (1.0 - (-kf * dt).exp());
    }

    pub fn render(&mut self, view: &wgpu::TextureView) {
        let aspect = self.w as f32 / self.h as f32;
        let eye = Vec3::new(0.0, 0.35, 5.5);
        let proj = Mat4::perspective_rh(40f32.to_radians(), aspect, 0.5, 40.0);
        let viewm = Mat4::look_at_rh(eye, Vec3::new(0.0, 0.1, 0.0), Vec3::Y);
        let floor = -0.72f32;
        let g = Globals { vp: (proj * viewm).to_cols_array_2d(), eye: [eye.x, eye.y, eye.z, 1.0], time: [self.time, floor, aspect, 0.0] };
        self.queue.write_buffer(&self.globals_buf, 0, bytemuck::bytes_of(&g));

        let mut verts: Vec<Vertex> = Vec::new();
        let mut draws: Vec<(usize, u8, std::ops::Range<u32>, bool)> = Vec::new(); // (box, part, range, mirrored)
        for mirrored in [true, false] {
            for (i, b) in self.boxes.iter().enumerate() {
                let o = i as f32 - self.scroll;
                if o.abs() > 5.0 {
                    continue;
                }
                let m = box_matrix(o, self.flip, b.ratio);
                let parts = box_parts(b.side, if mirrored { floor } else { f32::NAN });
                for (part, mesh) in parts.into_iter().enumerate() {
                    let start = verts.len() as u32;
                    for v in mesh {
                        let mut p = m.transform_point3(Vec3::from(v.pos));
                        let mut n = m.transform_vector3(Vec3::from(v.normal)).normalize_or_zero();
                        if mirrored {
                            p.y = 2.0 * floor - p.y;
                            n.y = -n.y;
                        }
                        verts.push(Vertex { pos: p.to_array(), normal: n.to_array(), uv: v.uv, tint: v.tint });
                    }
                    draws.push((i, part as u8, start..verts.len() as u32, mirrored));
                }
            }
        }
        let need = (verts.len() * std::mem::size_of::<Vertex>()) as u64;
        if need > self.vcap {
            self.vcap = need.next_power_of_two();
            self.vbuf = self.device.create_buffer(&wgpu::BufferDescriptor { label: None, size: self.vcap, usage: wgpu::BufferUsages::VERTEX | wgpu::BufferUsages::COPY_DST, mapped_at_creation: false });
        }
        if !verts.is_empty() {
            self.queue.write_buffer(&self.vbuf, 0, bytemuck::cast_slice(&verts));
        }

        let mut enc = self.device.create_command_encoder(&Default::default());
        {
            let mut pass = enc.begin_render_pass(&wgpu::RenderPassDescriptor {
                label: None,
                color_attachments: &[Some(wgpu::RenderPassColorAttachment { view: self.msaa.as_ref().unwrap(), resolve_target: Some(view), ops: wgpu::Operations { load: wgpu::LoadOp::Clear(wgpu::Color::BLACK), store: wgpu::StoreOp::Discard }})],
                depth_stencil_attachment: Some(wgpu::RenderPassDepthStencilAttachment { view: self.depth.as_ref().unwrap(), depth_ops: Some(wgpu::Operations { load: wgpu::LoadOp::Clear(1.0), store: wgpu::StoreOp::Discard }), stencil_ops: None }),
                timestamp_writes: None,
                occlusion_query_set: None,
            });
            if let Some(b) = self.boxes.first() {
                pass.set_pipeline(&self.p_bg);
                pass.set_bind_group(0, &self.globals_bg, &[]);
                pass.set_bind_group(1, &b.plain, &[]);
                pass.draw(0..3, 0..1);
            }
            pass.set_vertex_buffer(0, self.vbuf.slice(..));
            for pass_mirrored in [true, false] {
                pass.set_pipeline(if pass_mirrored { &self.p_ref } else { &self.p_box });
                pass.set_bind_group(0, &self.globals_bg, &[]);
                for (i, part, range, mirrored) in &draws {
                    if *mirrored != pass_mirrored {
                        continue;
                    }
                    let b = &self.boxes[*i];
                    pass.set_bind_group(1, match part { 0 => &b.front, 1 => &b.back, _ => &b.plain }, &[]);
                    pass.draw(range.clone(), 0..1);
                }
            }
        }
        self.queue.submit(Some(enc.finish()));
    }
}

fn flat(c: [f32; 3], k: f32) -> Vec<u8> {
    let px = [(c[0] * 255.0 * k) as u8, (c[1] * 255.0 * k) as u8, (c[2] * 255.0 * k) as u8, 255];
    (0..4).flat_map(|_| px).collect()
}

fn box_matrix(o: f32, flip: f32, ratio: f32) -> Mat4 {
    let ax = o.abs();
    let sign = if o < 0.0 { -1.0 } else { 1.0 };
    let x = sign * if ax <= 1.0 { ax * 0.8 } else { 0.8 + (ax - 1.0) * 0.4 };
    let z = -ax.min(1.0) * 0.8 - (ax - 1.0).max(0.0) * 0.3;
    let yaw = -sign * ax.min(1.0) * 0.8;
    let sc = 1.0 - 0.12 * ax.min(1.0);
    let centre = (1.0 - ax.min(1.0)).max(0.0);
    let flipa = flip * std::f32::consts::PI * centre;
    let w = (ratio * 1.4).clamp(0.9, 1.6);
    let _ = w;
    Mat4::from_translation(Vec3::new(x, 0.0, z)) * Mat4::from_rotation_y(yaw + flipa) * Mat4::from_scale(Vec3::splat(sc))
}

fn box_parts(side: [f32; 3], _mirror_floor: f32) -> [Vec<Vertex>; 3] {
    let (hw, hh, hd) = (0.5f32, 0.7f32, 0.045f32);
    let tex = |n: [f32; 3], a: [f32; 3], b: [f32; 3], c: [f32; 3], d: [f32; 3]| -> Vec<Vertex> {
        let t = [1.0, 1.0, 1.0, 1.0];
        let v = |p: [f32; 3], uv: [f32; 2]| Vertex { pos: p, normal: n, uv, tint: t };
        vec![v(a, [0.0, 1.0]), v(b, [1.0, 1.0]), v(c, [1.0, 0.0]), v(a, [0.0, 1.0]), v(c, [1.0, 0.0]), v(d, [0.0, 0.0])]
    };
    let front = tex([0.0, 0.0, 1.0], [-hw, -hh, hd], [hw, -hh, hd], [hw, hh, hd], [-hw, hh, hd]);
    let back = tex([0.0, 0.0, -1.0], [hw, -hh, -hd], [-hw, -hh, -hd], [-hw, hh, -hd], [hw, hh, -hd]);
    let col = |k: f32| [side[0] * k, side[1] * k, side[2] * k, 0.0];
    let flat_q = |n: [f32; 3], a: [f32; 3], b: [f32; 3], c: [f32; 3], d: [f32; 3], k: f32| -> Vec<Vertex> {
        let v = |p: [f32; 3]| Vertex { pos: p, normal: n, uv: [0.5, 0.5], tint: col(k) };
        vec![v(a), v(b), v(c), v(a), v(c), v(d)]
    };
    let mut sides = Vec::new();
    sides.extend(flat_q([-1.0, 0.0, 0.0], [-hw, -hh, -hd], [-hw, -hh, hd], [-hw, hh, hd], [-hw, hh, -hd], 0.95)); // the spine
    sides.extend(flat_q([1.0, 0.0, 0.0], [hw, -hh, hd], [hw, -hh, -hd], [hw, hh, -hd], [hw, hh, hd], 0.7));
    sides.extend(flat_q([0.0, 1.0, 0.0], [-hw, hh, hd], [hw, hh, hd], [hw, hh, -hd], [-hw, hh, -hd], 0.8));
    sides.extend(flat_q([0.0, -1.0, 0.0], [-hw, -hh, -hd], [hw, -hh, -hd], [hw, -hh, hd], [-hw, -hh, hd], 0.5));
    [front, back, sides]
}
