
use image::{ImageBuffer, Rgba};

use crate::config::{FrameInterpolationConfig, InterpolationMode};

pub type RgbaImage = ImageBuffer<Rgba<u8>, Vec<u8>>;

pub fn interpolate_pair(previous: &RgbaImage, next: &RgbaImage, config: FrameInterpolationConfig) -> Vec<RgbaImage> {
    if config.mode == InterpolationMode::Off || config.multiplier <= 1 {
        return Vec::new();
    }

    let width = previous.width().min(next.width());
    let height = previous.height().min(next.height());
    let mut frames = Vec::new();

    for step in 1..config.multiplier {
        match config.mode {
            InterpolationMode::Off => {}
            InterpolationMode::Duplicate => frames.push(previous.clone()),
            InterpolationMode::Blend => {
                let t = step as f32 / config.multiplier as f32;
                frames.push(blend_frames(previous, next, width, height, t));
            }
        }
    }

    frames
}

pub fn blend_frames(previous: &RgbaImage, next: &RgbaImage, width: u32, height: u32, t: f32) -> RgbaImage {
    let mut out = ImageBuffer::new(width, height);
    let t = t.max(0.0).min(1.0);
    let inv = 1.0 - t;

    for y in 0..height {
        for x in 0..width {
            let a = previous.get_pixel(x, y);
            let b = next.get_pixel(x, y);
            out.put_pixel(x, y, Rgba([
                (a[0] as f32 * inv + b[0] as f32 * t).round() as u8,
                (a[1] as f32 * inv + b[1] as f32 * t).round() as u8,
                (a[2] as f32 * inv + b[2] as f32 * t).round() as u8,
                (a[3] as f32 * inv + b[3] as f32 * t).round() as u8,
            ]));
        }
    }

    out
}
