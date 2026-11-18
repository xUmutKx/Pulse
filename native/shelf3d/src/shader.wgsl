struct Globals {
    vp: mat4x4<f32>,
    eye: vec4<f32>,
    time: vec4<f32>,   // x: seconds, y: floor y, z: aspect, w: unused
};
@group(0) @binding(0) var<uniform> g: Globals;
@group(1) @binding(0) var tex: texture_2d<f32>;
@group(1) @binding(1) var smp: sampler;

struct VIn {
    @location(0) pos: vec3<f32>,
    @location(1) normal: vec3<f32>,
    @location(2) uv: vec2<f32>,
    @location(3) tint: vec4<f32>,   // rgb: colour factor, a: how much of the texture (1) or the flat colour (0)
};
struct VOut {
    @builtin(position) clip: vec4<f32>,
    @location(0) world: vec3<f32>,
    @location(1) normal: vec3<f32>,
    @location(2) uv: vec2<f32>,
    @location(3) tint: vec4<f32>,
};

@vertex
fn vs(v: VIn) -> VOut {
    var o: VOut;
    o.clip = g.vp * vec4<f32>(v.pos, 1.0);
    o.world = v.pos;
    o.normal = v.normal;
    o.uv = v.uv;
    o.tint = v.tint;
    return o;
}

fn shade(v: VOut) -> vec3<f32> {
    let t = textureSample(tex, smp, v.uv).rgb;
    let base = mix(v.tint.rgb, t, v.tint.a);
    let n = normalize(v.normal);
    let l = normalize(vec3<f32>(-0.35, 0.55, 0.75));
    let diff = max(dot(n, l), 0.0);
    let vd = normalize(g.eye.xyz - v.world);
    let h = normalize(l + vd);
    let spec = pow(max(dot(n, h), 0.0), 48.0) * 0.18;
    return base * (0.55 + 0.55 * diff) + vec3<f32>(spec);
}

@fragment
fn fs(v: VOut) -> @location(0) vec4<f32> {
    return vec4<f32>(shade(v), 1.0);
}

// the box mirrored in the floor: faded out with the distance from it
@fragment
fn fs_reflect(v: VOut) -> @location(0) vec4<f32> {
    let d = max(g.time.y - v.world.y, 0.0);
    let fade = clamp(0.38 - d * 0.32, 0.0, 0.38);
    return vec4<f32>(shade(v) * 0.8, fade);
}

// ---- background -------------------------------------------------------------------------------------------------------------------------------

struct BOut {
    @builtin(position) clip: vec4<f32>,
    @location(0) uv: vec2<f32>,
};

@vertex
fn vs_bg(@builtin(vertex_index) i: u32) -> BOut {
    var p = array<vec2<f32>, 3>(vec2<f32>(-1.0, -1.0), vec2<f32>(3.0, -1.0), vec2<f32>(-1.0, 3.0));
    var o: BOut;
    o.clip = vec4<f32>(p[i], 0.999, 1.0);
    o.uv = vec2<f32>(p[i].x * 0.5 + 0.5, 0.5 - p[i].y * 0.5);
    return o;
}

fn hash21(p: vec2<f32>) -> f32 {
    var q = fract(p * vec2<f32>(123.34, 456.21));
    q = q + dot(q, q + 45.32);
    return fract(q.x * q.y);
}

@fragment
fn fs_bg(b: BOut) -> @location(0) vec4<f32> {
    let t = g.time.x;
    let top = vec3<f32>(0.05, 0.34, 0.42);
    let mid = vec3<f32>(0.03, 0.20, 0.29);
    let bot = vec3<f32>(0.012, 0.035, 0.06);
    let horizon = 0.64;
    var c = mix(top, mid, smoothstep(0.0, 0.55, b.uv.y));
    c = mix(c, bot, smoothstep(horizon - 0.1, 1.0, b.uv.y));
    // three layers of slow light waves, each its own speed, height and tint
    for (var i = 0; i < 3; i = i + 1) {
        let fi = f32(i);
        let y0 = 0.16 + fi * 0.17;
        let amp = 0.05 + fi * 0.015;
        let wave = y0 + amp * sin(b.uv.x * (4.0 + fi * 1.7) + t * (0.35 + fi * 0.18) + fi * 2.0) + 0.02 * sin(b.uv.x * 11.0 - t * 0.5 + fi);
        let d = abs(b.uv.y - wave);
        let core = smoothstep(0.010, 0.0, d);
        let glow = exp(-d * 38.0) * 0.35;
        let tint = mix(vec3<f32>(0.15, 0.55, 0.62), vec3<f32>(0.30, 0.45, 0.75), fi * 0.5);
        c = c + tint * (core * 0.55 + glow * 0.5);
    }
    // soft beams from the top
    let beam = smoothstep(0.0, 0.5, 1.0 - b.uv.y) * (0.5 + 0.5 * sin(b.uv.x * 9.0 + t * 0.2)) * 0.035;
    c = c + vec3<f32>(0.2, 0.5, 0.55) * beam;
    // floating specks of light
    let cell = floor(vec2<f32>(b.uv.x * 14.0, (b.uv.y + t * 0.015) * 24.0));
    let h = hash21(cell);
    let ctr = vec2<f32>(fract(h * 7.0), fract(h * 13.0));
    let f2 = fract(vec2<f32>(b.uv.x * 14.0, (b.uv.y + t * 0.015) * 24.0)) - ctr;
    let speck = smoothstep(0.09, 0.0, length(f2)) * step(0.82, h) * (0.4 + 0.6 * sin(t * 1.3 + h * 40.0));
    c = c + vec3<f32>(0.4, 0.8, 0.85) * speck * 0.35 * (1.0 - smoothstep(horizon - 0.05, horizon + 0.05, b.uv.y));
    // the floor: darker, with faint vertical tiles and a pool of light under the middle box
    if (b.uv.y > horizon) {
        let k = smoothstep(horizon, horizon + 0.04, b.uv.y);
        let stripe = 0.5 + 0.5 * sin(b.uv.x * 90.0);
        c = c * (1.0 - 0.25 * k) + vec3<f32>(0.03, 0.07, 0.08) * stripe * 0.35 * k * (b.uv.y - horizon);
    }
    let gl = exp(-pow((b.uv.x - 0.5) * 2.0, 2.0) - pow((b.uv.y - 0.70) * 4.5, 2.0));
    c = c + vec3<f32>(0.12, 0.26, 0.28) * gl * 0.8;
    // vignette
    let v = length((b.uv - vec2<f32>(0.5, 0.45)) * vec2<f32>(1.0, 0.8));
    c = c * (1.0 - smoothstep(0.45, 0.95, v) * 0.45);
    return vec4<f32>(c, 1.0);
}
